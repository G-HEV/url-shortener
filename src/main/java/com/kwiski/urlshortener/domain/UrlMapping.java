package com.kwiski.urlshortener.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
    name = "url_mapping",
    indexes = @Index(name = "idx_url_mapping_short_key", columnList = "short_key", unique = true)
)
public class UrlMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "short_key", nullable = false, unique = true, length = 16)
    private String shortKey;

    @Column(name = "original_url", nullable = false, length = 2048)
    private String originalUrl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "click_count", nullable = false)
    private long clickCount = 0;

    protected UrlMapping() {
    }

    public UrlMapping(String shortKey, String originalUrl, Instant createdAt) {
        this.shortKey = shortKey;
        this.originalUrl = originalUrl;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getShortKey() {
        return shortKey;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public long getClickCount() {
        return clickCount;
    }

    public void incrementClickCount() {
        this.clickCount++;
    }
}
