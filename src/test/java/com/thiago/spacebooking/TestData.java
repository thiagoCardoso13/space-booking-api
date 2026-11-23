package com.thiago.spacebooking;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Builds the data an integration test needs through the real API. */
public final class TestData {

    public static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    private TestData() {
    }

    /** A new location open 08:00-20:00 with one active space; returns the space id. */
    public static String newSpace(MockMvc mvc, String adminToken) throws Exception {
        String location = mvc.perform(post("/api/v1/locations").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Loc %s\",\"address\":\"Street 1\",\"city\":\"Elche\",\"opensAt\":\"08:00\",\"closesAt\":\"20:00\"}"
                                .formatted(UUID.randomUUID())))
                .andReturn().getResponse().getContentAsString();
        String space = mvc.perform(post("/api/v1/spaces").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"locationId\":\"%s\",\"name\":\"Space\",\"type\":\"SPORTS_COURT\"}"
                                .formatted(JsonPath.<String>read(location, "$.id"))))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(space, "$.id");
    }

    public static String userId(MockMvc mvc, String token) throws Exception {
        String body = mvc.perform(get("/api/v1/users/me").header("Authorization", token))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    /** The given full hour (Madrid time) on the day that is {@code daysAhead} days from now, as an instant. */
    public static Instant at(int daysAhead, int hour) {
        return ZonedDateTime.now(MADRID).plusDays(daysAhead).truncatedTo(ChronoUnit.DAYS).withHour(hour).toInstant();
    }

    public static String bookingBody(String spaceId, Instant startsAt) {
        return "{\"spaceId\":\"%s\",\"startsAt\":\"%s\"}".formatted(spaceId, startsAt);
    }
}
