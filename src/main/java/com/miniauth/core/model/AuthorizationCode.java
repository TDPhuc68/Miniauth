package com.miniauth.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthorizationCode {
    private String code;
    private String clientId;
    private String userId;
    private String username;
    private String redirectUri;
    private String scopes;
    private String codeChallenge;
    private String codeChallengeMethod; // S256 or plain
    private Instant expiresAt;

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
