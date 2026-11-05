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
@Table("audit_logs")
public class AuditLog {
    @Id
    private String id;
    private String eventType;
    private String actor;
    private String target;
    private String status; // SUCCESS, FAILURE
    private String details;
    private String ipAddress;
    private LocalDateTime createdAt;
}
