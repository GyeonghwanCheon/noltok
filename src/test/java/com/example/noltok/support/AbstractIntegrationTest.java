package com.example.noltok.support;

import io.minio.MinioClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

// Kafka/Redis가 실제로 필요한 통합 테스트의 공통 베이스
// → MySQL/Kafka/Redis 컨테이너를 static으로 선언하고 static 블록에서
//   직접 start()하는 "진짜" 싱글턴 컨테이너 패턴 사용
// → @Container + @Testcontainers 조합(JUnit5 확장이 자동으로 lifecycle
//   관리)은 static 필드라도 "이 테스트 클래스가 끝나면 stop()"을 각
//   서브클래스마다 개별로 실행함 — 여러 테스트 클래스가 상속받아 같이
//   돌아가면 앞 클래스가 끝나면서 컨테이너를 내려버려 뒤 클래스가
//   연결 실패(HikariPool 새 연결 타임아웃)를 겪는 문제가 있었음
//   (docs/troubleshooting-log.md 2026-07-13 참고). @DynamicPropertySource로
//   직접 연결 정보를 등록하면 JUnit5 lifecycle 관리를 아예 안 타서
//   컨테이너가 전체 테스트 실행 동안(JVM 종료 시 Ryuk이 정리) 살아있음
// → MinIO는 아직 컨테이너화하지 않음 — 이번 테스트 대상(메시지/알림/인증)과
//   무관해서 MinioClient만 Mock으로 대체 (MinioBucketInitializer의
//   @PostConstruct가 Mock 상대로는 예외 없이 조용히 지나감)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public abstract class AbstractIntegrationTest {

    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    // apache/kafka 이미지 사용 (docker-compose.yml의 실제 로컬 개발 환경과 동일한 이미지, KRaft 모드)
    static final KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:3.7.0"));

    static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    static {
        mysql.start();
        kafka.start();
        redis.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @MockitoBean
    protected MinioClient minioClient;
}
