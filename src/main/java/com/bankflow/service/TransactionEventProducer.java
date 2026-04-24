package com.bankflow.service;

import com.bankflow.dto.request.TransactionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionEventProducer {

    private final KafkaTemplate<String, TransactionEvent> kafkaTemplate;

    @Value("${bankflow.kafka.topics.transaction-initiated}")
    private String transactionInitiatedTopic;

    @Value("${bankflow.kafka.topics.transaction-completed}")
    private String transactionCompletedTopic;

    @Value("${bankflow.kafka.topics.transaction-failed}")
    private String transactionFailedTopic;

    public void publishTransactionInitiated(TransactionEvent event) {
        publishEvent(transactionInitiatedTopic, event);
    }

    public void publishTransactionCompleted(TransactionEvent event) {
        publishEvent(transactionCompletedTopic, event);
    }

    public void publishTransactionFailed(TransactionEvent event) {
        publishEvent(transactionFailedTopic, event);
    }

    private void publishEvent(String topic, TransactionEvent event) {
        CompletableFuture<SendResult<String, TransactionEvent>> future =
            kafkaTemplate.send(topic, event.getReferenceId(), event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish event to topic [{}] for transaction [{}]: {}",
                    topic, event.getReferenceId(), ex.getMessage());
            } else {
                log.info("Published event to topic [{}] partition [{}] for transaction [{}]",
                    topic,
                    result.getRecordMetadata().partition(),
                    event.getReferenceId());
            }
        });
    }
}
