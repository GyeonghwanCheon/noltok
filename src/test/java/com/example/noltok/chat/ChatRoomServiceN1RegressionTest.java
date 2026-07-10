package com.example.noltok.chat;

import com.example.noltok.chat.dto.response.ChatRoomDetailResponse;
import com.example.noltok.chat.dto.response.ChatRoomSearchResponse;
import com.example.noltok.friend.FriendRepository;
import com.example.noltok.block.BlockRepository;
import com.example.noltok.notification.kafka.NotificationProducer;
import com.example.noltok.user.User;
import com.example.noltok.user.UserRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

// getRoomDetail()/searchRooms()는 Kafka/Redis에 의존하지 않는 순수 DB
// 로직이라 MySQL Testcontainer 하나로 충분함 (@DataJpaTest 참고)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({ChatRoomService.class, ChatRoomServiceN1RegressionTest.TestConfig.class})
class ChatRoomServiceN1RegressionTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    // Hibernate Statistics는 @ServiceConnection이 다루는 접속정보가 아니라 별도로 설정
    @DynamicPropertySource
    static void hibernateStats(DynamicPropertyRegistry registry) {
        registry.add("spring.jpa.properties.hibernate.generate_statistics", () -> "true");
    }

    @TestConfiguration
    static class TestConfig {
        // Spring Security 자동설정 없이 JPA 슬라이스만 로딩되므로 PasswordEncoder를 직접 등록
        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder();
        }
    }

    @Autowired
    private ChatRoomService chatRoomService;
    @Autowired
    private ChatRoomRepository chatRoomRepository;
    @Autowired
    private ChatRoomMemberRepository chatRoomMemberRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    // getRoomDetail()/searchRooms()는 호출하지 않는 의존성이라 Mock으로 대체
    @MockitoBean
    private NotificationProducer notificationProducer;
    @MockitoBean
    private FriendRepository friendRepository;
    @MockitoBean
    private BlockRepository blockRepository;
    @MockitoBean
    private UnreadCountCacheService unreadCountCacheService;

    private Statistics statistics;

    @BeforeEach
    void setUp() {
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
    }

    @Test
    void getRoomDetail_쿼리수는_멤버수와_무관하게_일정하다() {
        // given: 방 하나에 관리자 1명 + 멤버 49명, 총 50명
        User admin = userRepository.save(User.create("admin@test.com", "pw", "관리자"));
        ChatRoom room = chatRoomRepository.save(ChatRoom.create("N+1 회귀 테스트방", ChatRoomType.OPEN, admin.getId(), null));
        chatRoomMemberRepository.save(ChatRoomMember.create(room, admin.getId(), ChatRoomRole.ADMIN));

        for (int i = 0; i < 49; i++) {
            User member = userRepository.save(User.create("member" + i + "@test.com", "pw", "멤버" + i));
            chatRoomMemberRepository.save(ChatRoomMember.create(room, member.getId(), ChatRoomRole.MEMBER));
        }

        // when
        statistics.clear();
        ChatRoomDetailResponse response = chatRoomService.getRoomDetail(admin.getId(), room.getId());

        // then: 멤버가 50명이어도 쿼리 수는 한 자릿수여야 함
        // (findAllById 배치 조회로 최적화 안 됐다면 멤버 수만큼 쿼리가 나가 50을 훌쩍 넘음)
        long queryCount = statistics.getPrepareStatementCount();
        assertThat(response.members()).hasSize(50);
        assertThat(queryCount).isLessThan(10);
    }

    @Test
    void searchRooms_쿼리수는_검색결과수와_무관하게_일정하다() {
        // given: 검색어를 공유하는 OPEN 채팅방 20개
        User owner = userRepository.save(User.create("owner@test.com", "pw", "방장"));
        for (int i = 0; i < 20; i++) {
            ChatRoom room = chatRoomRepository.save(
                    ChatRoom.create("N+1검색방" + i, ChatRoomType.OPEN, owner.getId(), null));
            chatRoomMemberRepository.save(ChatRoomMember.create(room, owner.getId(), ChatRoomRole.ADMIN));
        }

        // when
        statistics.clear();
        ChatRoomSearchResponse response = chatRoomService.searchRooms("N+1검색방");

        // then: 검색 결과가 20개여도 쿼리 수는 한 자릿수여야 함
        // (배치 카운트 쿼리로 최적화 안 됐다면 방 수만큼 COUNT 쿼리가 나가 20을 훌쩍 넘음)
        long queryCount = statistics.getPrepareStatementCount();
        assertThat(response.rooms()).hasSize(20);
        assertThat(queryCount).isLessThan(10);
    }
}
