package com.bk.sbs.security;

import com.bk.sbs.dto.nogenerated.ApiResponse;
import com.bk.sbs.exception.ServerErrorCode;
import com.bk.sbs.service.ServerStatusService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// 클라 API 요청마다 X-App-Version-Code 헤더를 최소 허용 버전과 비교 — 없거나 낮으면 처리 전에 거절(옛 클라가 바뀐 서버에 요청을 보내지 못하게)
// 거절 응답은 HTTP 200 + errorCode라 옛 클라도 하트비트 실패로 집계해 메인 화면 → 서버 상태 확인(업데이트 안내)으로 넘어감
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AppVersionCheckFilter extends OncePerRequestFilter {

    public static final String HEADER_APP_VERSION_CODE = "X-App-Version-Code";

    private static final String API_PATH_PREFIX = "/api/";
    private static final String STATUS_PATH_PREFIX = "/api/status"; // 업데이트 안내를 띄우는 통로라 검사 제외
    private static final long MIN_VERSION_CACHE_MILLIS = 10_000L;

    private final ServerStatusService serverStatusService;
    private final ObjectMapper objectMapper;

    private volatile int m_cachedMinVersionCode;
    private volatile long m_cachedAtMillis;

    public AppVersionCheckFilter(ServerStatusService serverStatusService, ObjectMapper objectMapper) {
        this.serverStatusService = serverStatusService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        boolean isApiPath = path.startsWith(API_PATH_PREFIX);
        boolean isStatusPath = path.startsWith(STATUS_PATH_PREFIX);
        return isApiPath == false || isStatusPath == true;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        int clientVersionCode = parseVersionCode(request.getHeader(HEADER_APP_VERSION_CODE));
        int minVersionCode = getMinVersionCode();
        if (clientVersionCode >= minVersionCode) {
            filterChain.doFilter(request, response);
            return;
        }

        log.debug("[AppVersionCheck] 거절 uri={} client={} min={}", request.getRequestURI(), clientVersionCode, minVersionCode);
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.error(ServerErrorCode.APP_VERSION_FAIL_UPDATE_REQUIRED)));
    }

    // 헤더가 없거나 숫자가 아니면 0 — 최소 버전(기본 1 이상)보다 항상 낮아 거절됨
    private int parseVersionCode(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) return 0;
        try {
            return Integer.parseInt(headerValue.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // 요청마다 DB를 읽지 않도록 짧게 캐시 — 운영 중 최소 버전을 올려도 최대 10초 뒤에 반영
    private int getMinVersionCode() {
        long now = System.currentTimeMillis();
        boolean isExpired = now - m_cachedAtMillis > MIN_VERSION_CACHE_MILLIS;
        if (isExpired) {
            m_cachedMinVersionCode = serverStatusService.getMinVersionCode();
            m_cachedAtMillis = now;
        }
        return m_cachedMinVersionCode;
    }
}
