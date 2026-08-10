package com.kwiski.urlshortener.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class RateLimiterServiceTest {

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
    RateLimiterService limiter;

    @Autowired
    StringRedisTemplate redisTemplate;

    private static final int CAPACITY = 5;
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final String KEY = "test:tb:user";

    private static final long T0 = 1_700_000_000_000L;

    @BeforeEach
    void clean() {

        redisTemplate.delete(KEY);
    }

    private boolean allow(long now) {
        return limiter.allowTokenBucket(KEY, CAPACITY, WINDOW, now).allowed();
    }

    private long retryAfter(long now) {
        return limiter.allowTokenBucket(KEY, CAPACITY, WINDOW, now).retryAfterSeconds();
    }

    private void drainBucket() {
        for (int i = 0; i < CAPACITY; i++) {
            allow(T0);
        }
    }

    @Test
    @DisplayName("a full bucket allows exactly `capacity` requests and rejects the next")
    void burstUpToCapacity() {

        for (int i = 1; i <= CAPACITY; i++) {
            assertThat(allow(T0))
                    .as("request %d of the burst — the bucket starts full", i)
                    .isTrue();
        }

        assertThat(allow(T0))
                .as("request %d — beyond capacity, no tokens left", CAPACITY + 1)
                .isFalse();
    }

    @Test
    @DisplayName("exactly one token is refilled after 12 s")
    void refillAfterTwelveSeconds() {

        drainBucket();

        assertThat(allow(T0 + 12_000))
                .as("after 12 s there should be exactly one token")
                .isTrue();

        assertThat(allow(T0 + 12_000))
                .as("second request at the same instant — the token is already spent")
                .isFalse();
    }

    @Test
    @DisplayName("11.9 s is not enough, 12.0 s is — the boundary sits exactly where it should")
    void refillBoundary() {

        drainBucket();
        assertThat(allow(T0 + 11_900))
                .as("11.9 s yields 0.99 of a token — a fraction is not enough")
                .isFalse();

        redisTemplate.delete(KEY);
        drainBucket();
        assertThat(allow(T0 + 12_000))
                .as("12.0 s yields a whole token")
                .isTrue();
    }

    @Test
    @DisplayName("after a long idle period the bucket refills no further than capacity")
    void capacityCeiling() {
        drainBucket();

        int passed = 0;
        for (int i = 0; i < CAPACITY + 3; i++) {
            if (allow(T0 + 600_000)) {
                passed++;
            }
        }

        assertThat(passed)
                .as("a 10 min refill is 50 tokens, but the ceiling is %d", CAPACITY)
                .isEqualTo(CAPACITY);
    }

    @Test
    @DisplayName("Retry-After decreases as tokens refill: 12 -> 6 -> 3 -> 0")
    void retryAfterDecreasesOverTime() {
        drainBucket();

        assertThat(retryAfter(T0))
                .as("empty bucket: a full 12 s until one token")
                .isEqualTo(12);

        assertThat(retryAfter(T0 + 6_000))
                .as("after 6 s there is half a token, so half the wait remains")
                .isEqualTo(6);

        assertThat(retryAfter(T0 + 9_000))
                .as("after 9 s there is 0.75 of a token")
                .isEqualTo(3);

        assertThat(retryAfter(T0 + 12_000))
                .as("after 12 s the token is there — the request passes, so 0")
                .isEqualTo(0);

    }

    @Test
    @DisplayName("a rejection returns at least 1 second, never 0")
    void rejectionNeverSaysZeroSeconds() {
        drainBucket();

        assertThat(retryAfter(T0 + 11_999))
                .as("a fraction of a token is missing, but the answer must not read 'come back now'")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rejected requests do not consume tokens")
    void rejectionDoesNotConsumeToken() {
        drainBucket();

        for (int i = 1; i <= 10; i++) {
            assertThat(allow(T0))
                    .as("rejected request %d", i)
                    .isFalse();
        }

        assertThat(allow(T0 + 12_000))
                .as("ten rejections must not have delayed the refill")
                .isTrue();
    }
}
