package com.kwiski.urlshortener.repository;

import com.kwiski.urlshortener.domain.UrlMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UrlMappingRepository extends JpaRepository<UrlMapping, Long> {

    Optional<UrlMapping> findByShortKey(String shortKey);

    boolean existsByShortKey(String shortKey);

    @Modifying
    @Query("UPDATE UrlMapping u SET u.clickCount = u.clickCount + :delta WHERE u.shortKey = :key")
    int incrementClickCount(@Param("key") String key, @Param("delta") long delta);
}
