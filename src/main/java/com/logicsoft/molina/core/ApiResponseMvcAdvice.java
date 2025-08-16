package com.logicsoft.molina.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.logicsoft.molina.annotations.ResponseHandler;
import com.logicsoft.molina.api.ApiResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.time.Instant;
import java.util.Optional;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class ApiResponseMvcAdvice implements ResponseBodyAdvice<Object> {

    private final ObjectMapper mapper;

    // ObjectMapper opcional: usa el del contexto si existe; si no, crea uno.
    public ApiResponseMvcAdvice(ObjectProvider<ObjectMapper> mapperProvider) {
        ObjectMapper m = mapperProvider.getIfAvailable(ObjectMapper::new);
        // (opcional) coherencia en timestamps si tu ApiResponse usa Instant
        m.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.mapper = m;
    }

    @Override
    public boolean supports(MethodParameter returnType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        // Aplica solo si hay @ResponseHandler
        ResponseHandler ann = Optional.ofNullable(returnType.getMethodAnnotation(ResponseHandler.class))
                .orElse(returnType.getDeclaringClass().getAnnotation(ResponseHandler.class));
        return ann != null; // ✅ ahora NO excluimos StringHttpMessageConverter
    }

    @Override
    public Object beforeBodyWrite(Object body,
                                  MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request,
                                  ServerHttpResponse response) {

        // Si ya es ApiResponse, solo fijamos status y content-type
        if (body instanceof ApiResponse<?> alreadyWrapped) {
            int statusFromBody = alreadyWrapped.getStatus() > 0 ? alreadyWrapped.getStatus() : 200;
            response.setStatusCode(HttpStatusCode.valueOf(statusFromBody));
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            // Si el converter es String, devolvemos JSON como String
            if (StringHttpMessageConverter.class.isAssignableFrom(selectedConverterType)) {
                try {
                    return mapper.writeValueAsString(alreadyWrapped);
                } catch (Exception e) {
                    throw new RuntimeException("Error serializando ApiResponse", e);
                }
            }
            return body;
        }

        // Leer @ResponseHandler (método > clase)
        ResponseHandler ann = Optional.ofNullable(returnType.getMethodAnnotation(ResponseHandler.class))
                .orElse(returnType.getDeclaringClass().getAnnotation(ResponseHandler.class));

        int status = ann != null ? ann.status() : 200;
        boolean ok = ann == null || ann.result();

        ApiResponse<Object> env = new ApiResponse<>();
        env.setTimestamp(Instant.now());
        env.setStatus(status);
        env.setResult(ok);
        env.setData(body);

        // Fijar HTTP status y content-type JSON
        response.setStatusCode(HttpStatusCode.valueOf(status));
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        // 🔑 Caso especial: StringHttpMessageConverter seleccionado
        if (StringHttpMessageConverter.class.isAssignableFrom(selectedConverterType)) {
            try {
                // devolvemos String JSON para que el converter String lo escriba tal cual
                return mapper.writeValueAsString(env);
            } catch (Exception e) {
                throw new RuntimeException("Error serializando ApiResponse", e);
            }
        }

        // Caso normal (Jackson converter): devolver el objeto y que Jackson lo serialice
        return env;
    }
}
