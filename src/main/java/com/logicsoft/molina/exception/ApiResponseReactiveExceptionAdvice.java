package com.logicsoft.molina.exception;

import com.logicsoft.molina.api.ApiResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class ApiResponseReactiveExceptionAdvice {

    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ResponseEntity<ApiResponse<Object>>> handleBind(WebExchangeBindException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getFieldErrors().forEach(fe -> errors.put(fe.getField(), fe.getDefaultMessage()));
        ex.getGlobalErrors().forEach(ge -> errors.putIfAbsent("_global", ge.getDefaultMessage()));
        return Mono.just(ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(error400("WebExchangeBindException error", errors)));
    }

    @ExceptionHandler(ServerWebInputException.class)
    public Mono<ResponseEntity<ApiResponse<Object>>> handleInput(ServerWebInputException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        String param = ex.getMethodParameter() != null ? ex.getMethodParameter().getParameterName() : "_request";
        String msg = ex.getReason() != null ? ex.getReason() : "Invalid request parameter";
        errors.put(param, msg);
        return Mono.just(ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(error400("ServerWebInputException error", errors)));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Mono<ResponseEntity<ApiResponse<Object>>> handleCve(ConstraintViolationException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (ConstraintViolation<?> v : ex.getConstraintViolations()) {
            String field = lastSegment(String.valueOf(v.getPropertyPath()));
            errors.put(field != null && !field.isBlank() ? field : "_violation",
                    v.getMessage() != null ? v.getMessage() : "invalid");
        }
        return Mono.just(ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(error400("Validation error", errors)));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public Mono<ResponseEntity<ApiResponse<Object>>> handleRse(ResponseStatusException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (ex.getReason() != null) errors.put("_reason", ex.getReason());
        return Mono.just(ResponseEntity
                .status(ex.getStatusCode())
                .body(error(ex.getStatusCode().value(), "Request failed", errors)));
    }

    @ExceptionHandler(Throwable.class)
    public Mono<ResponseEntity<ApiResponse<Object>>> handleAny(Throwable ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (ex.getMessage() != null) errors.put("_error", ex.getMessage());
        return Mono.just(ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(error(500, "Unexpected error", errors)));
    }

    /* ---------- helpers ---------- */

    private static String lastSegment(String path) {
        if (path == null) return null;
        int dot = path.lastIndexOf('.');
        String s = (dot >= 0 ? path.substring(dot + 1) : path);
        return s.replaceAll("\\[.*?]", "");
    }

    private static ApiResponse<Object> error400(String message, Map<String, String> errors) {
        return error(400, message, errors);
    }

    private static ApiResponse<Object> error(int status, String message, Map<String, String> errors) {
        ApiResponse<Object> r = new ApiResponse<>();
        r.setTimestamp(Instant.now());
        r.setStatus(status);
        r.setResult(false);
        r.setMessage(message);
        r.setErrors(errors);
        r.setData(null);
        return r;
    }
}
