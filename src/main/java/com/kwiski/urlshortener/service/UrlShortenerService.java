package com.kwiski.urlshortener.service;

import com.kwiski.urlshortener.domain.UrlMapping;
import com.kwiski.urlshortener.repository.UrlMappingRepository;
import com.kwiski.urlshortener.web.ShortKeyNotFoundException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;

@Service
public class UrlShortenerService {

    private static final String BASE62 =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int KEY_LENGTH = 7;
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    private final SecureRandom random = new SecureRandom();
    private final UrlMappingRepository repository;
    private final StringRedisTemplate redis;
    private final Counter shortenCounter;
    private final MeterRegistry meterRegistry;
    private final Timer resolveHitTimer;
    private final Timer resolveMissTimer;
    private final Counter resolveNotFoundCounter;

    public UrlShortenerService(UrlMappingRepository repository, StringRedisTemplate redis, MeterRegistry meterRegistry) {
        this.repository = repository;
        this.redis = redis;
        this.shortenCounter = Counter.builder("urlshortener.shorten.count")
                .description("Number of short URLs created")
                .register(meterRegistry);
        this.meterRegistry = meterRegistry;
        this.resolveHitTimer = Timer.builder("urlshortener.resolve").
                description("Time to resolve a short key").tag("cache", "hit").publishPercentileHistogram().register(meterRegistry);
        this.resolveMissTimer = Timer.builder("urlshortener.resolve").
                description("Time to resolve a short key").tag("cache", "miss").publishPercentileHistogram().register(meterRegistry);
        this.resolveNotFoundCounter = Counter.builder("urlshortener.resolve.notfound").
                description("Lookups for a non-existent key").register(meterRegistry);
    }

    public UrlMapping shorten(String originalUrl) {
        String key = generateUniqueKey();
        UrlMapping mapping = new UrlMapping(key, originalUrl, Instant.now());

        UrlMapping returnUrl = repository.save(mapping);
        shortenCounter.increment();
        return returnUrl;
    }

    public String resolveOriginalUrl(String key) {
        if (key == null) {
            throw new IllegalArgumentException("key is null");
        }
        Timer.Sample sample = Timer.start(meterRegistry);
        String cacheKey = "url:" + key;
        String url = redis.opsForValue().get(cacheKey);
        boolean cacheHit;
        if (url == null) {

            url = repository.findByShortKey(key)
                    .orElseThrow(() -> {
                        resolveNotFoundCounter.increment();
                        return new ShortKeyNotFoundException("key: " + key + " not found");
                    })
                    .getOriginalUrl();
            redis.opsForValue().set(cacheKey, url, CACHE_TTL);
            cacheHit = false;
        } else {
            cacheHit = true;
        }
        redis.opsForValue().increment("clicks:" + key);
        sample.stop(cacheHit ? resolveHitTimer : resolveMissTimer);
        return url;
    }

    private String generateUniqueKey() {
        boolean isKeyNotExist = false;
        StringBuilder key;
        String keyString = "";
        while (!isKeyNotExist) {
            key = new StringBuilder();
            for (int i = 0; i < KEY_LENGTH; i++) {
                char character = BASE62.charAt(random.nextInt(BASE62.length()));
                key.append(character);
            }
            keyString = key.toString();
            if (!repository.existsByShortKey(keyString)) {
                isKeyNotExist = true;
            }
        }
        return keyString;
    }
}
