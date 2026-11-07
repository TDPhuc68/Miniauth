package com.miniauth.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Đối tượng đóng gói thông tin đăng nhập từ Client hoặc Web UI.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthenticationRequest {
    private String username;
    private String password;

    @Builder.Default
    private String providerType = "LOCAL";

    @Builder.Default
    private Map<String, Object> additionalParams = new HashMap<>();
}
