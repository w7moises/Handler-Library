package com.logicsoft.molina.core;

import com.logicsoft.molina.annotations.ResponseHandler;
import com.logicsoft.molina.api.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.core.ResolvableType;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.HandlerResult;
import org.springframework.web.reactive.HandlerResultHandler;
import org.springframework.web.reactive.accept.RequestedContentTypeResolver;
import org.springframework.web.reactive.result.method.annotation.ResponseBodyResultHandler;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.annotation.NonNull;

import java.lang.reflect.Method;
import java.time.Instant;

public class ApiResponseResultHandler implements HandlerResultHandler, Ordered {

    private static final Logger log = LoggerFactory.getLogger(ApiResponseResultHandler.class);

    private final ResponseBodyResultHandler delegate;

    public ApiResponseResultHandler(ServerCodecConfigurer codecs,
                                    RequestedContentTypeResolver resolver) {
        this.delegate = new ResponseBodyResultHandler(codecs.getWriters(), resolver);
        this.delegate.setOrder(Ordered.HIGHEST_PRECEDENCE);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public boolean supports(@NonNull HandlerResult result) {
        MethodParameter methodParameter = result.getReturnTypeSource();
        ResponseHandler responseHandler = methodParameter.getMethodAnnotation(ResponseHandler.class);
        if (responseHandler == null) {
            Class<?> containing = methodParameter.getContainingClass();
            responseHandler = containing.getAnnotation(ResponseHandler.class);
        }
        if (responseHandler == null) return false;
        ResolvableType rt = result.getReturnType();
        Class<?> raw = rt.toClass();
        if (isPublisher(raw)) {
            Class<?> inner = rt.getGeneric(0).toClass();
            if (isPublisher(inner)) {
                String msg = "⚠️ Invalid reactive return type detected: " + rt +
                        ". Avoid using Mono<Flux<T>> or Flux<Mono<T>>. ";
                log.error(msg);
            }
        }
        return true;
    }

    @Override
    @NonNull
    public Mono<Void> handleResult(@NonNull ServerWebExchange exchange, HandlerResult result) {
        MethodParameter methodParameter = result.getReturnTypeSource();
        ResponseHandler responseHandler = methodParameter.getMethodAnnotation(ResponseHandler.class);
        if (responseHandler == null) {
            responseHandler = methodParameter.getContainingClass().getAnnotation(ResponseHandler.class);
        }
        final int status = (responseHandler != null ? responseHandler.status() : 200);
        final boolean ok = (responseHandler == null || responseHandler.result());
        Object originalBody = result.getReturnValue();
        if (originalBody == null) {
            ApiResponse<Object> env = okEnvelope(status, ok, null);
            exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.valueOf(env.getStatus()));
            return write(exchange, result, env);
        }
        if (originalBody instanceof Mono<?> mono) {
            Mono<ApiResponse<Object>> mapped = mono
                    .map(data -> okEnvelope(status, ok, data))
                    .onErrorResume(ex -> Mono.just(errorEnvelope(ex)));

            return mapped.flatMap(env -> {
                exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.valueOf(env.getStatus()));
                return write(exchange, result, env);
            });
        }
        if (originalBody instanceof Flux<?> flux) {
            Mono<ApiResponse<Object>> envMono = flux
                    .collectList()
                    .map(list -> okEnvelope(status, ok, list))
                    .onErrorResume(ex -> Mono.just(errorEnvelope(ex)));

            return envMono.flatMap(env -> {
                exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.valueOf(env.getStatus()));
                return write(exchange, result, env);
            });
        }
        ApiResponse<Object> env = okEnvelope(status, ok, originalBody);
        exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.valueOf(env.getStatus()));
        return write(exchange, result, env);
    }

    private Mono<Void> write(ServerWebExchange exchange, HandlerResult original, Object newBody) {
        HandlerResult newResult = new HandlerResult(
                original.getHandler(),
                newBody,
                methodParameterForApiResponse()
        );
        return this.delegate.handleResult(exchange, newResult);
    }

    private static MethodParameter methodParameterForApiResponse() {
        try {
            Method m = Dummy.class.getDeclaredMethod("m");
            return new MethodParameter(m, -1);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    static final class Dummy {
        public static ApiResponse<Object> m() {
            return null;
        }
    }

    private static ApiResponse<Object> okEnvelope(int status, boolean result, Object data) {
        ApiResponse<Object> r = new ApiResponse<>();
        r.setTimestamp(Instant.now());
        r.setStatus(status);
        r.setResult(result);
        r.setData(data);
        return r;
    }

    private static ApiResponse<Object> errorEnvelope(Throwable ex) {
        ApiResponse<Object> r = new ApiResponse<>();
        r.setTimestamp(Instant.now());
        r.setStatus(500);
        r.setResult(false);
        r.setMessage(ex.getMessage());
        r.setData(null);
        return r;
    }

    private static boolean isPublisher(Class<?> c) {
        return c != null && (Mono.class.isAssignableFrom(c) || Flux.class.isAssignableFrom(c));
    }
}