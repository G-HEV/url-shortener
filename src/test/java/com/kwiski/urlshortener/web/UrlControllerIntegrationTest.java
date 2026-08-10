package com.kwiski.urlshortener.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class UrlControllerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProps(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    StringRedisTemplate redisTemplate;

    @BeforeEach
    void resetLimiter() {

        redisTemplate.delete("rate:tb:admin");
    }

    private String logIn() throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"secret\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return body.replaceAll(".*\"token\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    }

    private String shorten(String url) throws Exception {
        String body = mockMvc.perform(post("/api/shorten")
                        .header("Authorization", "Bearer " + logIn())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"" + url + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return body.replaceAll(".*\"shortKey\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    }

    @Test
    @DisplayName("POST /api/shorten without a token is rejected")
    void rejectedWithoutToken() throws Exception {

        mockMvc.perform(post("/api/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com\"}"))
                .andExpect(status().isForbidden());

    }

    @Test
    @DisplayName("POST /api/shorten with a token returns 201 with shortKey and shortUrl")
    void shortensWithToken() throws Exception {
        mockMvc.perform(post("/api/shorten")
                        .header("Authorization", "Bearer " + logIn())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/test\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortKey").exists())
                .andExpect(jsonPath("$.shortUrl").exists());
    }

    @Test
    @DisplayName("GET /{key} redirects with 302 to the original URL, no login required")
    void redirects() throws Exception {
        String original = "https://example.com/target";
        String key = shorten(original);

        mockMvc.perform(get("/" + key))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", original));
    }

    @Test
    @DisplayName("GET of an unknown key returns 404")
    void unknownKey() throws Exception {
        mockMvc.perform(get("/ZZZZZZZ"))
                .andExpect(status().isNotFound());

    }

    @Test
    @DisplayName("POST with a non-http/https URL returns 400")
    void validationRejectsWrongScheme() throws Exception {
        mockMvc.perform(post("/api/shorten")
                        .header("Authorization", "Bearer " + logIn())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"ftp://example.com\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST with an empty URL returns 400")
    void validationRejectsEmptyUrl() throws Exception {
        mockMvc.perform(post("/api/shorten")
                        .header("Authorization", "Bearer " + logIn())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("the sixth POST within a minute gets 429")
    void rateLimitOverHttp() throws Exception {

        String token = logIn();

        for (int i = 1; i <= 5; i++) {
            mockMvc.perform(post("/api/shorten")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"url\":\"https://example.com/" + i + "\"}"))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(post("/api/shorten")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/sixth\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "12"));

    }
}
