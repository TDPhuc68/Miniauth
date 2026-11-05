package com.miniauth.infrastructure.persistence.r2dbc;

import com.miniauth.core.model.ClientApplication;
import com.miniauth.core.port.ClientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * Adapter kết nối ClientRepository với Spring Data R2DBC.
 */
@Repository
@RequiredArgsConstructor
public class R2dbcClientRepositoryAdapter implements ClientRepository {

    private final SpringDataClientRepository springDataClientRepository;

    @Override
    public Mono<ClientApplication> findByClientId(String clientId) {
        return springDataClientRepository.findByClientId(clientId);
    }

    @Override
    public Mono<ClientApplication> save(ClientApplication client) {
        return springDataClientRepository.save(client);
    }
}
