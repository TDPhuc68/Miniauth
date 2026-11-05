package com.miniauth.core.port;

import com.miniauth.core.model.AuditLog;
import reactor.core.publisher.Mono;

/**
 * Port (Contract) để lưu trữ và truy vấn AuditLog trong Core domain.
 * Không phụ thuộc vào bất kỳ framework ORM cụ thể nào.
 */
public interface AuditRepository {
    Mono<AuditLog> save(AuditLog auditLog);
}
