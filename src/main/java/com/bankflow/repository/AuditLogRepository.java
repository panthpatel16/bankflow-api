package com.bankflow.repository;

import com.bankflow.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    Page<AuditLog> findByTransactionIdOrderByCreatedAtDesc(String transactionId, Pageable pageable);
    Page<AuditLog> findByActorOrderByCreatedAtDesc(String actor, Pageable pageable);
}
