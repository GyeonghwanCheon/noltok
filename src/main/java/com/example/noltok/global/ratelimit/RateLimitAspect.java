package com.example.noltok.global.ratelimit;

import com.example.noltok.global.exception.BusinessException;
import com.example.noltok.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private final RateLimiterService rateLimiterService;

    @Before("@annotation(rateLimited)")
    public void checkRateLimit(JoinPoint joinPoint, RateLimited rateLimited) {
        String identifier = resolveIdentifier(joinPoint, rateLimited.keyType());
        String key = "rate_limit:" + rateLimited.action() + ":" + identifier;

        boolean allowed = rateLimiterService.tryConsume(key, rateLimited.limit(), rateLimited.windowSeconds());
        if (!allowed) {
            throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED);
        }
    }

    private String resolveIdentifier(JoinPoint joinPoint, RateLimitKeyType keyType) {
        return switch (keyType) {
            case IP -> resolveIp();
            case USER_AND_ROOM -> resolveUserId(joinPoint) + ":" + resolveRoomId(joinPoint);
        };
    }

    private String resolveIp() {
        HttpServletRequest request =
                ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        return request.getRemoteAddr();
    }

    private String resolveUserId(JoinPoint joinPoint) {
        for (Object arg : joinPoint.getArgs()) {
            if (arg instanceof UserDetails userDetails) {
                return userDetails.getUsername();  // JwtAuthenticationFilter가 userId를 username 자리에 담아 인증 처리
            }
        }
        throw new IllegalStateException("@RateLimited(USER_AND_ROOM)는 @AuthenticationPrincipal UserDetails 파라미터가 필요합니다.");
    }

    private String resolveRoomId(JoinPoint joinPoint) {
        for (Object arg : joinPoint.getArgs()) {
            if (arg instanceof Long roomId) {
                return String.valueOf(roomId);
            }
        }
        throw new IllegalStateException("@RateLimited(USER_AND_ROOM)는 Long 타입 PathVariable(roomId) 파라미터가 필요합니다.");
    }
}
