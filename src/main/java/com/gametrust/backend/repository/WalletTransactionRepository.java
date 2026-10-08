package com.gametrust.backend.repository;

import com.gametrust.backend.entity.WalletTransaction;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WalletTransactionRepository extends MongoRepository<WalletTransaction, String> {

    Optional<WalletTransaction> findByOrderCode(long orderCode);

    List<WalletTransaction> findByUserIdOrderByCreatedAtDesc(String userId);

    List<WalletTransaction> findTop10ByOrderByCreatedAtDesc();

    long countByUserIdAndStatusAndCreatedAtAfter(String userId, String status, java.time.Instant after);

    // For manual reconciliation: find by the GT description code (e.g. "GT891725")
    Optional<WalletTransaction> findByDescription(String description);

    // Find pending orders for a specific user (for manual reconciliation flow)
    List<WalletTransaction> findByUserIdAndStatusOrderByCreatedAtDesc(String userId, String status);

    // Check if a bank reference was already processed (idempotency guard)
    boolean existsByReference(String reference);
}
