package com.bankflow.entity;

import com.bankflow.enums.TransactionStatus;
import com.bankflow.enums.TransactionType;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "transactions", indexes = {
    @Index(name = "idx_transaction_reference", columnList = "reference_id", unique = true),
    @Index(name = "idx_transaction_account", columnList = "source_account_id"),
    @Index(name = "idx_transaction_status", columnList = "status"),
    @Index(name = "idx_transaction_created", columnList = "created_at")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "reference_id", unique = true, nullable = false, length = 64)
    private String referenceId;

    @Column(name = "source_account_id", nullable = false, length = 64)
    private String sourceAccountId;

    @Column(name = "destination_account_id", nullable = false, length = 64)
    private String destinationAccountId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private TransactionStatus status;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "failure_reason", length = 512)
    private String failureReason;

    @Column(name = "idempotency_key", unique = true, length = 128)
    private String idempotencyKey;

    @Column(name = "initiated_by", nullable = false, length = 128)
    private String initiatedBy;

    @Column(name = "processing_fee", precision = 10, scale = 4)
    private BigDecimal processingFee;

    @Version
    @Column(name = "version")
    private Long version;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private Account account;
}
