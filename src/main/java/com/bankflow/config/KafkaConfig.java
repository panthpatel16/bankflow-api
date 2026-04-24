package com.bankflow.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.support.converter.RecordMessageConverter;
import org.springframework.kafka.support.converter.StringJsonMessageConverter;

@Configuration
public class KafkaConfig {

    @Value("${bankflow.kafka.topics.transaction-initiated}")
    private String transactionInitiatedTopic;

    @Value("${bankflow.kafka.topics.transaction-completed}")
    private String transactionCompletedTopic;

    @Value("${bankflow.kafka.topics.transaction-failed}")
    private String transactionFailedTopic;

    @Value("${bankflow.kafka.topics.audit-log}")
    private String auditLogTopic;

    @Bean
    public NewTopic transactionInitiatedTopic() {
        return TopicBuilder.name(transactionInitiatedTopic)
                .partitions(6)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic transactionCompletedTopic() {
        return TopicBuilder.name(transactionCompletedTopic)
                .partitions(6)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic transactionFailedTopic() {
        return TopicBuilder.name(transactionFailedTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic auditLogTopic() {
        return TopicBuilder.name(auditLogTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public RecordMessageConverter converter() {
        return new StringJsonMessageConverter();
    }
}
