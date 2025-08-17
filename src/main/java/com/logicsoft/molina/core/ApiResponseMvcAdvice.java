package com.logicsoft.molina.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.logicsoft.molina.annotations.ResponseHandler;
import com.logicsoft.molina.api.ApiResponse;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import reactor.util.annotation.NonNull;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class ApiResponseMvcAdvice implements ResponseBodyAdvice<Object> {

    private static final Logger log = LoggerFactory.getLogger(ApiResponseMvcAdvice.class);

    private final ObjectMapper mapper;

    // Diagnóstico no intrusivo de validación
    private final boolean validationActive;
    private final boolean warnWhenInactive;
    private final AtomicBoolean warnedOnce = new AtomicBoolean(false);

    public ApiResponseMvcAdvice(ObjectProvider<ObjectMapper> mapperProvider,
                                ApplicationContext ctx,
                                @Value("${molina.response-handler.validation.warn-when-inactive:true}")
                                boolean warnWhenInactive) {
        this.mapper = Optional.of(mapperProvider.getIfAvailable(ObjectMapper::new))
                .map(m -> m.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS))
                .orElseGet(() -> {
                    ObjectMapper objectMapper = new ObjectMapper();
                    objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
                    return objectMapper;
                });

        boolean hasValidator = ctx.getBeanProvider(Validator.class).getIfAvailable() != null;
        boolean hasMethodValidation = ctx.getBeanProvider(MethodValidationPostProcessor.class).getIfAvailable() != null;
        this.validationActive = hasValidator && hasMethodValidation;
        this.warnWhenInactive = warnWhenInactive;
    }

    @Override
    public boolean supports(@NonNull MethodParameter returnType,
                            @NonNull Class<? extends HttpMessageConverter<?>> converterType) {
        return findResponseHandler(returnType) != null;
    }

    @Override
    public Object beforeBodyWrite(Object body,
                                  @NonNull MethodParameter returnType,
                                  @NonNull MediaType selectedContentType,
                                  @NonNull Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  @NonNull ServerHttpRequest request,
                                  @NonNull ServerHttpResponse response) {

        // Advertir solo una vez si la validación NO está activa
        if (!validationActive && warnWhenInactive && warnedOnce.compareAndSet(false, true)) {
            log.warn("[Molina] Bean Validation NO está activa en MVC. Las constraints en @RequestParam/@RequestBody "
                    + "no se aplicarán. Agregue 'spring-boot-starter-validation' o registre Validator + "
                    + "MethodValidationPostProcessor si desea validación.");
        }

        if (body instanceof ProblemDetail || body instanceof ResponseEntity<?>) {
            return body;
        }
        if (body instanceof ApiResponse<?> alreadyWrapped) {
            int status = normalizeStatus(alreadyWrapped.getStatus());
            setStatusIfNotExplicit(response, status);
            setJsonContentTypeIfAbsent(response);
            return maybeToJson(selectedConverterType, alreadyWrapped);
        }

        ResponseHandler ann = findResponseHandler(returnType);
        int status = (ann != null) ? ann.status() : HttpStatus.OK.value();
        boolean ok = (ann == null) || ann.result();
        ApiResponse<Object> envelope = new ApiResponse<>();
        envelope.setTimestamp(Instant.now());
        envelope.setStatus(status);
        envelope.setResult(ok);
        envelope.setData(body);

        setStatusIfNotExplicit(response, status);
        setJsonContentTypeIfAbsent(response);
        return maybeToJson(selectedConverterType, envelope);
    }

    /* ==================== helpers ==================== */

    private ResponseHandler findResponseHandler(MethodParameter mp) {
        ResponseHandler ann = mp.getMethodAnnotation(ResponseHandler.class);
        return (ann != null) ? ann : mp.getDeclaringClass().getAnnotation(ResponseHandler.class);
    }

    private int normalizeStatus(int status) {
        return status > 0 ? status : HttpStatus.OK.value();
    }

    private void setStatusIfNotExplicit(ServerHttpResponse response, int status) {
        if (response instanceof ServletServerHttpResponse servletResp) {
            int current = servletResp.getServletResponse().getStatus(); // 200 por defecto
            if (current == HttpStatus.OK.value()) {
                response.setStatusCode(HttpStatusCode.valueOf(status));
            }
        } else {
            response.setStatusCode(HttpStatusCode.valueOf(status));
        }
    }

    private void setJsonContentTypeIfAbsent(ServerHttpResponse response) {
        HttpHeaders headers = response.getHeaders();
        if (!headers.containsKey(HttpHeaders.CONTENT_TYPE)) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
    }

    private Object maybeToJson(Class<? extends HttpMessageConverter<?>> converterType, Object value) {
        if (StringHttpMessageConverter.class.isAssignableFrom(converterType)) {
            try {
                return mapper.writeValueAsString(value);
            } catch (Exception e) {
                throw new RuntimeException("Error in ApiResponse serialization", e);
            }
        }
        return value;
    }
}
