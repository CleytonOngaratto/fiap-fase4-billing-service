package br.com.fiap.postech.carworkshop.billing.infrastructure.messaging;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

// Postgres de verdade em vez do H2 do perfil de teste: so assim a V1/V2 sao aplicadas pelo Flyway, e o
// indice parcial da V2 nem existiria no H2. No %test o Flyway esta desligado e o schema vem do create-drop.
public class BillingMessagingTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "quarkus.datasource.db-kind", "postgresql",
                "quarkus.hibernate-orm.dialect", "org.hibernate.dialect.PostgreSQLDialect",
                "quarkus.hibernate-orm.schema-management.strategy", "none",
                "quarkus.hibernate-orm.sql-load-script", "no-file",
                "quarkus.flyway.migrate-at-start", "true");
    }
}
