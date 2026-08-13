package com.bk.sbs.config;

import com.bk.sbs.security.CommanderIdArgumentResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final CommanderIdArgumentResolver commanderIdArgumentResolver;

    public WebMvcConfig(CommanderIdArgumentResolver commanderIdArgumentResolver) {
        this.commanderIdArgumentResolver = commanderIdArgumentResolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(commanderIdArgumentResolver);
    }
}
