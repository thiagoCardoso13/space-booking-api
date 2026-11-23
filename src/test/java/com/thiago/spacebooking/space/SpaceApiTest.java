package com.thiago.spacebooking.space;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class SpaceApiTest {

    @Autowired
    MockMvc mvc;

    String admin;
    String user;
    String locationId;
    String city;

    @BeforeEach
    void setUp() throws Exception {
        admin = TestAuth.adminToken(mvc);
        user = TestAuth.userToken(mvc);
        city = "City-" + UUID.randomUUID();
        String response = mvc.perform(post("/api/v1/locations").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test Location\",\"address\":\"Street 1\",\"city\":\"%s\",\"opensAt\":\"08:00\",\"closesAt\":\"20:00\"}".formatted(city)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        locationId = JsonPath.read(response, "$.id");
    }

    @Test
    void adminCreatesASpaceThatIsActiveByDefault() throws Exception {
        mvc.perform(post("/api/v1/spaces").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content(body(locationId, "Room 1", "MEETING_ROOM")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.locationId").value(locationId))
                .andExpect(jsonPath("$.type").value("MEETING_ROOM"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void writesRequireAdmin() throws Exception {
        String payload = body(locationId, "Room 1", "DESK");

        mvc.perform(post("/api/v1/spaces").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/spaces").header("Authorization", user).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/spaces/" + UUID.randomUUID()).header("Authorization", user))
                .andExpect(status().isForbidden());
    }

    @Test
    void createValidatesLocationNameAndType() throws Exception {
        mvc.perform(post("/api/v1/spaces").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content(body(UUID.randomUUID().toString(), "Room", "DESK")))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/spaces").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Room\",\"type\":\"DESK\"}"))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(post("/api/v1/spaces").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content(body(locationId, "", "DESK")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/spaces").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content(body(locationId, "Boat", "BOAT")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void duplicateNameInTheSameLocationIsAConflictButAllowedElsewhere() throws Exception {
        create("Court 1", "SPORTS_COURT");
        mvc.perform(post("/api/v1/spaces").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content(body(locationId, "Court 1", "SPORTS_COURT")))
                .andExpect(status().isConflict());

        String otherLocation = JsonPath.read(mvc.perform(post("/api/v1/locations").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Other\",\"address\":\"Street 2\",\"city\":\"%s\",\"opensAt\":\"08:00\",\"closesAt\":\"20:00\"}".formatted(city)))
                .andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(post("/api/v1/spaces").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content(body(otherLocation, "Court 1", "SPORTS_COURT")))
                .andExpect(status().isCreated());
    }

    @Test
    void listFiltersByTypeLocationAndCity() throws Exception {
        create("Room 1", "MEETING_ROOM");
        create("Desk 1", "DESK");
        create("Desk 2", "DESK");

        mvc.perform(get("/api/v1/spaces").param("city", city.toLowerCase()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));
        mvc.perform(get("/api/v1/spaces").param("locationId", locationId).param("type", "DESK"))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].name").value("Desk 1"));
        mvc.perform(get("/api/v1/locations/" + locationId + "/spaces").param("type", "MEETING_ROOM"))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/locations/" + UUID.randomUUID() + "/spaces")).andExpect(status().isNotFound());
    }

    @Test
    void deleteDeactivatesTheSpaceAndHidesItFromDefaultListings() throws Exception {
        String id = create("Room 1", "MEETING_ROOM");

        mvc.perform(delete("/api/v1/spaces/" + id).header("Authorization", admin)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/spaces/" + id).header("Authorization", admin)).andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/spaces").param("locationId", locationId))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/spaces").param("locationId", locationId).param("active", "false"))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/spaces/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void adminUpdatesASpaceAndCanReactivateIt() throws Exception {
        String id = create("Room 1", "MEETING_ROOM");
        mvc.perform(delete("/api/v1/spaces/" + id).header("Authorization", admin));

        mvc.perform(put("/api/v1/spaces/" + id).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed\",\"type\":\"DESK\",\"active\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"))
                .andExpect(jsonPath("$.type").value("DESK"))
                .andExpect(jsonPath("$.active").value(true));
        mvc.perform(put("/api/v1/spaces/" + UUID.randomUUID()).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"X\",\"type\":\"DESK\"}"))
                .andExpect(status().isNotFound());
    }

    private String create(String name, String type) throws Exception {
        String response = mvc.perform(post("/api/v1/spaces").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(body(locationId, name, type)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private static String body(String locationId, String name, String type) {
        return "{\"locationId\":\"%s\",\"name\":\"%s\",\"type\":\"%s\"}".formatted(locationId, name, type);
    }
}
