package com.example.noltok.chat.message.websocket;

import com.example.noltok.chat.ChatRoomMemberRepository;
import com.example.noltok.global.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class StompAuthInterceptor implements ChannelInterceptor {

    private final JwtProvider jwtProvider;
    private final ChatRoomMemberRepository chatRoomMemberRepository;

    // 구독 destination 형식: /topic/rooms/{roomId}
    private static final Pattern ROOM_TOPIC_PATTERN = Pattern.compile("^/topic/rooms/(\\d+)$");

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) {
            return message;
        }

        // CONNECT: JWT 검증 후 Principal 설정
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            authenticate(accessor);
        }
        // SUBSCRIBE: 요청한 방의 활성 멤버인지 확인
        else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            authorizeSubscription(accessor);
        }

        return message;
    }

    // Authorization 헤더의 JWT를 검증하고 Principal로 세션에 저장
    private void authenticate(StompHeaderAccessor accessor) {
        String bearerToken = accessor.getFirstNativeHeader("Authorization");

        if (!StringUtils.hasText(bearerToken) || !bearerToken.startsWith("Bearer ")) {
            throw new MessagingException("Authorization 헤더가 없습니다.");
        }

        String token = bearerToken.substring(7);
        if (!jwtProvider.validateToken(token)) {
            throw new MessagingException("유효하지 않은 토큰입니다.");
        }

        Long userId = jwtProvider.getUserId(token);
        accessor.setUser(new StompPrincipal(userId));
    }

    // destination에서 roomId를 파싱해서 활성 멤버인지 확인
    private void authorizeSubscription(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();

        // /user/** 는 세션 소유자에게만 전달되는 개인 큐라 방 멤버십 검증 대상이 아님
        if (destination != null && destination.startsWith("/user/")) {
            return;
        }

        Matcher matcher = destination != null ? ROOM_TOPIC_PATTERN.matcher(destination) : null;

        if (matcher == null || !matcher.matches()) {
            throw new MessagingException("구독할 수 없는 destination입니다.");
        }

        if (accessor.getUser() == null) {
            throw new MessagingException("인증되지 않은 연결입니다.");
        }

        Long roomId = Long.parseLong(matcher.group(1));
        Long userId = Long.parseLong(accessor.getUser().getName());

        chatRoomMemberRepository.findByChatRoomIdAndUserIdAndIsActiveTrue(roomId, userId)
                .orElseThrow(() -> new MessagingException("채팅방 멤버가 아닙니다."));
    }
}
