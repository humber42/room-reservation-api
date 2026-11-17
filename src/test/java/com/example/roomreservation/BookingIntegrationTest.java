package com.example.roomreservation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BookingIntegrationTest {
    @Autowired MockMvc mvc;

    long room() throws Exception {
        String location = mvc.perform(post("/api/rooms").with(httpBasic("reviewer", "test-password"))
                .contentType("application/json").content("{\"name\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.capacity").value(1))
                .andReturn().getResponse().getHeader("Location");
        mvc.perform(get(location)).andExpect(status().isOk()).andExpect(jsonPath("$.capacity").value(1));
        return Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
    }

    org.springframework.test.web.servlet.ResultActions book(long room, String start, String end) throws Exception {
        return mvc.perform(post("/api/bookings").with(httpBasic("reviewer", "test-password"))
                .contentType("application/json").content("""
                {"roomId":%d,"responsible":" Alice ","startAt":"%s","endAt":"%s"}
                """.formatted(room, start, end)));
    }

    @Test
    void overlapAdjacencyCancellationAndReuse() throws Exception {
        long room = room();
        String start = "2026-12-01T10:00:00Z", end = "2026-12-01T11:00:00Z";
        String location = book(room, start, end).andExpect(status().isCreated())
                .andExpect(jsonPath("$.responsible").value("Alice"))
                .andExpect(jsonPath("$.roomId").value(room))
                .andExpect(jsonPath("$.startAt").value(start)).andExpect(jsonPath("$.endAt").value(end))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn().getResponse().getHeader("Location");
        book(room, start, end).andExpect(status().isConflict());
        book(room, "2026-12-01T09:00:00Z", "2026-12-01T12:00:00Z").andExpect(status().isConflict());
        book(room, "2026-12-01T10:15:00Z", "2026-12-01T10:45:00Z").andExpect(status().isConflict());
        book(room, end, "2026-12-01T12:00:00Z").andExpect(status().isCreated());
        book(room, "2026-12-01T09:00:00Z", start).andExpect(status().isCreated());
        for (int i = 0; i < 2; i++) mvc.perform(delete(location).with(httpBasic("reviewer", "test-password")))
                .andExpect(status().isNoContent());
        mvc.perform(get(location)).andExpect(jsonPath("$.status").value("CANCELLED"));
        book(room, start, end).andExpect(status().isCreated());
    }

    @Test
    void dateFiltersUseUtcIntersectionAndStablePagination() throws Exception {
        long room = room();
        book(room, "2026-12-01T23:30:00Z", "2026-12-02T01:00:00Z").andExpect(status().isCreated());
        book(room, "2026-12-02T01:00:00Z", "2026-12-02T02:00:00Z").andExpect(status().isCreated());
        book(room, "2026-12-03T00:00:00Z", "2026-12-03T01:00:00Z").andExpect(status().isCreated());
        mvc.perform(get("/api/bookings").param("roomId", ""+room).param("date", "2026-12-02").param("size", "1"))
                .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].startAt").value("2026-12-01T23:30:00Z"));
        mvc.perform(get("/api/bookings").param("roomId", ""+room).param("date", "2026-12-02").param("size", "1").param("page", "1"))
                .andExpect(jsonPath("$.page").value(1)).andExpect(jsonPath("$.content[0].startAt").value("2026-12-02T01:00:00Z"));
        mvc.perform(get("/api/bookings").param("roomId", ""+room)).andExpect(jsonPath("$.totalElements").value(3));
        mvc.perform(get("/api/bookings").param("date", "2026-12-02")).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=-1", "size=0", "size=101", "roomId=0", "roomId=-1", "date=invalid", "page=abc", "date=+999999999-12-31"})
    void invalidQueryIsBadRequest(String query) throws Exception {
        String[] pair = query.split("=");
        mvc.perform(get("/api/bookings").param(pair[0], pair[1])).andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void invalidRangeAndMissingRoom() throws Exception {
        book(999999, "2026-12-01T10:00:00Z", "2026-12-01T10:00:00Z").andExpect(status().isBadRequest());
        book(999999, "2026-12-01T11:00:00Z", "2026-12-01T10:00:00Z").andExpect(status().isBadRequest());
        book(999999, "2026-12-01T10:00:00Z", "2026-12-01T11:00:00Z").andExpect(status().isNotFound());
    }

    @Test
    void simultaneousBookingsCommitExactlyOneReservation() throws Exception {
        long room = room();
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Integer> attempt = () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start timeout");
                return book(room, "2026-12-10T10:00:00Z", "2026-12-10T11:00:00Z").andReturn().getResponse().getStatus();
            };
            var first = pool.submit(attempt);
            var second = pool.submit(attempt);
            try { assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); }
            finally { start.countDown(); }
            assertThat(java.util.List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        }
        mvc.perform(get("/api/bookings").param("roomId", ""+room))
                .andExpect(jsonPath("$.totalElements").value(1));
    }
    @Test
    void timestampPrecisionIsValidatedBeforeStorage() throws Exception {
        long room = room();
        book(room,"2026-12-01T10:00:00.000000001Z","2026-12-01T10:00:00.000000002Z")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("timestamps must use microsecond precision or coarser"));
        String location = book(room,"2026-12-01T10:00:00.000001Z","2026-12-01T10:00:00.000002Z")
                .andExpect(status().isCreated()).andReturn().getResponse().getHeader("Location");
        mvc.perform(get(location)).andExpect(jsonPath("$.startAt").value("2026-12-01T10:00:00.000001Z"))
                .andExpect(jsonPath("$.endAt").value("2026-12-01T10:00:00.000002Z"));
        book(room,"2026-12-01T10:00:00.000002Z","2026-12-01T10:00:00.000003Z").andExpect(status().isCreated());
    }

    @Test
    void simultaneousCaseVariantsCreateOnlyOneRoom() throws Exception {
        String name = "Race-" + UUID.randomUUID();
        var barrier = new CyclicBarrier(2);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var futures = new java.util.ArrayList<Future<Integer>>();
            for (String variant : java.util.List.of(name.toLowerCase(java.util.Locale.ROOT), name.toUpperCase(java.util.Locale.ROOT))) {
                futures.add(pool.submit(() -> {
                    barrier.await(10, TimeUnit.SECONDS);
                    return mvc.perform(post("/api/rooms").with(httpBasic("reviewer","test-password"))
                            .contentType("application/json").content("{\"name\":\"" + variant + "\"}"))
                            .andReturn().getResponse().getStatus();
                }));
            }
            assertThat(java.util.List.of(futures.get(0).get(20, TimeUnit.SECONDS), futures.get(1).get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201,409);
        }
    }
}
