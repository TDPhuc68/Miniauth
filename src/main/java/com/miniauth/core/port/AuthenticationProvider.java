package com.miniauth.core.port;

import com.miniauth.core.model.AuthenticationRequest;
import com.miniauth.core.model.User;
import reactor.core.publisher.Mono;

/**
 * Strategy Interface đại diện cho một Identity Provider (IDP).
 * Cho phép cắm thêm nhiều nguồn định danh (Local DB, Google, LDAP...)
 * mà không sửa đổi mã nguồn cốt lõi (Open-Closed Principle).
 */
public interface AuthenticationProvider {

    /**
     * Tên loại định danh: "LOCAL", "GOOGLE", "MOCK_LDAP", v.v.
     */
    String getProviderType();

    /**
     * Xác thực thông tin người dùng một cách phi phong tỏa (Reactive)
     */
    Mono<User> authenticate(AuthenticationRequest request);
}
