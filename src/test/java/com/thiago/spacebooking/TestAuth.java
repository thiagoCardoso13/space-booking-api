package com.thiago.spacebooking;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Helpers for integration tests that need real tokens. */
public final class TestAuth {

    private TestAuth() {
    }

    public static String adminToken(MockMvc mvc) throws Exception {
        return login(mvc, "admin@example.com", "ChangeMe123!");
    }

    /** Registers a brand new regular user and returns a bearer token for it. */
    public static String userToken(MockMvc mvc) throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Test User\",\"email\":\"%s\",\"password\":\"S3cure-pass!\"}".formatted(email)));
        return login(mvc, email, "S3cure-pass!");
    }

    private static String login(MockMvc mvc, String email, String password) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.<String>read(body, "$.accessToken");
    }
}
