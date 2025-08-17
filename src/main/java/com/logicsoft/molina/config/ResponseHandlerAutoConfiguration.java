package com.logicsoft.molina.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logicsoft.molina.core.ApiResponseMvcAdvice;
import com.logicsoft.molina.core.ApiResponseResultHandler;
import com.logicsoft.molina.exception.ApiResponseConstraintViolationAdvice;
import com.logicsoft.molina.exception.ApiResponseGenericExceptionAdvice;
import com.logicsoft.molina.exception.ApiResponseReactiveExceptionAdvice;
import jakarta.validation.Validator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.reactive.HandlerResultHandler;
import org.springframework.web.reactive.accept.RequestedContentTypeResolver;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.reactive.result.method.annotation.ResponseBodyResultHandler;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@AutoConfiguration
@ConditionalOnProperty(prefix = "molina.response-handler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ResponseHandlerAutoConfiguration {

    /* ===================== MVC (SERVLET) ===================== */

    @Configuration
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass({ResponseBodyAdvice.class, HttpMessageConverter.class, WebMvcConfigurer.class})
    static class MvcConfig {

        @Bean
        @ConditionalOnMissingBean
        public ApiResponseMvcAdvice apiResponseMvcAdvice(
                ObjectProvider<ObjectMapper> mapperProvider,
                org.springframework.context.ApplicationContext ctx,
                @Value("${molina.response-handler.validation.warn-when-inactive:true}")
                boolean warnWhenInactive) {
            return new ApiResponseMvcAdvice(mapperProvider, ctx, warnWhenInactive);
        }

        @Bean
        @ConditionalOnMissingBean
        public ApiResponseGenericExceptionAdvice apiResponseGenericExceptionAdvice() {
            return new ApiResponseGenericExceptionAdvice();
        }
    }

    @Configuration
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass({WebMvcConfigurer.class, Validator.class, org.springframework.validation.beanvalidation.MethodValidationPostProcessor.class})
    @ConditionalOnBean(Validator.class)
    static class MvcValidationConfig {

        @Bean
        @ConditionalOnMissingBean
        public ApiResponseConstraintViolationAdvice apiResponseConstraintViolationAdvice() {
            return new ApiResponseConstraintViolationAdvice();
        }
    }

    /* ===================== WebFlux (REACTIVE) ===================== */

    @Configuration
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    @ConditionalOnClass({
            WebFluxConfigurer.class,
            ResponseBodyResultHandler.class,
            ServerCodecConfigurer.class,
            RequestedContentTypeResolver.class,
            HandlerResultHandler.class
    })
    static class WebFluxConfig {

        @Bean
        @ConditionalOnMissingBean(ApiResponseResultHandler.class)
        public ApiResponseResultHandler apiResponseResultHandler(ServerCodecConfigurer codecs,
                                                                 RequestedContentTypeResolver resolver) {
            return new ApiResponseResultHandler(codecs, resolver);
        }

        @Bean
        @ConditionalOnMissingBean(ApiResponseReactiveExceptionAdvice.class)
        public ApiResponseReactiveExceptionAdvice apiResponseReactiveExceptionAdvice() {
            return new ApiResponseReactiveExceptionAdvice();
        }
    }
}
