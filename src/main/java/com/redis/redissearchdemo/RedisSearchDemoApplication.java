package com.redis.redissearchdemo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.om.spring.annotations.EnableRedisDocumentRepositories;
import com.redis.redissearchdemo.service.MovieService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableRedisDocumentRepositories
public class RedisSearchDemoApplication {

    static void main(String[] args) {
        SpringApplication.run(RedisSearchDemoApplication.class, args);
    }

    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    @Bean
    CommandLineRunner loadData(MovieService movieService) {
        return args -> {
            if (movieService.isDataLoaded()) {
                System.out.println("Data already loaded. Skipping data load.");
                return;
            }
            movieService.loadAndSaveMovies("movies.json");
        };
    }
}
