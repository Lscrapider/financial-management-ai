package com.scrapider.finance.config;

import com.scrapider.finance.interceptor.AppVisitLogInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AppVisitLogInterceptor appVisitLogInterceptor;

    public WebMvcConfig(AppVisitLogInterceptor appVisitLogInterceptor) {
        this.appVisitLogInterceptor = appVisitLogInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this.appVisitLogInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/ai/console/**");
    }
}
