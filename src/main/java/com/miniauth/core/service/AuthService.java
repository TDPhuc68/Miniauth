package com.miniauth.core.service;

import com.miniauth.core.model.AuthenticationRequest;
import com.miniauth.core.model.User;
import com.miniauth.core.port.AuthenticationProvider;
import com.miniauth.infrastructure.idp.AuthenticationProviderRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Service nghiệp vụ xử lý xác thực danh tính người dùng.
 * Phối hợp với AuthenticationProviderRegistry để ủy quyền cho đúng Strategy IDP.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationProviderRegistry providerRegistry;

    /**
     * Xác thực thông tin người dùng qua Identity Provider tương ứng
     */
    public Mono<User> authenticate(AuthenticationRequest request) {
        String providerType = request.getProviderType() != null ? request.getProviderType() : "LOCAL";
        AuthenticationProvider provider = providerRegistry.getProvider(providerType);
        return provider.authenticate(request);
    }
}
