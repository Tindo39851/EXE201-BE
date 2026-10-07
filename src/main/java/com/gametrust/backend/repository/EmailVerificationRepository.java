package com.gametrust.backend.repository;

import com.gametrust.backend.entity.EmailVerification;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EmailVerificationRepository extends MongoRepository<EmailVerification, String> {

    Optional<EmailVerification> findTopByEmailOrderByCreatedAtDesc(String email);

    void deleteByEmail(String email);
}
