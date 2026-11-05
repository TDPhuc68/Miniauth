package com.miniauth.infrastructure.persistence.r2dbc;

import com.miniauth.core.model.AuditLog;
import org.springframework.data.r2dbc.repository.R2dbcRepository;

/**
 * Spring Data R2DBC Repository nội bộ cho bảng audit_logs.
 */
public interface SpringDataAuditRepository extends R2dbcRepository<AuditLog, String> {
}
