package com.miniauth.infrastructure.idp;

import com.miniauth.core.exception.OAuth2Exception;
import com.miniauth.core.port.AuthenticationProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry quản lý tập trung toàn bộ các AuthenticationProvider.
 * Tận dụng Spring Dependency Injection để tự động thu thập các beans
 * triển khai AuthenticationProvider mà không cần cấu hình thủ công.
 */
@Slf4j
@Component
public class AuthenticationProviderRegistry {

    private final Map<String, AuthenticationProvider> providers = new ConcurrentHashMap<>();

    public AuthenticationProviderRegistry(List<AuthenticationProvider> providerList) {
        for (AuthenticationProvider provider : providerList) {
            String key = provider.getProviderType().toUpperCase();
            providers.put(key, provider);
            log.info("Registered Identity Provider: [{}] -> {}", key, provider.getClass().getSimpleName());
        }
    }

    /**
     * Lấy Provider tương ứng theo loại (LOCAL, GOOGLE_MOCK, ...)
     */
    public AuthenticationProvider getProvider(String providerType) {
        if (providerType == null) {
            providerType = "LOCAL";
        }
        AuthenticationProvider provider = providers.get(providerType.toUpperCase());
        if (provider == null) {
            throw OAuth2Exception.invalidRequest("Unsupported identity provider: " + providerType);
        }
        return provider;
    }

    /**
     * Danh sách tất cả các loại IDP đang được hỗ trợ
     */
    public Set<String> getSupportedProviders() {
        return Collections.unmodifiableSet(providers.keySet());
    }
}
