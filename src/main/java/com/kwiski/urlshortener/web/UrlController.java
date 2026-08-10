package com.kwiski.urlshortener.web;

import com.kwiski.urlshortener.domain.UrlMapping;
import com.kwiski.urlshortener.dto.RateLimiterResult;
import com.kwiski.urlshortener.service.RateLimiterService;
import com.kwiski.urlshortener.service.UrlShortenerService;
import com.kwiski.urlshortener.web.dto.ShortenRequest;
import com.kwiski.urlshortener.web.dto.ShortenResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.Duration;

@RestController
@RequestMapping("/api")
public class UrlController {

    private static final int SHORTEN_LIMIT = 5;
    private static final Duration SHORTEN_WINDOW = Duration.ofMinutes(1);

    private final UrlShortenerService service;
    private final RateLimiterService rateLimiter;

    public UrlController(UrlShortenerService service, RateLimiterService rateLimiter) {
        this.service = service;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/shorten")
    public ResponseEntity<ShortenResponse> shorten(@Valid @RequestBody ShortenRequest request,
                                                   Authentication authentication) {

        String user = authentication.getName();
        RateLimiterResult rlr = rateLimiter.allowTokenBucket("rate:tb:" + user, SHORTEN_LIMIT, SHORTEN_WINDOW);
        if (!rlr.allowed()) {
            throw new RateLimitExceededException(rlr, "Rate limit of " + SHORTEN_LIMIT + " requests/min exceeded");
        }

        UrlMapping saved = service.shorten(request.url());

        String shortUrl = ServletUriComponentsBuilder
                .fromCurrentContextPath()
                .path("/{key}")
                .buildAndExpand(saved.getShortKey())
                .toUriString();

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ShortenResponse(saved.getShortKey(), shortUrl));
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<String> handleException(RateLimitExceededException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER,
                String.valueOf(ex.getRetryAfterSeconds())).body(ex.getMessage());
    }
}
