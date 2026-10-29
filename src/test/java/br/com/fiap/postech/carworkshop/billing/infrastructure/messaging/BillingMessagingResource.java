package br.com.fiap.postech.carworkshop.billing.infrastructure.messaging;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

public class BillingMessagingResource implements QuarkusTestResourceLifecycleManager {

    public static final String COMMANDS_QUEUE = "billing-commands.fifo";

    private static final DockerImageName ELASTICMQ =
            DockerImageName.parse("softwaremill/elasticmq-native:1.7.1");
    private static final int SQS_PORT = 9324;

    private GenericContainer<?> elasticMq;
    private PostgreSQLContainer<?> postgres;

    @Override
    public Map<String, String> start() {
        elasticMq = new GenericContainer<>(ELASTICMQ)
                .withExposedPorts(SQS_PORT)
                .withCopyFileToContainer(
                        MountableFile.forClasspathResource("elasticmq.conf"), "/opt/elasticmq.conf")
                .waitingFor(Wait.forListeningPort());
        elasticMq.start();

        postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"))
                .withDatabaseName("billing_db")
                .withUsername("postgres")
                .withPassword("postgres");
        postgres.start();

        String endpoint = "http://" + elasticMq.getHost() + ":" + elasticMq.getMappedPort(SQS_PORT);

        Map<String, String> config = new HashMap<>();
        config.put("quarkus.datasource.jdbc.url", postgres.getJdbcUrl());
        config.put("quarkus.datasource.username", postgres.getUsername());
        config.put("quarkus.datasource.password", postgres.getPassword());
        config.put("quarkus.sqs.endpoint-override", endpoint);
        config.put("billing.commands.queue-url", queueUrl(endpoint, COMMANDS_QUEUE));
        return config;
    }

    @Override
    public void stop() {
        if (postgres != null) {
            postgres.stop();
        }
        if (elasticMq != null) {
            elasticMq.stop();
        }
    }

    // A URL vem do proprio ElasticMQ em vez de montada a mao: o formato dela nao e contrato publico.
    private static String queueUrl(String endpoint, String queueName) {
        try (SqsClient sqs = SqsClient.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("elasticmq", "elasticmq")))
                .build()) {
            return sqs.getQueueUrl(GetQueueUrlRequest.builder().queueName(queueName).build()).queueUrl();
        }
    }
}
