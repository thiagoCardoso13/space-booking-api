package com.thiago.spacebooking.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String BEARER = "bearerAuth";
    private static final String PROBLEM = "ProblemDetail";

    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Space Booking API")
                        .version("v1")
                        .description("""
                                REST API to book meeting rooms, coworking desks and sports courts.

                                **How to try it:** call `POST /api/v1/auth/login`, copy the `accessToken` and press \
                                **Authorize**. The README lists the demo credentials.

                                Errors follow RFC 7807 (`application/problem+json`). Business rule violations return \
                                `422` with a `rule` code (R1-R10); a slot that is already taken returns `409`.""")
                        .contact(new Contact().name("Thiago Bastos Cardoso").url("https://github.com/thiagoCardoso13"))
                        .license(new License().name("MIT").url("https://opensource.org/licenses/MIT")))
                .tags(List.of(new Tag().name("Authentication"), new Tag().name("Users"), new Tag().name("Locations"),
                        new Tag().name("Spaces"), new Tag().name("Bookings")))
                .components(new Components()
                        .addSecuritySchemes(BEARER, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT"))
                        .addSchemas(PROBLEM, problemDetailSchema()));
    }

    /**
     * Documents the errors every operation can return, so each endpoint does not need to repeat them:
     * 400 when it takes input, 401 when it needs a token, 403 for admin endpoints and 404 when it addresses a resource.
     */
    @Bean
    OperationCustomizer errorResponses() {
        return (operation, handlerMethod) -> {
            boolean secured = operation.getSecurity() != null && !operation.getSecurity().isEmpty();
            boolean takesInput = operation.getRequestBody() != null
                    || (operation.getParameters() != null && !operation.getParameters().isEmpty());
            boolean addressesResource = operation.getParameters() != null
                    && operation.getParameters().stream().anyMatch(p -> "path".equals(p.getIn()));
            if (takesInput) {
                addProblem(operation, "400", "Invalid request (validation errors are listed in `errors`)");
            }
            if (secured) {
                addProblem(operation, "401", "Missing, invalid or expired token");
            }
            if (handlerMethod.hasMethodAnnotation(AdminOnly.class)) {
                addProblem(operation, "403", "Admin role required");
            }
            if (addressesResource) {
                addProblem(operation, "404", "Resource not found");
            }
            return operation;
        };
    }

    private static void addProblem(Operation operation, String code, String description) {
        if (operation.getResponses() == null) {
            operation.setResponses(new ApiResponses());
        }
        if (operation.getResponses().containsKey(code)) {
            return;
        }
        Schema<?> ref = new Schema<>().$ref("#/components/schemas/" + PROBLEM);
        operation.getResponses().addApiResponse(code, new ApiResponse().description(description)
                .content(new Content().addMediaType("application/problem+json", new MediaType().schema(ref))));
    }

    private static Schema<?> problemDetailSchema() {
        return new ObjectSchema()
                .description("RFC 7807 problem detail")
                .addProperty("type", new StringSchema().example("about:blank"))
                .addProperty("title", new StringSchema().example("Unprocessable Content"))
                .addProperty("status", new IntegerSchema().example(422))
                .addProperty("detail", new StringSchema().example("Bookings must start on the full hour."))
                .addProperty("instance", new StringSchema().example("/api/v1/bookings"))
                .addProperty("rule", new StringSchema().description("Business rule code (R1-R10), only on 422").example("R3"))
                .addProperty("errors", new ObjectSchema()
                        .description("Field validation messages, only on 400")
                        .additionalProperties(new StringSchema())
                        .example(Map.of("password", "must be between 8 and 72 characters")));
    }
}
