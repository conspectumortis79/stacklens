package com.stacklens.infrastructure.config

import org.springframework.context.annotation.Configuration
import org.springframework.http.CacheControl
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.util.concurrent.TimeUnit

@Configuration
class I18nCacheConfig : WebMvcConfigurer {
    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        registry
            .addResourceHandler("/i18n2/**")
            .addResourceLocations("classpath:/static/i18n/")
            .setCachePeriod(0)
            .resourceChain(true)

        registry
            .addResourceHandler("/i18n/**")
            .addResourceLocations("classpath:/static/i18n/")
            .setCachePeriod(0)
            .resourceChain(true)

        registry
            .addResourceHandler("/favicon.ico", "/favicon.svg")
            .addResourceLocations("classpath:/static/")
            .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic())
            .setUseLastModified(false)
    }
}
