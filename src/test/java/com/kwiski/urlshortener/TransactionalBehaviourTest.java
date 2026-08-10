package com.kwiski.urlshortener;

import com.kwiski.urlshortener.domain.UrlMapping;
import com.kwiski.urlshortener.repository.UrlMappingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@SpringBootTest
@Testcontainers
public class TransactionalBehaviourTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7").withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProps(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @TestConfiguration
    static class Config {
        @Bean
        ProbeService probeService(UrlMappingRepository repo) {
            return new ProbeService(repo);
        }
    }

    static class ProbeService {
        private final UrlMappingRepository repo;

        ProbeService(UrlMappingRepository repo) {
            this.repo = repo;
        }

        @Transactional
        public boolean inner() {
            return TransactionSynchronizationManager.isActualTransactionActive();
        }

        public boolean outer() {
            return inner();
        }

        @Transactional
        public void saveThenThrowChecked(String key) throws CheckedBoom {
            UrlMapping newRecord = new UrlMapping(key, "google.pl", Instant.now());
            repo.save(newRecord);
            throw new CheckedBoom();
        }

        @Transactional
        public void saveThenThrowUnchecked(String key) {
            UrlMapping newRecord = new UrlMapping(key, "google.pl", Instant.now());
            repo.save(newRecord);
            throw new RuntimeException();
        }
    }

    @Autowired
    ProbeService probe;
    @Autowired
    UrlMappingRepository repo;

    @Test
    void selfInvocation(){
        boolean viaOuter = probe.outer();
        boolean viaInner = probe.inner();
        System.out.println("A  outer() -> inner() : " + viaOuter);
        System.out.println("B  inner() directly     : " + viaInner);
        assertThat(probe.inner()).isTrue();
        assertThat(probe.outer()).isFalse();
    }

    @Test
    void saveVsException(){
        String key1 = "chk1";
        String key2 = "chk2";
        try{
            probe.saveThenThrowChecked(key1);
        }catch(CheckedBoom boom){
            System.out.println("Booom");
        }
        try{
            probe.saveThenThrowUnchecked(key2);
        }catch(RuntimeException ignore){
            System.out.println("runtime exception");
        }

        assertThat(repo.findByShortKey(key1).isPresent()).isTrue();
        assertThat(repo.findByShortKey(key2).isPresent()).isFalse();
    }

    public static class CheckedBoom extends Exception {
    }

}
