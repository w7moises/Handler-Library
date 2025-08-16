package com.logicsoft.molina.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logicsoft.molina.core.ApiResponseMvcAdvice;
import com.logicsoft.molina.core.ApiResponseResultHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.accept.RequestedContentTypeResolver;
import org.springframework.web.reactive.accept.RequestedContentTypeResolverBuilder;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@AutoConfiguration
@ConditionalOnProperty(prefix = "molina.response-handler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ResponseHandlerAutoConfiguration {

    // ---------- MVC (Servlet) ----------
    @Configuration
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass({ResponseBodyAdvice.class, HttpMessageConverter.class})
    static class MvcConfig {
        @Bean
        @ConditionalOnMissingBean
        public ApiResponseMvcAdvice apiResponseMvcAdvice(ObjectProvider<ObjectMapper> mapperProvider) {
            return new ApiResponseMvcAdvice(mapperProvider);
        }
    }

    // ---------- WebFlux (Reactivo) ----------
    @Configuration
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    @ConditionalOnClass({
            ServerCodecConfigurer.class,
            RequestedContentTypeResolver.class
    })
    static class ReactiveConfig {

        @Bean
        @ConditionalOnMissingBean
        public ApiResponseResultHandler apiResponseResultHandler(
                ObjectProvider<ServerCodecConfigurer> codecsProvider,
                ObjectProvider<RequestedContentTypeResolver> resolverProvider) {

            ServerCodecConfigurer codecs =
                    codecsProvider.getIfAvailable(ServerCodecConfigurer::create);

            RequestedContentTypeResolver resolver =
                    resolverProvider.getIfAvailable(() ->
                            new RequestedContentTypeResolverBuilder().build());

            return new ApiResponseResultHandler(codecs, resolver);
        }
    }
}
