package de.uni_leipzig.eva.tausendfuessler.bot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "telegram.bot.token=test-token",
        "telegram.bot.username=test_bot",
        "telegram.bot.register=false"   // kein Long-Polling gegen Telegram im Test
})
class TelegrambotApplicationTests {

    @Test
    void contextLoads() {
    }
}
