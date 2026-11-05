package com.miniauth.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("client_applications")
public class ClientApplication {
    @Id
    private String id;
    private String clientId;
    private String clientSecretHash;
    private String clientName;
    private String redirectUris; // comma-separated
    private String authorizedGrantTypes; // comma-separated
    private String scopes; // comma-separated
    private LocalDateTime createdAt;

    public List<String> getRedirectUriList() {
        if (redirectUris == null) return List.of();
        return Arrays.stream(redirectUris.split(","))
                .map(String::trim)
                .toList();
    }

    public boolean isRedirectUriAllowed(String targetUri) {
        return getRedirectUriList().contains(targetUri);
    }
}
