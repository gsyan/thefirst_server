// 인증된 게임 API 성공 시 lastOnlineAt 갱신 — 온라인 활동 추적
package com.bk.sbs.interceptor;

import com.bk.sbs.security.JwtUtil;
import com.bk.sbs.service.OnlineActivityService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class OnlineActivityInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final OnlineActivityService onlineActivityService;

    public OnlineActivityInterceptor(JwtUtil jwtUtil, OnlineActivityService onlineActivityService) {
        this.jwtUtil = jwtUtil;
        this.onlineActivityService = onlineActivityService;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 2xx 성공 응답이고 characterId가 있는 경우만 갱신
        if (response.getStatus() < 200 || response.getStatus() >= 300) return;

        String token = jwtUtil.getTokenFromRequest(request);
        if (token == null || !jwtUtil.hasCharacterId(token)) return;

        Long characterId = jwtUtil.getCharacterIdFromToken(token);
        if (characterId == null) return;

        long actualCharacterId = characterId & 0x00FFFFFFFFFFFFFFL;
        onlineActivityService.touch(actualCharacterId);
    }
}
