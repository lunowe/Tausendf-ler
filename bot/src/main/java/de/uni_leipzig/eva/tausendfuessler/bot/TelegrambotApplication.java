package de.uni_leipzig.eva.tausendfuessler.bot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@SpringBootApplication
public class TelegrambotApplication {

    private static final Logger log = LoggerFactory.getLogger(TelegrambotApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(TelegrambotApplication.class, args);
    }

    /** Kann in Tests per {@code telegram.bot.register=false} abgeschaltet werden. */
    @Bean
    @ConditionalOnProperty(name = "telegram.bot.register", havingValue = "true", matchIfMissing = true)
    public CommandLineRunner registerBot(TausflerBot bot) {
        return args -> {
            new TelegramBotsApi(DefaultBotSession.class).registerBot(bot);
            log.info("Telegram bot @{} registered, long polling started", bot.getBotUsername());
        };
    }
}
