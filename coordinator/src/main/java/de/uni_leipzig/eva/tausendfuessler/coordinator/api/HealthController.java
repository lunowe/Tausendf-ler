package de.uni_leipzig.eva.tausendfuessler.coordinator.api;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {

    /** JVM start of this process; together with {@link #readyAt} this gives the real startup time. */
    private final Instant jvmStart = Instant.ofEpochMilli(ManagementFactory.getRuntimeMXBean().getStartTime());
    private volatile Instant readyAt;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        readyAt = Instant.now();
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("time", Instant.now().toString());
        Instant ready = readyAt;
        if (ready != null) {
            body.put("startupSeconds", (ready.toEpochMilli() - jvmStart.toEpochMilli()) / 1000.0);
        }
        return body;
    }
}
