package br.com.supermercados.prices.common;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    @Bean
    OpenAPI pricesOpenApi() {
        return new OpenAPI()
                .info(new Info().title("Gomo API").version("v1")
                        .description("Preços rastreáveis em BRL. Ausência de preço não informa estoque."))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer")
                                .bearerFormat("opaque")));
    }

    @Bean
    OpenApiCustomizer protectedOperations() {
        return api -> api.getPaths().forEach((path, item) -> {
            if (path.startsWith("/api/v1/shopping-lists") || path.startsWith("/api/v1/users/")
                    || path.startsWith("/api/v1/comparisons/shopping-lists/")
                    || path.startsWith("/api/v1/admin")
                    || path.startsWith("/api/v1/alerts")
                    || path.startsWith("/api/v1/notifications")
                    || path.equals("/api/v1/auth/logout")
                    || path.equals("/api/v1/auth/email-verifications")) {
                item.readOperations().forEach(operation -> operation.setSecurity(
                        List.of(new SecurityRequirement().addList("bearerAuth"))));
            }
        });
    }
}
