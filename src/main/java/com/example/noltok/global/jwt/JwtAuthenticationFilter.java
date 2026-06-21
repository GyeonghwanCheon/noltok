package com.example.noltok.global.jwt;

import com.example.noltok.user.User;
import com.example.noltok.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // 1. Header에서 토큰 추출
        String token = extractToken(request);

        // 2. 토큰이 있고 유효한 경우에만 인증 처리
        if (StringUtils.hasText(token) && jwtProvider.validateToken(token)) {

            // 3. 토큰에서 userId 추출
            Long userId = jwtProvider.getUserId(token);

            // 4. DB에서 User 조회
            userRepository.findById(userId).ifPresent(user -> {

                // 5. UserDetails 생성
                UserDetails userDetails = org.springframework.security.core.userdetails
                        .User.builder()
                        .username(String.valueOf(user.getId()))
                        .password("")   // JWT 방식에서는 비밀번호 불필요
                        .roles("USER")
                        .build();

                // 6. Authentication 객체 생성
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities()
                        );

                authentication.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request));

                // 7. SecurityContext에 저장
                SecurityContextHolder.getContext().setAuthentication(authentication);
            });
        }

        // 8. 다음 필터로 진행
        filterChain.doFilter(request, response);
    }

    // "Bearer {token}" 에서 토큰만 추출
    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");

        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);  // "Bearer " 이후 문자열
        }
        return null;
    }
}
