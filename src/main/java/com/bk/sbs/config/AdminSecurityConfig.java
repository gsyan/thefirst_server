//--------------------------------------------------------------------------------------------------
package com.bk.sbs.config;

import com.bk.sbs.admin.security.AdminUserDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// /admin/** 전용 인증 체인 — 기존 SecurityConfig(JWT, STATELESS)와 완전히 분리된 세션 기반 form login
// AdminPortRestrictionFilter가 /admin 경로 자체를 어드민 포트(8081)에서만 통과시키므로, 여기서는 인증/인가만 담당
@Configuration
@Order(1)
public class AdminSecurityConfig {

    private final AdminUserDetailsService adminUserDetailsService;

    public AdminSecurityConfig(AdminUserDetailsService adminUserDetailsService) {
        this.adminUserDetailsService = adminUserDetailsService;
    }

    @Bean
    public SecurityFilterChain adminSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .userDetailsService(adminUserDetailsService)
                .securityMatcher("/admin/**")
                // 순수 정적 HTML이라 마스킹 없는 원본 토큰 값을 쿠키/폼에서 그대로 주고받는 단순한 핸들러 사용
                // (기본 XorCsrfTokenRequestAttributeHandler는 요청마다 토큰을 마스킹해 템플릿 엔진 없는 수동 폼 제출과 맞지 않음)
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/admin/login.html", "/admin/css/**", "/admin/js/**").permitAll()
                        .anyRequest().hasRole("ADMIN"))
                .formLogin(form -> form
                        .loginPage("/admin/login.html")
                        .loginProcessingUrl("/admin/doLogin")
                        .defaultSuccessUrl("/admin/index.html", true)
                        .failureUrl("/admin/login.html?error=1")
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/admin/logout")
                        .logoutSuccessUrl("/admin/login.html"))
                // 정적 HTML(템플릿 엔진 없음)이 JS로 CSRF 쿠키를 읽어 쓰므로, CsrfFilter의 지연 토큰 해석을 매 요청마다 강제로 트리거해 쿠키가 항상 내려가게 함
                .addFilterAfter(new OncePerRequestFilter() {
                    @Override
                    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                            throws ServletException, IOException {
                        CsrfToken csrfToken = (CsrfToken) request.getAttribute("_csrf");
                        if (csrfToken != null) csrfToken.getToken();
                        filterChain.doFilter(request, response);
                    }
                }, CsrfFilter.class);

        return http.build();
    }
}
