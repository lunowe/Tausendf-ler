package de.uni_leipzig.eva.tausendfuessler.bot.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AllowedChatsTest {

    @Test
    void emptyListAllowsEveryChat() {
        AllowedChats open = new AllowedChats("");
        assertThat(open.isOpen()).isTrue();
        assertThat(open.allows(42L)).isTrue();
        assertThat(open.allows(-100123L)).isTrue();
        assertThat(new AllowedChats(null).allows(1L)).isTrue();
    }

    @Test
    void onlyListedChatsAreAllowed() {
        AllowedChats chats = new AllowedChats(" 42, -100123 ,,7");
        assertThat(chats.isOpen()).isFalse();
        assertThat(chats.allows(42L)).isTrue();
        assertThat(chats.allows(-100123L)).isTrue();
        assertThat(chats.allows(7L)).isTrue();
        assertThat(chats.allows(8L)).isFalse();
    }

    @Test
    void rejectsNonNumericIds() {
        assertThatThrownBy(() -> new AllowedChats("42,abc")).isInstanceOf(NumberFormatException.class);
    }
}
