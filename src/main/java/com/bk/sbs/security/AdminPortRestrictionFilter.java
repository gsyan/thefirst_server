//--------------------------------------------------------------------------------------------------
package com.bk.sbs.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// /admin/** 은 어드민 전용 포트(기본 8081)에서만, 그 외 경로는 그 포트에서 서빙되지 않도록 강제
// 리버스 프록시 설정이 잘못돼 8080 트래픽이 /admin으로 흘러도 애플리케이션 코드 레벨에서 확실히 차단하기 위함
// Spring Security의 두 SecurityFilterChain보다 먼저 실행돼야 하므로 최우선순위로 등록
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AdminPortRestrictionFilter extends OncePerRequestFilter {

    @Value("${admin.server.port:8081}")
    private int adminServerPort;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        boolean isAdminPath = request.getRequestURI().startsWith("/admin");
        boolean isAdminPort = request.getLocalPort() == adminServerPort;

        if (isAdminPath != isAdminPort) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        filterChain.doFilter(request, response);
    }
}
