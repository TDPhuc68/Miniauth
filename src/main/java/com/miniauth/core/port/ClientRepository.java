package com.miniauth.core.port;

import com.miniauth.core.model.ClientApplication;
import reactor.core.publisher.Mono;

public interface ClientRepository {
    Mono<ClientApplication> findByClientId(String clientId);
    Mono<ClientApplication> save(ClientApplication client);
}
