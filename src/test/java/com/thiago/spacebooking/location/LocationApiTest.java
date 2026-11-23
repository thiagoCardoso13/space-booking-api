package com.thiago.spacebooking.location;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.thiago.spacebooking.TestAuth;
import com.thiago.spacebooking.TestcontainersConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class LocationApiTest {

    @Autowired
    MockMvc mvc;

    String admin;
    String user;

    @BeforeEach
    void tokens() throws Exception {
        admin = TestAuth.adminToken(mvc);
        user = TestAuth.userToken(mvc);
    }

    @Test
    void listIsPublicPaginatedAndIncludesSeedData() throws Exception {
        mvc.perform(get("/api/v1/locations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").isNumber());
    }

    @Test
    void listFiltersByCityIgnoringCase() throws Exception {
        String city = "City-" + UUID.randomUUID();
        create(admin, "Filtered", city, "08:00", "20:00");

        mvc.perform(get("/api/v1/locations").param("city", city.toUpperCase()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].city").value(city));
    }

    @Test
    void pageSizeIsCappedAndUnknownSortPropertyIsABadRequest() throws Exception {
        mvc.perform(get("/api/v1/locations").param("size", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
        mvc.perform(get("/api/v1/locations").param("sort", "doesNotExist"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getByIdReturns404ForUnknownAndBadRequestForMalformedId() throws Exception {
        mvc.perform(get("/api/v1/locations/" + UUID.randomUUID())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/locations/not-a-uuid")).andExpect(status().isBadRequest());
    }

    @Test
    void adminCreatesAndReadsALocation() throws Exception {
        String id = create(admin, "New Place", "Elche", "09:00", "21:00");

        mvc.perform(get("/api/v1/locations/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Place"))
                .andExpect(jsonPath("$.opensAt").value("09:00:00"));
    }

    @Test
    void createReturnsLocationHeader() throws Exception {
        mvc.perform(post("/api/v1/locations").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content(body("Header Place", "Elche", "09:00", "21:00")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/v1/locations/")));
    }

    @Test
    void writesRequireAdmin() throws Exception {
        String id = create(admin, "Protected", "Elche", "09:00", "21:00");
        String payload = body("X", "Elche", "09:00", "21:00");

        mvc.perform(post("/api/v1/locations").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/locations").header("Authorization", user).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/locations/" + id).header("Authorization", user).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/locations/" + id).header("Authorization", user))
                .andExpect(status().isForbidden());
    }

    @Test
    void createValidatesFieldsAndOpeningHours() throws Exception {
        mvc.perform(post("/api/v1/locations").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"address\":\"a\",\"city\":\"c\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.opensAt").exists());

        mvc.perform(post("/api/v1/locations").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content(body("Backwards", "Elche", "20:00", "08:00")))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void adminUpdatesALocation() throws Exception {
        String id = create(admin, "Old Name", "Elche", "09:00", "21:00");

        mvc.perform(put("/api/v1/locations/" + id).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content(body("Renamed", "Alicante", "10:00", "18:00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"))
                .andExpect(jsonPath("$.city").value("Alicante"));
    }

    @Test
    void deleteIsBlockedWhileTheLocationHasSpacesAndWorksOtherwise() throws Exception {
        String withSpaces = create(admin, "With Spaces", "Elche", "09:00", "21:00");
        mvc.perform(post("/api/v1/spaces").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"locationId\":\"%s\",\"name\":\"Room\",\"type\":\"MEETING_ROOM\"}".formatted(withSpaces)))
                .andExpect(status().isCreated());
        mvc.perform(delete("/api/v1/locations/" + withSpaces).header("Authorization", admin))
                .andExpect(status().isConflict());

        String empty = create(admin, "Empty", "Elche", "09:00", "21:00");
        mvc.perform(delete("/api/v1/locations/" + empty).header("Authorization", admin)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/locations/" + empty)).andExpect(status().isNotFound());
    }

    private String create(String token, String name, String city, String opens, String closes) throws Exception {
        String response = mvc.perform(post("/api/v1/locations").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(body(name, city, opens, closes)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private static String body(String name, String city, String opens, String closes) {
        return "{\"name\":\"%s\",\"address\":\"Some street 1\",\"city\":\"%s\",\"opensAt\":\"%s\",\"closesAt\":\"%s\"}"
                .formatted(name, city, opens, closes);
    }
}
