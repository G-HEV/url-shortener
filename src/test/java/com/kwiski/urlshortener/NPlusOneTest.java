package com.kwiski.urlshortener;

import com.kwiski.urlshortener.domain.Author;
import com.kwiski.urlshortener.domain.Book;
import com.kwiski.urlshortener.repository.AuthorRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Testcontainers
class NPlusOneTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7").withExposedPorts(6379);

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.data.redis.host", redis::getHost);
        r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    AuthorRepository authors;
    @Autowired
    EntityManagerFactory emf;
    @PersistenceContext
    EntityManager em;

    long stmts() {
        return emf.unwrap(SessionFactory.class).getStatistics().getPrepareStatementCount();
    }

    @Test
    @Transactional
    void nPlusOne() {

        for (int a = 1; a <= 3; a++) {
            Author author = new Author("Author " + a);
            for (int b = 1; b <= 4; b++) author.addBook(new Book("Book " + a + "." + b));
            authors.save(author);
        }
        emf.unwrap(SessionFactory.class).getStatistics().clear();

        long before = stmts();
        List<Author> authorList = authors.findAll();
        System.out.println("B  findAll without touching books : " + (stmts() - before));
        em.flush();
        em.clear();

        before = stmts();
        for (Author a : authors.findAllWithBooks())
            for (Book b : a.getBooks()) b.getTitle();
        System.out.println("D  JOIN FETCH                     : " + (stmts() - before));
        em.flush();
        em.clear();

        before = stmts();
        for (Author a : authors.findAllWithGraph())
            for (Book b : a.getBooks()) b.getTitle();
        System.out.println("E  @EntityGraph : " + (stmts() - before));
        em.flush();
        em.clear();

        before = stmts();
        for (Author a : authors.findAll())
            for (Book b : a.getBooks()) b.getTitle();
        System.out.println("F  @BatchSize   : " + (stmts() - before));
        em.flush();
        em.clear();
    }

}
