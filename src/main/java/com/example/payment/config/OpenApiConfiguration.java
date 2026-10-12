package com.example.payment.config;

import io.swagger.v3.oas.models.PathItem.HttpMethod;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

@Configuration
public class OpenApiConfiguration {
    private static final Set<HttpMethod> SAFE_METHODS = Set.of(
            HttpMethod.GET, HttpMethod.HEAD, HttpMethod.OPTIONS, HttpMethod.TRACE);

    @Bean
    public OpenApiCustomizer csrfHeaderDocumentation() {
        return api -> api.getPaths().forEach((path, item) -> {
            if (!path.startsWith("/api/")) return;
            item.readOperationsMap().forEach((method, operation) -> {
                if (SAFE_METHODS.contains(method)) return;
                operation.addParametersItem(new HeaderParameter()
                        .name("X-CSRF-TOKEN")
                        .required(true)
                        .schema(new StringSchema())
                        .description("Copy token from GET /api/auth/csrf in this browser session. "
                                + "Keep the same host and session cookie. Fetch a new token after login or logout."));
                operation.getResponses().putIfAbsent("403", new ApiResponse()
                        .description("CSRF_INVALID: missing or invalid CSRF token for this session; "
                                + "or ACCESS_DENIED: access denied."));
            });
        });
    }
}
