package com.example.telegrambot;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@SpringBootApplication
public class TelegrambotApplication {

	public static void main(String[] args) {
		SpringApplication.run(TelegrambotApplication.class, args);
	}

	@Bean
	public CommandLineRunner registerBot(TausflerBot bot) {
		return args -> {
			try {
				TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
				botsApi.registerBot(bot);
				System.out.println(">>>>> BOT ERFOLGREICH REGISTRIERT <<<<<");
			} catch (TelegramApiException e) {
				System.err.println(">>>>> FEHLER BEI BOT-REGISTRIERUNG <<<<<");
				e.printStackTrace();
			}
		};
	}

}
