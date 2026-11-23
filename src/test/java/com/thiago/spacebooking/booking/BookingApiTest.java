package com.thiago.spacebooking.booking;

import static com.thiago.spacebooking.TestData.at;
import static com.thiago.spacebooking.TestData.bookingBody;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.thiago.spacebooking.TestAuth;
import com.thiago.spacebooking.TestData;
import com.thiago.spacebooking.TestcontainersConfiguration;
import com.thiago.spacebooking.space.SpaceRepository;
import com.thiago.spacebooking.user.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
class BookingApiTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    BookingRepository bookings;
    @Autowired
    SpaceRepository spaces;
    @Autowired
    UserRepository users;

    String admin;
    String alice;
    String bob;
    String spaceId;

    @BeforeEach
    void setUp() throws Exception {
        admin = TestAuth.adminToken(mvc);
        alice = TestAuth.userToken(mvc);
        bob = TestAuth.userToken(mvc);
        spaceId = TestData.newSpace(mvc, admin);
    }

    // ---- creating ----

    @Test
    void userBooksASlotAndTheEndIsDerivedFromTheFixedDuration() throws Exception {
        Instant start = at(1, 10);

        mvc.perform(post("/api/v1/bookings").header("Authorization", alice).contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(spaceId, start)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/v1/bookings/")))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.spaceId").value(spaceId))
                .andExpect(jsonPath("$.endsAt").value(start.plus(1, ChronoUnit.HOURS).toString()))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    void startsAtAcceptsOffsetNotationAndIsStoredAsTheSameInstant() throws Exception {
        Instant start = at(1, 10); // 10:00 Madrid
        String madridOffset = java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(start.atZone(TestData.MADRID)); // e.g. 2030-01-16T10:00:00+01:00

        mvc.perform(post("/api/v1/bookings").header("Authorization", alice).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"spaceId\":\"%s\",\"startsAt\":\"%s\"}".formatted(spaceId, madridOffset)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.startsAt").value(start.toString()));
    }

    @Test
    void creatingRequiresAuthentication() throws Exception {
        mvc.perform(post("/api/v1/bookings").contentType(MediaType.APPLICATION_JSON).content(bookingBody(spaceId, at(1, 10))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidRequestsAreRejectedBeforeReachingTheRules() throws Exception {
        mvc.perform(post("/api/v1/bookings").header("Authorization", alice).contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.spaceId").exists())
                .andExpect(jsonPath("$.errors.startsAt").exists());
        mvc.perform(post("/api/v1/bookings").header("Authorization", alice).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"spaceId\":\"%s\",\"startsAt\":\"tomorrow at ten\"}".formatted(spaceId)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/bookings").header("Authorization", alice).contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(UUID.randomUUID().toString(), at(1, 10))))
                .andExpect(status().isNotFound());
    }

    @Test
    void businessRuleViolationsReturn422WithTheRuleCode() throws Exception {
        assertRule(at(1, 10).plus(30, ChronoUnit.MINUTES), "R3");
        assertRule(at(-1, 10), "R5");
        assertRule(at(15, 10), "R6");
        assertRule(at(1, 7), "R1");
        assertRule(at(1, 20), "R1");
    }

    @Test
    void inactiveSpacesCannotBeBooked() throws Exception {
        mvc.perform(delete("/api/v1/spaces/" + spaceId).header("Authorization", admin)).andExpect(status().isNoContent());

        assertRule(at(1, 10), "R10");
    }

    @Test
    void theSameSlotCannotBeBookedTwiceButBackToBackSlotsCan() throws Exception {
        book(alice, at(1, 10)).andExpect(status().isCreated());

        book(bob, at(1, 10)).andExpect(status().isConflict());
        book(bob, at(1, 11)).andExpect(status().isCreated());
        book(bob, at(1, 9)).andExpect(status().isCreated());
    }

    @Test
    void aUserCannotHoldMoreThanThreeActiveFutureBookings() throws Exception {
        book(alice, at(1, 9)).andExpect(status().isCreated());
        book(alice, at(1, 10)).andExpect(status().isCreated());
        String third = JsonPath.read(book(alice, at(1, 11)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");

        book(alice, at(1, 12)).andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.rule").value("R7"));

        mvc.perform(delete("/api/v1/bookings/" + third).header("Authorization", alice)).andExpect(status().isNoContent());
        book(alice, at(1, 12)).andExpect(status().isCreated());
    }

    // ---- reading ----

    @Test
    void myBookingsListsOnlyMineAndFiltersByStatus() throws Exception {
        String first = JsonPath.read(book(alice, at(1, 9)).andReturn().getResponse().getContentAsString(), "$.id");
        book(alice, at(1, 10));
        book(bob, at(1, 11));
        mvc.perform(delete("/api/v1/bookings/" + first).header("Authorization", alice));

        mvc.perform(get("/api/v1/bookings/me").header("Authorization", alice))
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/v1/bookings/me").header("Authorization", alice).param("status", "CONFIRMED"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].startsAt").value(at(1, 10).toString()));
        mvc.perform(get("/api/v1/bookings/me").header("Authorization", alice)
                        .param("from", at(1, 10).toString()).param("to", at(1, 11).toString()))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/bookings/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void onlyTheOwnerOrAnAdminCanSeeABooking() throws Exception {
        String id = JsonPath.read(book(alice, at(1, 10)).andReturn().getResponse().getContentAsString(), "$.id");

        mvc.perform(get("/api/v1/bookings/" + id).header("Authorization", alice)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/bookings/" + id).header("Authorization", admin)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/bookings/" + id).header("Authorization", bob)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/bookings/" + UUID.randomUUID()).header("Authorization", alice)).andExpect(status().isNotFound());
    }

    @Test
    void adminListsBookingsBySpaceAndDateAndUsersCannot() throws Exception {
        book(alice, at(1, 10));
        book(bob, at(1, 11));
        String date = at(1, 10).atZone(TestData.MADRID).toLocalDate().toString();

        mvc.perform(get("/api/v1/admin/bookings").header("Authorization", admin).param("spaceId", spaceId).param("date", date))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/v1/admin/bookings").header("Authorization", admin).param("spaceId", spaceId)
                        .param("date", at(2, 10).atZone(TestData.MADRID).toLocalDate().toString()))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/admin/bookings").header("Authorization", alice)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/bookings")).andExpect(status().isUnauthorized());
    }

    // ---- cancelling ----

    @Test
    void cancellingFreesTheSlotAndIsIdempotent() throws Exception {
        String id = JsonPath.read(book(alice, at(1, 10)).andReturn().getResponse().getContentAsString(), "$.id");

        mvc.perform(delete("/api/v1/bookings/" + id).header("Authorization", alice)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/bookings/" + id).header("Authorization", alice)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/bookings/" + id).header("Authorization", alice))
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        book(bob, at(1, 10)).andExpect(status().isCreated());
    }

    @Test
    void onlyTheOwnerOrAnAdminCanCancel() throws Exception {
        String id = JsonPath.read(book(alice, at(1, 10)).andReturn().getResponse().getContentAsString(), "$.id");

        mvc.perform(delete("/api/v1/bookings/" + id).header("Authorization", bob)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/bookings/" + id)).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/bookings/" + id).header("Authorization", admin)).andExpect(status().isNoContent());
    }

    @Test
    void ownersCannotCancelWithLessThanTwoHoursNoticeButAdminsCan() throws Exception {
        // Created straight in the database: the API (rightly) would also accept it, but this avoids depending on the time of day.
        var space = spaces.findById(UUID.fromString(spaceId)).orElseThrow();
        var owner = users.findById(UUID.fromString(TestData.userId(mvc, alice))).orElseThrow();
        Instant soon = Instant.now().plus(1, ChronoUnit.HOURS);
        String id = bookings.saveAndFlush(new Booking(space, owner, soon, soon.plus(1, ChronoUnit.HOURS))).getId().toString();

        mvc.perform(delete("/api/v1/bookings/" + id).header("Authorization", alice))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("R9"));
        mvc.perform(delete("/api/v1/bookings/" + id).header("Authorization", admin)).andExpect(status().isNoContent());
    }

    // ---- availability ----

    @Test
    void availabilityListsEverySlotOfTheDayAndMarksBookedOnes() throws Exception {
        book(alice, at(1, 10));
        String date = at(1, 10).atZone(TestData.MADRID).toLocalDate().toString();

        mvc.perform(get("/api/v1/spaces/" + spaceId + "/availability").param("date", date))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(12))            // 08:00-20:00, one-hour slots
                .andExpect(jsonPath("$[0].startsAt").value(at(1, 8).toString()))
                .andExpect(jsonPath("$[2].startsAt").value(at(1, 10).toString()))
                .andExpect(jsonPath("$[2].available").value(false))
                .andExpect(jsonPath("$[1].available").value(true))
                .andExpect(jsonPath("$[11].endsAt").value(at(1, 20).toString()));
    }

    @Test
    void availabilityIsPublicAndReflectsCancellationsAndTheRules() throws Exception {
        String id = JsonPath.read(book(alice, at(1, 10)).andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(delete("/api/v1/bookings/" + id).header("Authorization", alice));
        String tomorrow = at(1, 10).atZone(TestData.MADRID).toLocalDate().toString();

        mvc.perform(get("/api/v1/spaces/" + spaceId + "/availability").param("date", tomorrow))
                .andExpect(jsonPath("$[2].available").value(true));
        // Yesterday: everything is in the past, so nothing is available.
        mvc.perform(get("/api/v1/spaces/" + spaceId + "/availability")
                        .param("date", at(-1, 10).atZone(TestData.MADRID).toLocalDate().toString()))
                .andExpect(jsonPath("$[0].available").value(false));
        // Beyond the 14-day window as well.
        mvc.perform(get("/api/v1/spaces/" + spaceId + "/availability")
                        .param("date", at(30, 10).atZone(TestData.MADRID).toLocalDate().toString()))
                .andExpect(jsonPath("$[0].available").value(false));
    }

    @Test
    void availabilityValidatesItsInput() throws Exception {
        mvc.perform(get("/api/v1/spaces/" + UUID.randomUUID() + "/availability").param("date", "2030-01-16"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/spaces/" + spaceId + "/availability")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/spaces/" + spaceId + "/availability").param("date", "16/01/2030"))
                .andExpect(status().isBadRequest());
    }

    private org.springframework.test.web.servlet.ResultActions book(String token, Instant start) throws Exception {
        return mvc.perform(post("/api/v1/bookings").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(bookingBody(spaceId, start)));
    }

    private void assertRule(Instant start, String rule) throws Exception {
        book(alice, start).andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.rule").value(rule));
    }
}
