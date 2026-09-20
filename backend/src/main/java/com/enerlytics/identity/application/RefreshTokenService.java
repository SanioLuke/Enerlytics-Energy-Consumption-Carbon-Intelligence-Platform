package com.enerlytics.identity.application;

import com.enerlytics.identity.domain.RefreshTokenEntity;
import com.enerlytics.identity.domain.UserEntity;
import com.enerlytics.identity.infrastructure.persistence.RefreshTokenRepository;
import com.enerlytics.security.jwt.JwtTokenProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final long refreshExpirationMs;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
                               JwtTokenProvider jwtTokenProvider,
                               com.enerlytics.security.jwt.JwtProperties jwtProperties) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshExpirationMs = jwtProperties.refreshExpirationMs();
    }

    @Transactional
    public String createRefreshToken(UserEntity user) {
        String rawToken = jwtTokenProvider.generateRefreshTokenValue();
        String tokenHash = jwtTokenProvider.hashToken(rawToken);
        Instant now = Instant.now();
        RefreshTokenEntity entity = new RefreshTokenEntity(user, tokenHash, now.plusMillis(refreshExpirationMs));
        refreshTokenRepository.save(entity);
        return rawToken;
    }

    @Transactional(readOnly = true)
    public RefreshTokenEntity findValid(String rawToken) {
        RefreshTokenEntity entity = refreshTokenRepository.findByTokenHash(jwtTokenProvider.hashToken(rawToken))
                .orElseThrow(() -> new InvalidRefreshTokenException("Refresh token not recognized"));
        if (!entity.isUsable(Instant.now())) {
            throw new InvalidRefreshTokenException("Refresh token is expired or revoked");
        }
        return entity;
    }

    @Transactional
    public void revoke(String rawToken) {
        RefreshTokenEntity entity = refreshTokenRepository.findByTokenHash(jwtTokenProvider.hashToken(rawToken))
                .orElse(null);
        if (entity != null && !entity.isRevoked()) {
            entity.revoke(Instant.now());
            refreshTokenRepository.save(entity);
        }
    }

    @Transactional
    public void rotate(RefreshTokenEntity oldToken, String newRawToken) {
        String newHash = jwtTokenProvider.hashToken(newRawToken);
        oldToken.revoke(Instant.now());
        oldToken.markReplacedBy(newHash);
        refreshTokenRepository.save(oldToken);
    }

    public static class InvalidRefreshTokenException extends RuntimeException {
        public InvalidRefreshTokenException(String message) {
            super(message);
        }
    }
}
