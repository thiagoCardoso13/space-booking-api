package com.thiago.spacebooking.ops;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.thiago.spacebooking.TestAuth;
import com.thiago.spacebooking.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** API documentation and health endpoints: reachable without a token, and nothing else leaks. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class OperationalEndpointsTest {

    @Autowired
    MockMvc mvc;

    @Test
    void openApiDocumentIsPublicAndDescribesTheApi() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Space Booking API"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.paths['/api/v1/bookings']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/spaces/{id}/availability']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/login']").exists());
    }

    @Test
    void protectedOperationsDeclareTheBearerRequirementAndPublicOnesDoNot() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/api/v1/bookings'].post.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/locations'].post.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/locations'].get.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.security").doesNotExist());
    }

    @Test
    void swaggerUiIsReachableWithoutAToken() throws Exception {
        mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    @Test
    void healthIsPublicAndHidesDetails() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
    }

    @Test
    void otherActuatorEndpointsAreNotExposed() throws Exception {
        mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/env").header("Authorization", TestAuth.adminToken(mvc)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/actuator/beans").header("Authorization", TestAuth.adminToken(mvc)))
                .andExpect(status().isNotFound());
    }

    @Test
    void unknownRoutesReturnProblemDetailInsteadOfAnHtmlPage() throws Exception {
        mvc.perform(get("/api/v1/does-not-exist").header("Authorization", TestAuth.adminToken(mvc)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }
}
