package com.kwiski.urlshortener.repository;

import com.kwiski.urlshortener.domain.Author;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AuthorRepository extends JpaRepository<Author,Long> {

    @Query("SELECT a FROM Author a LEFT JOIN FETCH a.books")
    List<Author> findAllWithBooks();

    @EntityGraph(attributePaths = "books")
    @Query("SELECT a FROM Author a")
    List<Author> findAllWithGraph();
}
