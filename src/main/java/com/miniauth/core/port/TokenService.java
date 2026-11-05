package com.miniauth.core.port;

import com.miniauth.core.model.ClientApplication;
import com.miniauth.core.model.User;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Port đại diện cho các hành vi liên quan đến Token & Cryptography:
 * Ký Access Token, ID Token (OIDC), xác minh Token và xuất bản Public JWKS.
 */
public interface TokenService {

    /**
     * Sinh JWT Access Token chuẩn OAuth 2.0 (ký bằng RSA RS256)
     */
    String generateAccessToken(User user, ClientApplication client, String scope);

    /**
     * Sinh OIDC ID Token chứa profile của user
     */
    String generateIdToken(User user, ClientApplication client, String nonce);

    /**
     * Xác thực tính toàn vẹn và hợp lệ (thời hạn, chữ ký) của Access Token
     * Trả về Claims nếu hợp lệ
     */
    Mono<Map<String, Object>> validateAndExtractClaims(String token);

    /**
     * Lấy JSON Web Key Set (Public Keys) để publish ra endpoint /.well-known/jwks.json
     */
    Map<String, Object> getPublicJwkSet();
}
