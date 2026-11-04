package com.lifescript;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

// A throwaway PostgreSQL in Docker for tests that start the whole app; never the real data.
@TestConfiguration(proxyBeanMethods = false)
public class TestDatabase {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:17");
    }
}
