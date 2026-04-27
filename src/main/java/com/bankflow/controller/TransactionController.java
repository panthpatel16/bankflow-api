package com.bankflow.controller;

import com.bankflow.dto.request.TransactionRequest;
import com.bankflow.dto.response.ApiResponse;
import com.bankflow.dto.response.TransactionResponse;
import com.bankflow.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
@Tag(name = "Transactions", description = "Financial transaction management")
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping
    @Operation(summary = "Initiate a financial transaction")
    public ResponseEntity<ApiResponse<TransactionResponse>> initiateTransaction(
            @Valid @RequestBody TransactionRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        TransactionResponse response = transactionService.initiateTransaction(
            request, userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(response, "Transaction initiated successfully"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get transaction by ID")
    public ResponseEntity<ApiResponse<TransactionResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(
            ApiResponse.success(transactionService.getTransactionById(id), "Transaction retrieved"));
    }

    @GetMapping("/reference/{referenceId}")
    @Operation(summary = "Get transaction by reference ID")
    public ResponseEntity<ApiResponse<TransactionResponse>> getByReference(
            @PathVariable String referenceId) {
        return ResponseEntity.ok(
            ApiResponse.success(transactionService.getTransactionByReference(referenceId),
                "Transaction retrieved"));
    }

    @GetMapping("/account/{accountId}")
    @Operation(summary = "Get all transactions for an account")
    public ResponseEntity<ApiResponse<Page<TransactionResponse>>> getByAccount(
            @PathVariable String accountId,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(
            ApiResponse.success(transactionService.getTransactionsByAccount(accountId, pageable),
                "Transactions retrieved"));
    }

    @PostMapping("/{id}/reverse")
    @PreAuthorize("hasAnyRole('ADMIN', 'BANKER')")
    @Operation(summary = "Reverse a completed transaction")
    public ResponseEntity<ApiResponse<TransactionResponse>> reverseTransaction(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(
            ApiResponse.success(transactionService.reverseTransaction(id, userDetails.getUsername()),
                "Transaction reversed successfully"));
    }
}
