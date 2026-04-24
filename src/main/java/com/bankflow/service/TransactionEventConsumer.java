package com.bankflow.service;

import com.bankflow.dto.request.TransactionEvent;
import com.bankflow.entity.AuditLog;
import com.bankflow.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionEventConsumer {

    private final AuditLogRepository auditLogRepository;

    @KafkaListener(topics = "${bankflow.kafka.topics.transaction-initiated}", groupId = "${spring.kafka.consumer.group-id}", containerFactory = "kafkaListenerContainerFactory")
    @Transactional
    public void consumeTransactionInitiated(
            @Payload TransactionEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition) {

        log.info("Received INITIATED event for transaction [{}] from topic [{}] partition [{}]",
                event.getReferenceId(), topic, partition);
        persistAuditLog(event, "TRANSACTION_INITIATED");
    }

    @KafkaListener(topics = "${bankflow.kafka.topics.transaction-completed}", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void consumeTransactionCompleted(@Payload TransactionEvent event) {
        log.info("Received COMPLETED event for transaction [{}]", event.getReferenceId());
        persistAuditLog(event, "TRANSACTION_COMPLETED");
    }

    @KafkaListener(topics = "${bankflow.kafka.topics.transaction-failed}", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void consumeTransactionFailed(@Payload TransactionEvent event) {
        log.warn("Received FAILED event for transaction [{}]: {}",
                event.getReferenceId(), event.getFailureReason());
        persistAuditLog(event, "TRANSACTION_FAILED");
    }

    private void persistAuditLog(TransactionEvent event, String action) {
        AuditLog auditLog = AuditLog.builder()
                .transactionId(event.getReferenceId())
                .action(action)
                .actor(event.getInitiatedBy())
                .status(event.getStatus().name())
                .details(buildDetails(event))
                .build();
        auditLogRepository.save(auditLog);
    }

    private String buildDetails(TransactionEvent event) {
        return String.format(
                "amount=%s %s | source=%s | destination=%s | type=%s",
                event.getAmount(), event.getCurrency(),
                event.getSourceAccountId(), event.getDestinationAccountId(),
                event.getType());
    }
}