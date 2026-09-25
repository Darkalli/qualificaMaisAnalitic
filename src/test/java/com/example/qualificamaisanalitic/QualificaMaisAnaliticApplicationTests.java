package com.example.qualificamaisanalitic;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = "app.sheets.check-enabled=false")
@ActiveProfiles("test")
class QualificaMaisAnaliticApplicationTests {

    @Test
    void contextLoads() {
    }

}
