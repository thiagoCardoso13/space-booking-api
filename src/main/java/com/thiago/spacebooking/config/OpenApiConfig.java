package com.thiago.spacebooking.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String BEARER = "bearerAuth";

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
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")));
    }
}
