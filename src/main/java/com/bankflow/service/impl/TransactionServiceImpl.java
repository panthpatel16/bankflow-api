package com.bankflow.service.impl;

import com.bankflow.dto.request.TransactionEvent;
import com.bankflow.dto.request.TransactionRequest;
import com.bankflow.dto.response.TransactionResponse;
import com.bankflow.entity.Account;
import com.bankflow.entity.Transaction;
import com.bankflow.enums.AccountStatus;
import com.bankflow.enums.TransactionStatus;
import com.bankflow.enums.TransactionType;
import com.bankflow.exception.BankFlowException;
import com.bankflow.repository.AccountRepository;
import com.bankflow.repository.TransactionRepository;
import com.bankflow.service.TransactionEventProducer;
import com.bankflow.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final TransactionEventProducer eventProducer;

    @Value("${bankflow.transaction.daily-limit}")
    private BigDecimal dailyLimit;

    private static final BigDecimal FEE_RATE = new BigDecimal("0.0025");

    @Override
    @Transactional
    public TransactionResponse initiateTransaction(TransactionRequest request, String initiatedBy) {

        // Idempotency check
        if (transactionRepository.existsByIdempotencyKey(request.getIdempotencyKey())) {
            log.warn("Duplicate transaction attempt with idempotency key: {}", request.getIdempotencyKey());
            Transaction existing = transactionRepository
                .findByIdempotencyKey(request.getIdempotencyKey())
                .orElseThrow(() -> new BankFlowException("Transaction not found", HttpStatus.NOT_FOUND));
            return mapToResponse(existing);
        }

        // Load accounts with pessimistic lock to prevent race conditions
        Account source = accountRepository
            .findByAccountNumberWithLock(request.getSourceAccountId())
            .orElseThrow(() -> new BankFlowException(
                "Source account not found: " + request.getSourceAccountId(), HttpStatus.NOT_FOUND));

        Account destination = accountRepository
            .findByAccountNumberWithLock(request.getDestinationAccountId())
            .orElseThrow(() -> new BankFlowException(
                "Destination account not found: " + request.getDestinationAccountId(), HttpStatus.NOT_FOUND));

        // Validate accounts
        validateAccount(source, "Source");
        validateAccount(destination, "Destination");
        validateSufficientBalance(source, request.getAmount());
        validateDailyLimit(request.getSourceAccountId(), request.getAmount());

        // Calculate processing fee
        BigDecimal fee = request.getAmount().multiply(FEE_RATE).setScale(4, RoundingMode.HALF_UP);

        // Build transaction
        Transaction transaction = Transaction.builder()
            .referenceId(generateReferenceId())
            .sourceAccountId(request.getSourceAccountId())
            .destinationAccountId(request.getDestinationAccountId())
            .amount(request.getAmount())
            .currency(request.getCurrency())
            .type(request.getType())
            .status(TransactionStatus.PROCESSING)
            .description(request.getDescription())
            .idempotencyKey(request.getIdempotencyKey())
            .initiatedBy(initiatedBy)
            .processingFee(fee)
            .build();

        Transaction saved = transactionRepository.save(transaction);

        // Publish initiated event
        eventProducer.publishTransactionInitiated(buildEvent(saved));

        try {
            // Debit source
            source.setBalance(source.getBalance().subtract(request.getAmount()).subtract(fee));
            // Credit destination
            destination.setBalance(destination.getBalance().add(request.getAmount()));

            accountRepository.save(source);
            accountRepository.save(destination);

            saved.setStatus(TransactionStatus.COMPLETED);
            saved.setCompletedAt(LocalDateTime.now());
            transactionRepository.save(saved);

            eventProducer.publishTransactionCompleted(buildEvent(saved));
            log.info("Transaction [{}] completed successfully for amount {} {}",
                saved.getReferenceId(), saved.getAmount(), saved.getCurrency());

        } catch (Exception e) {
            saved.setStatus(TransactionStatus.FAILED);
            saved.setFailureReason(e.getMessage());
            transactionRepository.save(saved);
            eventProducer.publishTransactionFailed(buildEvent(saved));
            log.error("Transaction [{}] failed: {}", saved.getReferenceId(), e.getMessage());
            throw new BankFlowException("Transaction processing failed: " + e.getMessage(),
                HttpStatus.INTERNAL_SERVER_ERROR);
        }

        return mapToResponse(saved);
    }

    @Override
    @Cacheable(value = "transactions", key = "#id")
    @Transactional(readOnly = true)
    public TransactionResponse getTransactionById(UUID id) {
        return transactionRepository.findById(id)
            .map(this::mapToResponse)
            .orElseThrow(() -> new BankFlowException("Transaction not found: " + id, HttpStatus.NOT_FOUND));
    }

    @Override
    @Transactional(readOnly = true)
    public TransactionResponse getTransactionByReference(String referenceId) {
        return transactionRepository.findByReferenceId(referenceId)
            .map(this::mapToResponse)
            .orElseThrow(() -> new BankFlowException(
                "Transaction not found: " + referenceId, HttpStatus.NOT_FOUND));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TransactionResponse> getTransactionsByAccount(String accountId, Pageable pageable) {
        return transactionRepository
            .findBySourceAccountIdOrderByCreatedAtDesc(accountId, pageable)
            .map(this::mapToResponse);
    }

    @Override
    @Transactional
    public TransactionResponse reverseTransaction(UUID id, String reversedBy) {
        Transaction original = transactionRepository.findById(id)
            .orElseThrow(() -> new BankFlowException("Transaction not found: " + id, HttpStatus.NOT_FOUND));

        if (original.getStatus() != TransactionStatus.COMPLETED) {
            throw new BankFlowException(
                "Only completed transactions can be reversed", HttpStatus.BAD_REQUEST);
        }

        Account source = accountRepository
            .findByAccountNumberWithLock(original.getSourceAccountId())
            .orElseThrow(() -> new BankFlowException("Account not found", HttpStatus.NOT_FOUND));
        Account destination = accountRepository
            .findByAccountNumberWithLock(original.getDestinationAccountId())
            .orElseThrow(() -> new BankFlowException("Account not found", HttpStatus.NOT_FOUND));

        source.setBalance(source.getBalance().add(original.getAmount()).add(original.getProcessingFee()));
        destination.setBalance(destination.getBalance().subtract(original.getAmount()));
        accountRepository.save(source);
        accountRepository.save(destination);

        Transaction reversal = Transaction.builder()
            .referenceId(generateReferenceId())
            .sourceAccountId(original.getDestinationAccountId())
            .destinationAccountId(original.getSourceAccountId())
            .amount(original.getAmount())
            .currency(original.getCurrency())
            .type(TransactionType.REVERSAL)
            .status(TransactionStatus.COMPLETED)
            .description("Reversal of " + original.getReferenceId())
            .idempotencyKey("REV-" + original.getIdempotencyKey())
            .initiatedBy(reversedBy)
            .processingFee(BigDecimal.ZERO)
            .completedAt(LocalDateTime.now())
            .build();

        original.setStatus(TransactionStatus.REVERSED);
        transactionRepository.save(original);

        Transaction savedReversal = transactionRepository.save(reversal);
        eventProducer.publishTransactionCompleted(buildEvent(savedReversal));

        log.info("Transaction [{}] reversed by [{}]", original.getReferenceId(), reversedBy);
        return mapToResponse(savedReversal);
    }

    private void validateAccount(Account account, String label) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new BankFlowException(
                label + " account is not active: " + account.getAccountNumber(),
                HttpStatus.BAD_REQUEST);
        }
    }

    private void validateSufficientBalance(Account account, BigDecimal amount) {
        if (account.getBalance().compareTo(amount) < 0) {
            throw new BankFlowException("Insufficient balance in account: " +
                account.getAccountNumber(), HttpStatus.BAD_REQUEST);
        }
    }

    private void validateDailyLimit(String accountId, BigDecimal amount) {
        BigDecimal todayTotal = transactionRepository.sumDailyTransactions(
            accountId, LocalDateTime.now().toLocalDate().atStartOfDay());
        if (todayTotal.add(amount).compareTo(dailyLimit) > 0) {
            throw new BankFlowException(
                "Transaction would exceed daily limit of " + dailyLimit, HttpStatus.BAD_REQUEST);
        }
    }

    private String generateReferenceId() {
        return "BF" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private TransactionEvent buildEvent(Transaction t) {
        return TransactionEvent.builder()
            .transactionId(t.getId().toString())
            .referenceId(t.getReferenceId())
            .sourceAccountId(t.getSourceAccountId())
            .destinationAccountId(t.getDestinationAccountId())
            .amount(t.getAmount())
            .currency(t.getCurrency())
            .type(t.getType())
            .status(t.getStatus())
            .initiatedBy(t.getInitiatedBy())
            .failureReason(t.getFailureReason())
            .timestamp(LocalDateTime.now())
            .build();
    }

    private TransactionResponse mapToResponse(Transaction t) {
        return TransactionResponse.builder()
            .id(t.getId())
            .referenceId(t.getReferenceId())
            .sourceAccountId(t.getSourceAccountId())
            .destinationAccountId(t.getDestinationAccountId())
            .amount(t.getAmount())
            .currency(t.getCurrency())
            .type(t.getType())
            .status(t.getStatus())
            .description(t.getDescription())
            .processingFee(t.getProcessingFee())
            .initiatedBy(t.getInitiatedBy())
            .createdAt(t.getCreatedAt())
            .completedAt(t.getCompletedAt())
            .build();
    }
}
