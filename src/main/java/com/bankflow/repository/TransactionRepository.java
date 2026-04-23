package com.bankflow.repository;

import com.bankflow.entity.Transaction;
import com.bankflow.enums.TransactionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Optional<Transaction> findByReferenceId(String referenceId);

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    Page<Transaction> findBySourceAccountIdOrderByCreatedAtDesc(String accountId, Pageable pageable);

    Page<Transaction> findByStatusOrderByCreatedAtDesc(TransactionStatus status, Pageable pageable);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
           "WHERE t.sourceAccountId = :accountId " +
           "AND t.status = 'COMPLETED' " +
           "AND t.createdAt >= :startOfDay")
    BigDecimal sumDailyTransactions(@Param("accountId") String accountId,
                                    @Param("startOfDay") LocalDateTime startOfDay);

    @Query("SELECT t FROM Transaction t WHERE t.sourceAccountId = :accountId " +
           "AND t.createdAt BETWEEN :start AND :end ORDER BY t.createdAt DESC")
    Page<Transaction> findByAccountAndDateRange(@Param("accountId") String accountId,
                                                @Param("start") LocalDateTime start,
                                                @Param("end") LocalDateTime end,
                                                Pageable pageable);

    boolean existsByIdempotencyKey(String idempotencyKey);
}
