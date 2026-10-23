package com.loopers

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(properties = ["pg.callback-delay=1h"])
class PgSimulatorContextTest {
    @Test
    fun contextLoads() = Unit
}
