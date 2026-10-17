package com.dev.julian09.financeserver.config;

import java.util.List;

import com.dev.julian09.financeserver.shared.web.CurrentUserArgumentResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Lets controllers declare a CurrentUser parameter; also turns on the purge jobs (@Scheduled). */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class WebConfig implements WebMvcConfigurer {

    private final CurrentUserArgumentResolver currentUserResolver;

    public WebConfig(CurrentUserArgumentResolver currentUserResolver) {
        this.currentUserResolver = currentUserResolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserResolver);
    }
}
