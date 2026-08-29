package de.uni_leipzig.eva.tausendfuessler.coordinator;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Same web environment as the other integration tests so all of them share one cached Spring context. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CoordinatorApplicationTest {

    @Test
    void contextLoads() {
    }
}
