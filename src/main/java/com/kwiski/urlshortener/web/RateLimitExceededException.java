package com.kwiski.urlshortener.web;

import com.kwiski.urlshortener.dto.RateLimiterResult;

public class RateLimitExceededException extends RuntimeException {
    long retryAfterSeconds;

    public RateLimitExceededException(RateLimiterResult rlr, String message) {
        super(message);
        retryAfterSeconds = rlr.retryAfterSeconds();
    }

    long getRetryAfterSeconds() {
        return this.retryAfterSeconds;
    }
}
