package com.example.payment.config;

import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JsonConfiguration {
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer strictJsonStrings() {
        // ALLOW_COERCION_OF_SCALARS alone does not disable number-to-String coercion.
        return builder -> builder.postConfigurer(mapper -> {
            var textual = mapper.coercionConfigFor(LogicalType.Textual);
            textual.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail);
            textual.setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
            textual.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
        });
    }
}
