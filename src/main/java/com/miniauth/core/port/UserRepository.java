package com.miniauth.core.port;

import com.miniauth.core.model.User;
import reactor.core.publisher.Mono;

public interface UserRepository {
    Mono<User> findById(String id);
    Mono<User> findByUsername(String username);
    Mono<User> save(User user);
}
