package com.kwiski.urlshortener.job;

import com.kwiski.urlshortener.repository.UrlMappingRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Component
public class ClickFlushJob {

    private static final String CLICKS_PREFIX = "clicks:";

    private final StringRedisTemplate redis;
    private final UrlMappingRepository repository;

    public ClickFlushJob(StringRedisTemplate redis, UrlMappingRepository repository) {
        this.redis = redis;
        this.repository = repository;
    }

    @Scheduled(fixedRateString = "${app.click-flush-rate-ms:60000}")
    @Transactional
    public void flushClicks() {
        Set<String> keys = redis.keys(CLICKS_PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            return;
        }
        for (String redisKey : keys) {
            String shortKey = redisKey.substring(CLICKS_PREFIX.length());

            String val = redis.opsForValue().getAndDelete(redisKey);
            if (val == null) {
                continue;
            }
            long delta = Long.parseLong(val);
            if (delta > 0) {
                repository.incrementClickCount(shortKey, delta);
            }
        }
    }
}
