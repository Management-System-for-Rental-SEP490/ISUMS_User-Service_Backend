package com.isums.userservice;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled("Requires Keycloak/Postgres/Kafka/Redis infrastructure; run as integration test with Testcontainers")
class UserServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
