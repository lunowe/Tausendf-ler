package de.uni_leipzig.eva.tausendfuessler.common.protocol;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/** Single shared ObjectMapper for the socket protocol. One message per line, no embedded newlines. */
public final class ProtocolJson {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private ProtocolJson() {}

    public static String encode(Message message) {
        try {
            return MAPPER.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot encode message " + message, e);
        }
    }

    public static Message decode(String line) {
        try {
            return MAPPER.readValue(line, Message.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot decode message line: " + line, e);
        }
    }
}
