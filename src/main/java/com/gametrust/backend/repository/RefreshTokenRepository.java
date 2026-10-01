package com.gametrust.backend.repository;

import com.gametrust.backend.entity.RefreshToken;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends MongoRepository<RefreshToken, String> {

    Optional<RefreshToken> findByToken(String token);

    List<RefreshToken> findAllByUserId(String userId);

    void deleteAllByUserId(String userId);

    void deleteAllByExpiresAtBefore(Instant now);
}
