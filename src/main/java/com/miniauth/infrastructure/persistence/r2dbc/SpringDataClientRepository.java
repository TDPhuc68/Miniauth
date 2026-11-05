package com.miniauth.infrastructure.persistence.r2dbc;

import com.miniauth.core.model.ClientApplication;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Mono;

/**
 * Spring Data R2DBC Repository nội bộ cho bảng client_applications.
 */
public interface SpringDataClientRepository extends R2dbcRepository<ClientApplication, String> {
    Mono<ClientApplication> findByClientId(String clientId);
}
