package com.logicsoft.molina.core;

import com.logicsoft.molina.annotations.ResponseHandler;
import com.logicsoft.molina.api.ApiResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.HandlerResult;
import org.springframework.web.reactive.HandlerResultHandler;
import org.springframework.web.reactive.accept.RequestedContentTypeResolver;
import org.springframework.web.reactive.result.method.annotation.ResponseBodyResultHandler;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.annotation.NonNull;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

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
        MethodParameter mp = result.getReturnTypeSource();
        ResponseHandler ann = mp.getMethodAnnotation(ResponseHandler.class);
        if (ann == null) ann = mp.getDeclaringClass().getAnnotation(ResponseHandler.class);
        if (ann == null) return false;

        ResolvableType rt = result.getReturnType();
        Class<?> raw = rt.toClass();
        if (isPublisher(raw)) {
            Class<?> inner = rt.getGeneric(0).toClass();
            if (isPublisher(inner)) {
                log.error("⚠️ Invalid reactive return type: {}. Avoid Mono<Flux<T>> or Flux<Mono<T>>.", rt);
            }
        }
        return true;
    }

    @Override
    @NonNull
    public Mono<Void> handleResult(@NonNull ServerWebExchange exchange, HandlerResult result) {
        MethodParameter mp = result.getReturnTypeSource();
        ResponseHandler ann = mp.getMethodAnnotation(ResponseHandler.class);
        if (ann == null) ann = mp.getDeclaringClass().getAnnotation(ResponseHandler.class);
        final int okStatus = (ann != null ? ann.status() : 200);
        final boolean ok = (ann == null || ann.result());
        Object originalBody = result.getReturnValue();
        if (originalBody == null) {
            ApiResponse<Object> env = okEnvelope(okStatus, ok, null);
            exchange.getResponse().setStatusCode(HttpStatus.valueOf(env.getStatus()));
            return write(exchange, result, env);
        }
        if (originalBody instanceof Mono<?> mono) {
            return mono
                    .map(data -> okEnvelope(okStatus, ok, data))
                    .onErrorResume(ex -> Mono.just(errorEnvelope(ex)))
                    .flatMap(env -> {
                        exchange.getResponse().setStatusCode(HttpStatus.valueOf(env.getStatus()));
                        return write(exchange, result, env);
                    });
        }
        if (originalBody instanceof Flux<?> flux) {
            return flux
                    .collectList()
                    .map(list -> okEnvelope(okStatus, ok, list))
                    .onErrorResume(ex -> Mono.just(errorEnvelope(ex)))
                    .flatMap(env -> {
                        exchange.getResponse().setStatusCode(HttpStatus.valueOf(env.getStatus()));
                        return write(exchange, result, env);
                    });
        }
        ApiResponse<Object> env = okEnvelope(okStatus, ok, originalBody);
        exchange.getResponse().setStatusCode(HttpStatus.valueOf(env.getStatus()));
        return write(exchange, result, env);
    }

    private Mono<Void> write(ServerWebExchange exchange, HandlerResult original, Object newBody) {
        HandlerResult newResult = new HandlerResult(original.getHandler(), newBody, methodParameterForApiResponse());
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

    /* -------- envelopes -------- */

    private static ApiResponse<Object> okEnvelope(int status, boolean result, Object data) {
        ApiResponse<Object> r = new ApiResponse<>();
        r.setTimestamp(Instant.now());
        r.setStatus(status);
        r.setResult(result);
        r.setData(data);
        return r;
    }

    private static ApiResponse<Object> errorEnvelope(Throwable ex) {
        int status = statusFromException(ex);
        String message = (status == 400 ? "Validation error" :
                (ex instanceof ResponseStatusException ? "Request failed" : "Unexpected error"));
        Map<String, String> errors = errorsFromException(ex);
        ApiResponse<Object> r = new ApiResponse<>();
        r.setTimestamp(Instant.now());
        r.setStatus(status);
        r.setResult(false);
        r.setMessage(message);
        if (errors != null && !errors.isEmpty()) r.setErrors(errors);
        r.setData(null);
        return r;
    }

    /* -------- mapping -------- */
    private static int statusFromException(Throwable ex) {
        if (ex instanceof ResponseStatusException rse) return rse.getStatusCode().value();
        if (ex instanceof ConstraintViolationException) return HttpStatus.BAD_REQUEST.value();
        return HttpStatus.INTERNAL_SERVER_ERROR.value();
    }

    private static Map<String, String> errorsFromException(Throwable ex) {
        if (ex instanceof ResponseStatusException rse) {
            Map<String, String> map = new HashMap<>();
            if (rse.getReason() != null) map.put("_reason", rse.getReason());
            return map;
        }
        if (ex instanceof ConstraintViolationException cve) {
            Map<String, String> map = new HashMap<>();
            for (ConstraintViolation<?> v : cve.getConstraintViolations()) {
                String field = lastSegment(String.valueOf(v.getPropertyPath()));
                map.put(field != null && !field.isBlank() ? field : "_violation",
                        v.getMessage() != null ? v.getMessage() : "invalid");
            }
            return map;
        }
        return null;
    }

    private static String lastSegment(String path) {
        if (path == null) return null;
        int dot = path.lastIndexOf('.');
        String s = (dot >= 0 ? path.substring(dot + 1) : path);
        return s.replaceAll("\\[.*?]", "");
    }

    private static boolean isPublisher(Class<?> c) {
        return c != null && (Mono.class.isAssignableFrom(c) || Flux.class.isAssignableFrom(c));
    }
}