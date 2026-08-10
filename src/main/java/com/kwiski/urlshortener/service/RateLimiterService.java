package com.kwiski.urlshortener.service;

import com.kwiski.urlshortener.dto.RateLimiterResult;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
public class RateLimiterService {

    private final StringRedisTemplate redis;
    private static final RedisScript<Long> TOKEN_BUCKET =
            RedisScript.of(new ClassPathResource("scripts/token_bucket.lua"), Long.class);

    public RateLimiterService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public RateLimiterResult allowTokenBucket(String key, int capacity, Duration window) {
        return allowTokenBucket(key, capacity, window, System.currentTimeMillis());
    }

    RateLimiterResult allowTokenBucket(String key, int capacity, Duration window, long nowMillis) {
        if (key == null || window == null) {
            throw new NullPointerException("key or window cannot be null");
        }
        long windowMillis = window.toMillis();

        long ttlSeconds = (long) Math.ceil((double) windowMillis / 1000);

        long res = redis.execute(TOKEN_BUCKET, List.of(key),
                String.valueOf(capacity),
                String.valueOf(windowMillis),
                String.valueOf(nowMillis),
                String.valueOf(ttlSeconds));

        return new RateLimiterResult(res == 0, res);
    }

    public boolean allow(String key, int limit, Duration window) {
        if (key == null || window == null) {
            throw new NullPointerException("key or window cannot be null");
        }
        Long keyRedis = redis.opsForValue().increment(key);
        if (keyRedis == 1L) {
            redis.expire(key, window);
        }
        return keyRedis <= limit;
    }
}
