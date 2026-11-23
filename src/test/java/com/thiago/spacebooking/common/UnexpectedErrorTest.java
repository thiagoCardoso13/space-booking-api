package com.thiago.spacebooking.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.thiago.spacebooking.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Import({TestcontainersConfiguration.class, UnexpectedErrorTest.BoomController.class})
@SpringBootTest
@AutoConfigureMockMvc
class UnexpectedErrorTest {

    @Autowired
    MockMvc mvc;

    @Test
    @WithMockUser
    void unexpectedExceptionBecomesGenericProblemDetailWithoutLeakingInternals() throws Exception {
        mvc.perform(get("/api/v1/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred."))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret-internal-detail"))));
    }

    @TestConfiguration
    @RestController
    static class BoomController {

        @GetMapping("/api/v1/test/boom")
        String boom() {
            throw new IllegalStateException("secret-internal-detail");
        }
    }
}
