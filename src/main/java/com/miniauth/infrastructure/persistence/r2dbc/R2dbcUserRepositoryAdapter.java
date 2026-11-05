package com.miniauth.infrastructure.persistence.r2dbc;

import com.miniauth.core.model.User;
import com.miniauth.core.port.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * Adapter kết nối giữa Port thuần túy của Core (UserRepository)
 * với cơ chế lưu trữ Spring Data R2DBC.
 */
@Repository
@RequiredArgsConstructor
public class R2dbcUserRepositoryAdapter implements UserRepository {

    private final SpringDataUserRepository springDataUserRepository;

    @Override
    public Mono<User> findById(String id) {
        return springDataUserRepository.findById(id);
    }

    @Override
    public Mono<User> findByUsername(String username) {
        return springDataUserRepository.findByUsername(username);
    }

    @Override
    public Mono<User> save(User user) {
        return springDataUserRepository.save(user);
    }
}
