package com.logicsoft.molina.exception;

import com.logicsoft.molina.api.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class ApiResponseConstraintViolationAdvice {

    @ExceptionHandler(ConstraintViolationException.class)
    public Object handleConstraintViolation(ConstraintViolationException ex, HttpServletResponse response) {
        Map<String, String> violations = ex.getConstraintViolations().stream()
                .collect(Collectors.toMap(
                        v -> leafName(v.getPropertyPath().toString()),
                        ConstraintViolation::getMessage,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        ApiResponse<Object> env = new ApiResponse<>();
        env.setTimestamp(Instant.now());
        env.setStatus(HttpStatus.BAD_REQUEST.value());
        env.setResult(false);
        env.setMessage("Validation error");
        env.setErrors(violations);
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        response.setContentType("application/json");
        return env;
    }

    private static String leafName(String path) {
        if (path == null || path.isBlank()) return path;
        int i = path.lastIndexOf('.');
        return (i >= 0 && i < path.length() - 1) ? path.substring(i + 1) : path;
    }
}
