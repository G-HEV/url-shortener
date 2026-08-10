package com.kwiski.urlshortener.dto;

public record RateLimiterResult(boolean allowed, long retryAfterSeconds) {

}
