package com.bankflow.dto.response;

import com.bankflow.enums.TransactionStatus;
import com.bankflow.enums.TransactionType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class TransactionResponse {
    private UUID id;
    private String referenceId;
    private String sourceAccountId;
    private String destinationAccountId;
    private BigDecimal amount;
    private String currency;
    private TransactionType type;
    private TransactionStatus status;
    private String description;
    private BigDecimal processingFee;
    private String initiatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
