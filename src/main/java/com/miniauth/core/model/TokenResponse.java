package com.miniauth.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Định dạng phản hồi Token theo chuẩn OAuth 2.0 (RFC 6749 Section 5.1)
 * và OpenID Connect Core 1.0.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TokenResponse {

    @JsonProperty("access_token")
    private String accessToken;

    @Builder.Default
    @JsonProperty("token_type")
    private String tokenType = "Bearer";

    @JsonProperty("expires_in")
    private Long expiresIn;

    @JsonProperty("id_token")
    private String idToken;

    @JsonProperty("scope")
    private String scope;
}
