// MVC 설정 — 인터셉터 등록
package com.bk.sbs.config;

import com.bk.sbs.interceptor.OnlineActivityInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final OnlineActivityInterceptor onlineActivityInterceptor;

    public WebMvcConfig(OnlineActivityInterceptor onlineActivityInterceptor) {
        this.onlineActivityInterceptor = onlineActivityInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(onlineActivityInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/account/**"); // 로그인/가입 제외
    }
}
