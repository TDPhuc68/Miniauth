package com.miniauth.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("users")
public class User {
    @Id
    private String id;
    private String username;
    private String passwordHash;
    private String fullName;
    private String email;
    @Builder.Default
    private String status = "ACTIVE";
    @Builder.Default
    private Integer failedAttempts = 0;
    private LocalDateTime lockedUntil;
    private LocalDateTime createdAt;

    public boolean isLocked() {
        if ("LOCKED".equalsIgnoreCase(status)) {
            return lockedUntil == null || lockedUntil.isAfter(LocalDateTime.now());
        }
        return false;
    }
}
