package com.miniauth.infrastructure.persistence.r2dbc;

import com.miniauth.core.model.AuditLog;
import com.miniauth.core.port.AuditRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * Adapter kết nối AuditRepository với Spring Data R2DBC.
 */
@Repository
@RequiredArgsConstructor
public class R2dbcAuditRepositoryAdapter implements AuditRepository {

    private final SpringDataAuditRepository springDataAuditRepository;

    @Override
    public Mono<AuditLog> save(AuditLog auditLog) {
        return springDataAuditRepository.save(auditLog);
    }
}
