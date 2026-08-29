package de.uni_leipzig.eva.tausendfuessler.common.protocol;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProtocolJsonTest {

    @Test
    void roundTripsEveryMessageType() {
        List<Message> samples = List.of(
                new Message.Register("w1", 4, "secret"),
                new Message.Register("w2", 4, null),
                new Message.Registered("w1"),
                new Message.RequestWork("w1", 3),
                new Message.WorkPackage("job", 1, List.of("https://a", "https://b"), List.of()),
                new Message.NoWork(500),
                new Message.PageResult("w1", "job", "https://a", 1, 200, "T", "snip", List.of("https://b"), null, 1L),
                new Message.JobSignal("job", Message.Signal.PAUSE),
                new Message.Error("boom")
        );
        for (Message m : samples) {
            String line = ProtocolJson.encode(m);
            assertThat(line).doesNotContain("\n");
            assertThat(ProtocolJson.decode(line)).isEqualTo(m);
        }
    }

    @Test
    void typeFieldIsTheDiscriminator() {
        assertThat(ProtocolJson.encode(new Message.NoWork(1))).contains("\"type\":\"NO_WORK\"");
    }
}
