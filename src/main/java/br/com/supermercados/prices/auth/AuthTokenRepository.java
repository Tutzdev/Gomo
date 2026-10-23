package br.com.supermercados.prices.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AuthTokenRepository extends JpaRepository<AuthToken, UUID> {

    Optional<AuthToken> findByTokenHashAndExpiresAtAfter(String tokenHash, Instant now);

    @Modifying
    @Query("delete from AuthToken token where token.tokenHash = :hash and token.userId = :userId")
    void revoke(@Param("hash") String hash, @Param("userId") UUID userId);

    @Modifying
    @Query("delete from AuthToken token where token.userId = :userId and token.expiresAt <= :now")
    void deleteExpiredForUser(@Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying
    @Query("delete from AuthToken token where token.userId = :userId")
    void revokeAll(@Param("userId") UUID userId);

    /** Read only by the cleanup of unverified accounts (see AccountCleanupService). */
    @Modifying
    @Query(value = "update app_users set last_login_at = :now where id = :userId", nativeQuery = true)
    void recordSignIn(@Param("userId") UUID userId, @Param("now") Instant now);
}
