package com.findback.module3;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The PostgreSQL concurrency suite deletes all rows, so it must never be pointed at the dev database. */
class PostgresTestDatabaseGuardTest {

    @Test
    void acceptsDedicatedTestDatabase() {
        String url = "jdbc:postgresql://localhost:5432/findback_test?sslmode=disable";
        assertEquals(url, ClaimConcurrencyPostgresTest.requireTestDatabase(url));
    }

    @Test
    void refusesTheDevelopmentDatabase() {
        assertThrows(IllegalStateException.class,
                () -> ClaimConcurrencyPostgresTest.requireTestDatabase("jdbc:postgresql://localhost:5432/findback"));
    }

    @Test
    void refusesTestOnlyInHostOrParameters() {
        assertThrows(IllegalStateException.class,
                () -> ClaimConcurrencyPostgresTest.requireTestDatabase("jdbc:postgresql://test-host:5432/findback?app=test"));
    }

    @Test
    void refusesNonPostgresUrls() {
        assertThrows(IllegalStateException.class,
                () -> ClaimConcurrencyPostgresTest.requireTestDatabase("jdbc:h2:mem:findback_test"));
    }
}
