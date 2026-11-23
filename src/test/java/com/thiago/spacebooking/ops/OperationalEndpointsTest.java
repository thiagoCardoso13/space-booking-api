package com.thiago.spacebooking.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.thiago.spacebooking.TestAuth;
import com.thiago.spacebooking.TestcontainersConfiguration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
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
    void successResponsesAreDocumentedWithTheRealStatusCodes() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/register'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/bookings'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/locations'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/spaces'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/bookings/{id}'].delete.responses['204']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/bookings/{id}'].delete.responses['200']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/spaces/{id}'].delete.responses['204']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/locations/{id}'].delete.responses['204']").exists());
    }

    @Test
    void paginationIsDocumentedAsPageSizeAndSortNotAsAnObject() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/api/v1/spaces'].get.parameters[?(@.name=='page')]").isNotEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/spaces'].get.parameters[?(@.name=='size')]").isNotEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/spaces'].get.parameters[?(@.name=='sort')]").isNotEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/spaces'].get.parameters[?(@.name=='pageable')]").isEmpty());
    }

    @Test
    void errorsAreDocumentedAsProblemDetail() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.components.schemas.ProblemDetail.properties.rule").exists())
                .andExpect(jsonPath("$.paths['/api/v1/bookings'].post.responses['401'].content['application/problem+json']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/bookings'].post.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/bookings/{id}'].get.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/locations'].get.responses['401']").doesNotExist());
    }

    /**
     * The OpenAPI document must not lie about who can call what. Every operation is called with a regular user's
     * token: it answers 403 exactly when the document lists a 403 for it.
     */
    @Test
    void documentedAdminOnlyOperationsMatchWhatTheSecurityConfigurationEnforces() throws Exception {
        String user = TestAuth.userToken(mvc);
        String docs = mvc.perform(get("/v3/api-docs")).andReturn().getResponse().getContentAsString();
        Map<String, Map<String, Object>> paths = JsonPath.read(docs, "$.paths");

        int checked = 0;
        for (var path : paths.entrySet()) {
            for (var operation : path.getValue().entrySet()) {
                String method = operation.getKey().toUpperCase();
                @SuppressWarnings("unchecked")
                Map<String, Object> responses = (Map<String, Object>) ((Map<String, Object>) operation.getValue()).get("responses");
                boolean documentedAs403 = responses.containsKey("403");

                String url = path.getKey().replace("{id}", UUID.randomUUID().toString());
                int status = mvc.perform(request(HttpMethod.valueOf(method), url)
                                .header("Authorization", user)
                                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                        .andReturn().getResponse().getStatus();

                assertThat(status == 403).as("%s %s documented 403=%s, actual status=%d", method, path.getKey(), documentedAs403, status)
                        .isEqualTo(documentedAs403);
                checked++;
            }
        }
        assertThat(checked).isGreaterThan(15);
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
