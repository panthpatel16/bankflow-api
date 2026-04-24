package com.bankflow.service;

import com.bankflow.dto.request.TransactionRequest;
import com.bankflow.dto.response.TransactionResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface TransactionService {
    TransactionResponse initiateTransaction(TransactionRequest request, String initiatedBy);
    TransactionResponse getTransactionById(UUID id);
    TransactionResponse getTransactionByReference(String referenceId);
    Page<TransactionResponse> getTransactionsByAccount(String accountId, Pageable pageable);
    TransactionResponse reverseTransaction(UUID id, String reversedBy);
}
