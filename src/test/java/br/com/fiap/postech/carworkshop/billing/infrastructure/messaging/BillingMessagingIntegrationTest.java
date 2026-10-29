package br.com.fiap.postech.carworkshop.billing.infrastructure.messaging;

import br.com.fiap.postech.carworkshop.billing.domain.message.BillingMessageType;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.PurgeQueueRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(BillingMessagingTestProfile.class)
@QuarkusTestResource(value = BillingMessagingResource.class, restrictToAnnotatedClass = true)
class BillingMessagingIntegrationTest {

    private static final String TRACE_ID = "EVIDENCIA-B5-001";
    private static final UUID SAGA_ID = UUID.fromString("6f1d0000-0000-4000-8000-000000000005");

    @Inject
    SqsClient sqs;

    @Inject
    EntityManager entityManager;

    @Inject
    DataSource dataSource;

    @Inject
    OutboxRelayService relay;

    @Inject
    BillingCommandsConsumer consumer;

    @ConfigProperty(name = "billing.commands.queue-url")
    String commandsQueueUrl;

    @BeforeEach
    void emptyTheQueue() {
        sqs.purgeQueue(PurgeQueueRequest.builder().queueUrl(commandsQueueUrl).build());
    }

    // O indice parcial da V2 e justamente o que o H2 nao suporta: ve-lo aqui prova que a migracao rodou num
    // PostgreSQL de verdade, e nao no esquema que o Hibernate cria no perfil de teste.
    @Test
    void flywayAppliedBothMigrationsWithThePartialIndex() {
        assertEquals("1.0.0,2.0.0", query(
                "select string_agg(version, ',' order by installed_rank) from flyway_schema_history"
                        + " where success"));
        assertTrue(query("select indexdef from pg_indexes where indexname = 'saga_outbox_pending_idx'")
                .contains("sent_at IS NULL"));
    }

    @Test
    void createBudgetCommand_writesTheBudgetAndTheReplyInTheSameTransaction() {
        long workOrderId = 5201L;
        UUID messageId = UUID.randomUUID();

        send(createBudget(messageId, workOrderId), workOrderId);
        consumer.poll();

        assertEquals("PENDING_APPROVAL", statusOf(workOrderId));
        assertEquals(1L, outboxRowsFor(workOrderId));
        assertEquals(BillingMessageType.BUDGET_CREATED, replyTypeFor(workOrderId));
        assertNull(sentAtFor(workOrderId));
        assertEquals(0, messagesLeftOnTheQueue());
    }

    // Reentrega com o MESMO messageId: a inbox e quem decide, e o efeito tem de acontecer uma vez so — nem
    // segundo orcamento, nem segunda resposta para o orquestrador.
    @Test
    void theSameCommandTwice_advancesOnceAndAnswersOnce() {
        long workOrderId = 5202L;
        UUID messageId = UUID.randomUUID();
        String body = createBudget(messageId, workOrderId);

        send(body, workOrderId);
        consumer.poll();
        send(body, workOrderId);
        consumer.poll();

        assertEquals(1L, budgetRowsFor(workOrderId));
        assertEquals(1L, outboxRowsFor(workOrderId));
        assertEquals(1L, inboxRowsFor(messageId));
        assertEquals(0, messagesLeftOnTheQueue());
    }

    @Test
    void relay_marksTheRowSentAgainstTheRealDatabase() {
        long workOrderId = 5203L;
        send(createBudget(UUID.randomUUID(), workOrderId), workOrderId);
        consumer.poll();

        relay.drain();

        assertNotNull(sentAtFor(workOrderId));
        assertEquals(0L, pendingOutboxRowsFor(workOrderId));
    }

    // O UNIQUE(work_order_id) e a rede de seguranca da idempotencia de CreateBudget para concorrencia: o
    // caminho normal e o reuso no caso de uso, e so o PostgreSQL de verdade tem a constraint.
    @Test
    void theDatabaseRefusesASecondBudgetForTheSameWorkOrder() throws SQLException {
        long workOrderId = 5204L;
        insertBudget(UUID.randomUUID(), workOrderId);

        assertThrows(SQLException.class, () -> insertBudget(UUID.randomUUID(), workOrderId));
    }

    @Test
    void theDatabaseRefusesASecondNotificationWithTheSameTriple() throws SQLException {
        UUID budgetId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        insertBudget(budgetId, 5205L);
        insertPayment(paymentId, budgetId, 5205L);
        insertAttempt(paymentId, "mp-5205", "cc_rejected_insufficient_amount");

        assertThrows(SQLException.class,
                () -> insertAttempt(paymentId, "mp-5205", "cc_rejected_insufficient_amount"));
    }

    private void send(String body, long workOrderId) {
        sqs.sendMessage(SendMessageRequest.builder()
                .queueUrl(commandsQueueUrl)
                .messageBody(body)
                .messageGroupId(String.valueOf(workOrderId))
                .messageDeduplicationId(UUID.randomUUID().toString())
                .build());
    }

