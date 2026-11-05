package com.miniauth.infrastructure.persistence.r2dbc;

import com.miniauth.core.model.User;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Mono;

/**
 * Spring Data R2DBC Repository nội bộ cho bảng users.
 */
public interface SpringDataUserRepository extends R2dbcRepository<User, String> {
    Mono<User> findByUsername(String username);
}
