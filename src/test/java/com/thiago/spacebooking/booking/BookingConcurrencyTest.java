package com.thiago.spacebooking.booking;

import static com.thiago.spacebooking.TestData.at;
import static com.thiago.spacebooking.TestData.bookingBody;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.thiago.spacebooking.TestAuth;
import com.thiago.spacebooking.TestData;
import com.thiago.spacebooking.TestcontainersConfiguration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The guarantees that only hold under real concurrency, against a real PostgreSQL:
 * the no-overlap constraint (R4) and the per-user limit (R7).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class BookingConcurrencyTest {

    @Autowired
    MockMvc mvc;

    @Test
    void tenUsersBookingTheSameSlotAtTheSameTimeProduceExactlyOneBooking() throws Exception {
        String admin = TestAuth.adminToken(mvc);
        String spaceId = TestData.newSpace(mvc, admin);
        Instant slot = at(1, 10);
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tokens.add(TestAuth.userToken(mvc));
        }

        List<Integer> statuses = runAtTheSameTime(tokens.stream()
                .<Callable<Integer>>map(token -> () -> postBooking(token, spaceId, slot)).toList());

        assertThat(statuses.stream().filter(s -> s == 201)).hasSize(1);
        assertThat(statuses.stream().filter(s -> s == 409)).hasSize(9);
        String date = slot.atZone(TestData.MADRID).toLocalDate().toString();
        mvc.perform(get("/api/v1/admin/bookings").header("Authorization", admin).param("spaceId", spaceId).param("date", date))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void oneUserSendingParallelRequestsCannotExceedTheActiveBookingLimit() throws Exception {
        String admin = TestAuth.adminToken(mvc);
        String spaceId = TestData.newSpace(mvc, admin);
        String user = TestAuth.userToken(mvc);

        // Six different free slots requested at once: the limit is 3.
        List<Integer> statuses = runAtTheSameTime(java.util.stream.IntStream.range(9, 15)
                .<Callable<Integer>>mapToObj(hour -> () -> postBooking(user, spaceId, at(1, hour))).toList());

        assertThat(statuses.stream().filter(s -> s == 201)).hasSize(3);
        assertThat(statuses.stream().filter(s -> s == 422)).hasSize(3);
        mvc.perform(get("/api/v1/bookings/me").header("Authorization", user).param("status", "CONFIRMED"))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    private int postBooking(String token, String spaceId, Instant start) throws Exception {
        return mvc.perform(post("/api/v1/bookings").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(bookingBody(spaceId, start)))
                .andReturn().getResponse().getStatus();
    }

    /** Starts every task at the same instant (all threads wait on a latch) and returns their results. */
    private static <T> List<T> runAtTheSameTime(List<Callable<T>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        try {
            CountDownLatch ready = new CountDownLatch(tasks.size());
            CountDownLatch go = new CountDownLatch(1);
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return task.call();
                }));
            }
            ready.await();
            go.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
