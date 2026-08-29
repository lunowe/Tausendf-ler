package de.uni_leipzig.eva.tausendfuessler.coordinator.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseUrlEnvironmentPostProcessorTest {

    @Test
    void translatesRailwayStyleUrl() {
        Map<String, Object> p = DatabaseUrlEnvironmentPostProcessor.toDataSourceProperties(
                "postgres://tf_user:s3cr%40t@monorail.proxy.rlwy.net:43812/railway?sslmode=require");
        assertThat(p).containsEntry("spring.datasource.url", "jdbc:postgresql://monorail.proxy.rlwy.net:43812/railway?sslmode=require")
                .containsEntry("spring.datasource.username", "tf_user")
                .containsEntry("spring.datasource.password", "s3cr@t");
    }

    @Test
    void defaultsPortAndToleratesMissingPassword() {
        Map<String, Object> p = DatabaseUrlEnvironmentPostProcessor.toDataSourceProperties("postgresql://u@db.internal/tf");
        assertThat(p).containsEntry("spring.datasource.url", "jdbc:postgresql://db.internal:5432/tf")
                .containsEntry("spring.datasource.username", "u")
                .doesNotContainKey("spring.datasource.password");
    }

    @Test
    void ignoresNonPostgresOrGarbage() {
        assertThat(DatabaseUrlEnvironmentPostProcessor.toDataSourceProperties("jdbc:postgresql://x/y")).isEmpty();
        assertThat(DatabaseUrlEnvironmentPostProcessor.toDataSourceProperties("mysql://a@b/c")).isEmpty();
        assertThat(DatabaseUrlEnvironmentPostProcessor.toDataSourceProperties("::not a url::")).isEmpty();
    }
}
