package com.miniauth.infrastructure.crypto;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;

/**
 * Quản lý cặp khóa bất đối xứng RSA (RS256).
 * Tạo cặp khóa 2048-bit lúc khởi động hoặc nạp từ cấu hình,
 * cung cấp JWSSigner để ký token và JWKSet công khai để client tự xác minh.
 */
@Slf4j
@Component
public class KeyManager {

    @Getter
    private String keyId;
    private RSAKey rsaJwk;

    @Getter
    private JWSSigner signer;

    @PostConstruct
    public void init() {
        try {
            this.keyId = "miniauth-key-" + UUID.randomUUID().toString().substring(0, 8);
            
            // Sinh cặp khóa RSA 2048-bit
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            KeyPair keyPair = keyPairGenerator.generateKeyPair();

            RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
            RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();

            // Đóng gói thành định dạng chuẩn Nimbus RSAKey (RFC 7517)
            this.rsaJwk = new RSAKey.Builder(publicKey)
                    .privateKey(privateKey)
                    .keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.RS256)
                    .keyID(this.keyId)
                    .build();

            // Tạo Signer dùng Private Key để ký
            this.signer = new RSASSASigner(rsaJwk);

            log.info("Initialized RSA 2048-bit KeyPair successfully with Key ID: {}", keyId);
        } catch (NoSuchAlgorithmException | com.nimbusds.jose.JOSEException e) {
            throw new IllegalStateException("Failed to generate RSA KeyPair and initialize Signer", e);
        }
    }

    /**
     * Lấy tập khóa công khai JWKS (chỉ bao gồm Public Key, KHÔNG chứa Private Key)
     * để phục vụ cho endpoint /.well-known/jwks.json
     */
    public JWKSet getPublicJwkSet() {
        return new JWKSet(rsaJwk.toPublicJWK());
    }

    /**
     * Tạo Verifier từ Public Key để xác thực chữ ký token nhận vào
     */
    public RSASSAVerifier createVerifier() {
        try {
            return new RSASSAVerifier(rsaJwk.toRSAPublicKey());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create RSA Verifier", e);
        }
    }
}
