package org.logicsoft.molina.config;

import org.logicsoft.molina.core.ApiResponseResultHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.accept.RequestedContentTypeResolver;

@AutoConfiguration
public class ReactiveResponseAutoConfiguration {

    @Bean
    public ApiResponseResultHandler apiResponseResultHandler(ServerCodecConfigurer codecs,
                                                             RequestedContentTypeResolver resolver) {
        return new ApiResponseResultHandler(codecs, resolver);
    }
}
