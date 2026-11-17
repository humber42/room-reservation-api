package com.example.roomreservation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityIntegrationTest {
    @Autowired MockMvc mvc;

    @ParameterizedTest
    @ValueSource(strings = {"/api/rooms", "/api/bookings"})
    void listsArePublic(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/rooms/999999", "/api/bookings/999999"})
    void missingResourcesArePublicProblemDetails(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/rooms", "/api/bookings"})
    void writesRequireCredentials(String path) throws Exception {
        mvc.perform(post(path).contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized()).andExpect(header().exists("WWW-Authenticate"));
        mvc.perform(post(path).with(httpBasic("reviewer", "wrong")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deletionRequiresCredentialsAndAuthenticatedRequestReachesApplication() throws Exception {
        mvc.perform(delete("/api/bookings/999999")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/bookings/999999").with(httpBasic("reviewer", "test-password")))
                .andExpect(status().isNotFound());
    }

    @Test
    void authenticatedCreationIsStatelessAndDuplicateIsConflict() throws Exception {
        String body = "{\"name\":\"Security test room\",\"capacity\":4}";
        var result = mvc.perform(post("/api/rooms").with(httpBasic("reviewer", "test-password"))
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andExpect(cookie().doesNotExist("JSESSIONID")).andReturn();
        assertThat(result.getRequest().getSession(false)).isNull();
        mvc.perform(post("/api/rooms").contentType("application/json").content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/rooms").with(httpBasic("reviewer", "test-password"))
                        .contentType("application/json").content(body))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void validationAndMalformedJsonUseProblemDetails() throws Exception {
        for (String body : new String[]{"{}", "{"}) {
            mvc.perform(post("/api/bookings").with(httpBasic("reviewer", "test-password"))
                            .contentType("application/json").content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.status").value(400));
        }
    }

    @Test
    void unlistedMethodsAndPathsAreDenied() throws Exception {
        mvc.perform(put("/api/rooms/1").with(httpBasic("reviewer", "test-password")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/private").with(httpBasic("reviewer", "test-password")))
                .andExpect(status().isForbidden());
    }
}
