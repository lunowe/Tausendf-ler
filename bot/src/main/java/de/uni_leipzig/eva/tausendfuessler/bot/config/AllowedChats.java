package de.uni_leipzig.eva.tausendfuessler.bot.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Chat allowlist from {@code telegram.allowed-chats} (env {@code TELEGRAM_ALLOWED_CHATS}, comma-separated chat ids).
 * An empty list means the bot is public.
 */
@Component
public class AllowedChats {

    private static final Logger log = LoggerFactory.getLogger(AllowedChats.class);

    private final Set<Long> chatIds;

    public AllowedChats(@Value("${telegram.allowed-chats:}") String csv) {
        this.chatIds = parse(csv);
        if (chatIds.isEmpty()) {
            log.warn("TELEGRAM_ALLOWED_CHATS not set - every Telegram chat may use this bot");
        } else {
            log.info("bot restricted to {} chat(s): {}", chatIds.size(), chatIds);
        }
    }

    public boolean allows(long chatId) {
        return chatIds.isEmpty() || chatIds.contains(chatId);
    }

    /** True when no allowlist is configured. */
    public boolean isOpen() {
        return chatIds.isEmpty();
    }

    private static Set<Long> parse(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::parseLong)
                .collect(Collectors.toUnmodifiableSet());
    }
}
