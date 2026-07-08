package com.example.noltok.notification.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Component
public class SseEmitterRepository {

    private static final long TIMEOUT = 60 * 60 * 1000L; // 1시간, 끊기면 클라이언트(EventSource)가 자동 재연결

    // 한 유저가 여러 탭/기기로 접속할 수 있어 리스트로 관리
    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter register(Long userId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT);
        emitters.computeIfAbsent(userId, id -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> remove(userId, emitter));
        emitter.onError(e -> remove(userId, emitter));

        // 연결 직후 더미 이벤트 전송 → 일부 프록시/브라우저의 응답 버퍼링으로 인한
        // "연결 안 된 것처럼 보임" 문제 방지
        sendToEmitter(emitter, "connected", "SSE 연결 성공");

        return emitter;
    }

    // 특정 유저의 모든 연결에 이벤트 전송 (다음 단계인 Kafka Consumer가 호출할 자리)
    public void send(Long userId, Object data) {
        List<SseEmitter> userEmitters = emitters.get(userId);
        if (userEmitters == null) {
            return;
        }
        for (SseEmitter emitter : userEmitters) {
            sendToEmitter(emitter, "notification", data);
        }
    }

    private void sendToEmitter(SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }

    private void remove(Long userId, SseEmitter emitter) {
        List<SseEmitter> userEmitters = emitters.get(userId);
        if (userEmitters == null) {
            return;
        }
        userEmitters.remove(emitter);
        if (userEmitters.isEmpty()) {
            emitters.remove(userId);
        }
    }

    // 유휴 연결이 프록시/로드밸런서에서 타임아웃으로 끊기지 않도록 주기적으로 ping 전송
    @Scheduled(fixedRate = 30_000)
    public void sendHeartbeat() {
        emitters.forEach((userId, userEmitters) ->
                userEmitters.forEach(emitter -> sendToEmitter(emitter, "ping", "keep-alive")));
    }
}