    private static String createBudget(UUID messageId, long workOrderId) {
        return """
                {
                  "messageId": "%s",
                  "type": "CreateBudget",
                  "version": 1,
                  "occurredAt": "2026-10-25T12:00:00Z",
                  "sagaId": "%s",
                  "workOrderId": %d,
                  "branchId": "SP-01",
                  "correlationId": "%s",
                  "payload": {
                    "customerDocument": "11144477735",
                    "total": 257.70,
                    "approvalDeadline": "2099-01-01T00:00:00Z",
                    "items": [
                      {"type": "SERVICE", "catalogId": 1, "description": "Troca de oleo",
                       "quantity": 1, "unitPrice": 150.00},
                      {"type": "PART", "catalogId": 7, "description": "Filtro de oleo",
                       "quantity": 3, "unitPrice": 35.90}
                    ]
                  }
                }
                """.formatted(messageId, SAGA_ID, workOrderId, TRACE_ID);
    }

    private int messagesLeftOnTheQueue() {
        Map<QueueAttributeName, String> attributes = sqs.getQueueAttributes(
                GetQueueAttributesRequest.builder()
                        .queueUrl(commandsQueueUrl)
                        .attributeNames(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES,
                                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE)
                        .build()).attributes();
        return Integer.parseInt(attributes.get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES))
                + Integer.parseInt(
                        attributes.get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE));
    }

    // Projecao escalar, nao a entidade: uma leitura por entidade voltaria do cache de primeiro nivel com o
    // valor de antes da transacao do consumidor, e o teste mentiria nas duas direcoes.
    private String statusOf(long workOrderId) {
        return entityManager
                .createQuery("select b.status from BudgetJpaEntity b where b.workOrderId = :id",
                        String.class)
                .setParameter("id", workOrderId)
                .getSingleResult();
    }

    private long budgetRowsFor(long workOrderId) {
        return count("select count(b) from BudgetJpaEntity b where b.workOrderId = :id", workOrderId);
    }

    private long outboxRowsFor(long workOrderId) {
        return count("select count(o) from SagaOutboxJpaEntity o where o.workOrderId = :id",
                workOrderId);
    }

    private long pendingOutboxRowsFor(long workOrderId) {
        return count("select count(o) from SagaOutboxJpaEntity o where o.workOrderId = :id"
                + " and o.sentAt is null", workOrderId);
    }

    private long inboxRowsFor(UUID messageId) {
        return entityManager
                .createQuery("select count(i) from SagaInboxJpaEntity i where i.messageId = :id",
                        Long.class)
                .setParameter("id", messageId)
                .getSingleResult();
    }

    private String replyTypeFor(long workOrderId) {
        return entityManager
                .createQuery("select o.type from SagaOutboxJpaEntity o where o.workOrderId = :id",
                        String.class)
                .setParameter("id", workOrderId)
                .getSingleResult();
    }

    private Instant sentAtFor(long workOrderId) {
        return entityManager
                .createQuery("select o.sentAt from SagaOutboxJpaEntity o where o.workOrderId = :id",
                        Instant.class)
                .setParameter("id", workOrderId)
                .getSingleResult();
    }

    private long count(String jpql, long workOrderId) {
        return entityManager.createQuery(jpql, Long.class)
                .setParameter("id", workOrderId)
                .getSingleResult();
    }

    private String query(String sql) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            return result.next() ? result.getString(1) : null;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private void insertBudget(UUID id, long workOrderId) throws SQLException {
        update("""
                INSERT INTO budgets (id, work_order_id, saga_id, branch_id, customer_document, total,
                                     status, approval_deadline, created_at, updated_at)
                VALUES (?, ?, ?, 'SP-01', '11144477735', 257.70, 'PENDING_APPROVAL', now(), now(), now())
                """, id, workOrderId, SAGA_ID);
    }

    private void insertPayment(UUID id, UUID budgetId, long workOrderId) throws SQLException {
        update("""
                INSERT INTO payments (id, budget_id, work_order_id, amount, status,
                                      refund_idempotency_key, payment_deadline, created_at, updated_at)
                VALUES (?, ?, ?, 257.70, 'PENDING', ?, now(), now(), now())
                """, id, budgetId, workOrderId, UUID.randomUUID());
    }

    private void insertAttempt(UUID paymentId, String mpPaymentId, String statusDetail)
            throws SQLException {
        update("""
                INSERT INTO payment_attempts (payment_id, mp_payment_id, status_detail, occurred_at)
                VALUES (?, ?, ?, now())
                """, paymentId, mpPaymentId, statusDetail);
    }

    private void update(String sql, Object... parameters) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) {
                statement.setObject(index + 1, parameters[index]);
            }
            statement.executeUpdate();
        }
    }
}
