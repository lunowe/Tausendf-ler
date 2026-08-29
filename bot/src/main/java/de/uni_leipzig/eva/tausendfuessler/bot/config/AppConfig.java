package de.uni_leipzig.eva.tausendfuessler.bot.config;

import de.uni_leipzig.eva.tausendfuessler.bot.service.ApiKeyInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
@EnableScheduling
public class AppConfig {

    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);

    /** REST client for the coordinator; sends {@code X-Api-Key} when {@code coordinator.api.key} (env API_KEY) is set. */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder,
                                     @Value("${coordinator.api.key:}") String apiKey) {
        builder = builder
                .connectTimeout(Duration.ofSeconds(3))
                .readTimeout(Duration.ofSeconds(10));
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("API_KEY not set - requests to the coordinator are sent without X-Api-Key");
        } else {
            builder = builder.additionalInterceptors(new ApiKeyInterceptor(apiKey.trim()));
        }
        return builder.build();
    }
}
