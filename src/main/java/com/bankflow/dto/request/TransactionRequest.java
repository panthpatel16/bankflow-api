package com.bankflow.dto.request;

import com.bankflow.enums.TransactionType;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class TransactionRequest {

    @NotBlank(message = "Source account is required")
    private String sourceAccountId;

    @NotBlank(message = "Destination account is required")
    private String destinationAccountId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @DecimalMax(value = "25000.00", message = "Amount exceeds single transaction limit")
    @Digits(integer = 15, fraction = 4)
    private BigDecimal amount;

    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be 3 characters (ISO 4217)")
    private String currency;

    @NotNull(message = "Transaction type is required")
    private TransactionType type;

    @Size(max = 255)
    private String description;

    @NotBlank(message = "Idempotency key is required")
    @Size(max = 128)
    private String idempotencyKey;
}
