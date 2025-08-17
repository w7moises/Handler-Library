package com.logicsoft.molina.exception;

import com.logicsoft.molina.api.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class ApiResponseGenericExceptionAdvice {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Object handleValidation(MethodArgumentNotValidException ex, HttpServletResponse response) {
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> Optional.ofNullable(fe.getDefaultMessage()).orElse("Invalid"),
                        (a, b) -> a,
                        HashMap::new
                ));

        ApiResponse<Object> env = base(HttpStatus.BAD_REQUEST.value());
        env.setMessage("Validation error");
        env.setErrors(fieldErrors);
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        response.setContentType("application/json");
        return env;
    }

    @ExceptionHandler(Exception.class)
    public Object handleAny(Exception ex,
                            HttpServletResponse response) {
        int status = resolveStatus(ex);
        ApiResponse<Object> env = base(status);
        env.setMessage(resolveMessage(ex));
        response.setStatus(status);
        response.setContentType("application/json");
        return env;
    }

    private ApiResponse<Object> base(int status) {
        ApiResponse<Object> env = new ApiResponse<>();
        env.setTimestamp(Instant.now());
        env.setResult(false);
        env.setStatus(status);
        return env;
    }

    private int resolveStatus(Exception ex) {
        if (ex instanceof ErrorResponseException ere) return ere.getStatusCode().value();
        if (ex instanceof IllegalArgumentException) return HttpStatus.BAD_REQUEST.value();
        return HttpStatus.INTERNAL_SERVER_ERROR.value();
    }

    private String resolveMessage(Exception ex) {
        String msg = ex.getMessage();
        return (msg == null || msg.isBlank()) ? ex.getClass().getSimpleName() : msg;
    }
}
