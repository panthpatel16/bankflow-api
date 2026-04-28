package com.bankflow;

import com.bankflow.dto.request.TransactionRequest;
import com.bankflow.dto.response.TransactionResponse;
import com.bankflow.entity.Account;
import com.bankflow.entity.Transaction;
import com.bankflow.enums.AccountStatus;
import com.bankflow.enums.AccountType;
import com.bankflow.enums.TransactionStatus;
import com.bankflow.enums.TransactionType;
import com.bankflow.exception.BankFlowException;
import com.bankflow.repository.AccountRepository;
import com.bankflow.repository.TransactionRepository;
import com.bankflow.service.TransactionEventProducer;
import com.bankflow.service.impl.TransactionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionService Unit Tests")
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private TransactionEventProducer eventProducer;

    @InjectMocks
    private TransactionServiceImpl transactionService;

    private Account sourceAccount;
    private Account destinationAccount;
    private TransactionRequest request;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(transactionService, "dailyLimit", new BigDecimal("100000.00"));

        sourceAccount = Account.builder()
                .id(UUID.randomUUID())
                .accountNumber("ACC-0000000001")
                .balance(new BigDecimal("50000.00"))
                .currency("USD")
                .status(AccountStatus.ACTIVE)
                .type(AccountType.CHECKING)
                .version(0L)
                .build();

        destinationAccount = Account.builder()
                .id(UUID.randomUUID())
                .accountNumber("ACC-0000000002")
                .balance(new BigDecimal("10000.00"))
                .currency("USD")
                .status(AccountStatus.ACTIVE)
                .type(AccountType.SAVINGS)
                .version(0L)
                .build();

        request = new TransactionRequest();
        request.setSourceAccountId("ACC-0000000001");
        request.setDestinationAccountId("ACC-0000000002");
        request.setAmount(new BigDecimal("1000.00"));
        request.setCurrency("USD");
        request.setType(TransactionType.TRANSFER);
        request.setDescription("Test transfer");
        request.setIdempotencyKey("test-key-001");
    }

    @Test
    @DisplayName("Should complete transaction successfully")
    void initiateTransaction_Success() {
        when(transactionRepository.existsByIdempotencyKey(anyString())).thenReturn(false);
        when(accountRepository.findByAccountNumberWithLock("ACC-0000000001"))
                .thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findByAccountNumberWithLock("ACC-0000000002"))
                .thenReturn(Optional.of(destinationAccount));
        when(transactionRepository.sumDailyTransactions(anyString(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(inv -> {
                    Transaction t = inv.getArgument(0);
                    if (t.getId() == null) {
                        return Transaction.builder()
                                .id(UUID.randomUUID())
                                .referenceId("BF123")
                                .sourceAccountId(t.getSourceAccountId())
                                .destinationAccountId(t.getDestinationAccountId())
                                .amount(t.getAmount())
                                .currency(t.getCurrency())
                                .type(t.getType())
                                .status(TransactionStatus.COMPLETED)
                                .initiatedBy(t.getInitiatedBy())
                                .processingFee(t.getProcessingFee())
                                .idempotencyKey(t.getIdempotencyKey())
                                .build();
                    }
                    return t;
                });

        TransactionResponse response = transactionService.initiateTransaction(request, "john.doe");

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        verify(eventProducer, times(1)).publishTransactionInitiated(any());
        verify(eventProducer, times(1)).publishTransactionCompleted(any());
    }

    @Test
    @DisplayName("Should return existing transaction on duplicate idempotency key")
    void initiateTransaction_IdempotencyKeyExists_ReturnsExisting() {
        Transaction existing = Transaction.builder()
                .id(UUID.randomUUID())
                .referenceId("BF-EXISTING")
                .sourceAccountId("ACC-0000000001")
                .destinationAccountId("ACC-0000000002")
                .amount(new BigDecimal("1000.00"))
                .currency("USD")
                .type(TransactionType.TRANSFER)
                .status(TransactionStatus.COMPLETED)
                .initiatedBy("john.doe")
                .processingFee(new BigDecimal("2.50"))
                .build();

        when(transactionRepository.existsByIdempotencyKey("test-key-001")).thenReturn(true);
        when(transactionRepository.findByIdempotencyKey("test-key-001"))
                .thenReturn(Optional.of(existing));

        TransactionResponse response = transactionService.initiateTransaction(request, "john.doe");

        assertThat(response.getReferenceId()).isEqualTo("BF-EXISTING");
        verify(accountRepository, never()).findByAccountNumberWithLock(anyString());
    }

    @Test
    @DisplayName("Should throw exception when source account has insufficient balance")
    void initiateTransaction_InsufficientBalance_ThrowsException() {
        sourceAccount.setBalance(new BigDecimal("500.00"));

        when(transactionRepository.existsByIdempotencyKey(anyString())).thenReturn(false);
        when(accountRepository.findByAccountNumberWithLock("ACC-0000000001"))
                .thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findByAccountNumberWithLock("ACC-0000000002"))
                .thenReturn(Optional.of(destinationAccount));
        when(transactionRepository.sumDailyTransactions(anyString(), any()))
                .thenReturn(BigDecimal.ZERO);

        assertThatThrownBy(() -> transactionService.initiateTransaction(request, "john.doe"))
                .isInstanceOf(BankFlowException.class)
                .hasMessageContaining("Insufficient balance");
    }

    @Test
    @DisplayName("Should throw exception when account is not active")
    void initiateTransaction_AccountSuspended_ThrowsException() {
        sourceAccount.setStatus(AccountStatus.SUSPENDED);

        when(transactionRepository.existsByIdempotencyKey(anyString())).thenReturn(false);
        when(accountRepository.findByAccountNumberWithLock("ACC-0000000001"))
                .thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findByAccountNumberWithLock("ACC-0000000002"))
                .thenReturn(Optional.of(destinationAccount));
        when(transactionRepository.sumDailyTransactions(anyString(), any()))
                .thenReturn(BigDecimal.ZERO);

        assertThatThrownBy(() -> transactionService.initiateTransaction(request, "john.doe"))
                .isInstanceOf(BankFlowException.class)
                .hasMessageContaining("not active");
    }

    @Test
    @DisplayName("Should throw exception when daily limit is exceeded")
    void initiateTransaction_DailyLimitExceeded_ThrowsException() {
        when(transactionRepository.existsByIdempotencyKey(anyString())).thenReturn(false);
        when(accountRepository.findByAccountNumberWithLock("ACC-0000000001"))
                .thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findByAccountNumberWithLock("ACC-0000000002"))
                .thenReturn(Optional.of(destinationAccount));
        when(transactionRepository.sumDailyTransactions(anyString(), any()))
                .thenReturn(new BigDecimal("99500.00"));

        assertThatThrownBy(() -> transactionService.initiateTransaction(request, "john.doe"))
                .isInstanceOf(BankFlowException.class)
                .hasMessageContaining("daily limit");
    }

    @Test
    @DisplayName("Should throw exception when source account not found")
    void initiateTransaction_SourceAccountNotFound_ThrowsException() {
        when(transactionRepository.existsByIdempotencyKey(anyString())).thenReturn(false);
        when(accountRepository.findByAccountNumberWithLock("ACC-0000000001"))
                .thenReturn(Optional.empty());
        when(accountRepository.findByAccountNumberWithLock("ACC-0000000002"))
                .thenReturn(Optional.of(destinationAccount));
        when(transactionRepository.sumDailyTransactions(anyString(), any()))
                .thenReturn(BigDecimal.ZERO);

        assertThatThrownBy(() -> transactionService.initiateTransaction(request, "john.doe"))
                .isInstanceOf(BankFlowException.class)
                .hasMessageContaining("Source account not found");
    }
}