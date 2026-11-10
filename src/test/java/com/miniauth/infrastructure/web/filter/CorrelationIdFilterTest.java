package com.miniauth.infrastructure.web.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CorrelationIdFilterTest {

    private CorrelationIdFilter filter;
    private WebFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new CorrelationIdFilter();
        chain = mock(WebFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    @Test
    @DisplayName("Should generate new correlation ID if not present in request")
    void shouldGenerateNewCorrelationId() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/oauth/authorize").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        String responseHeader = exchange.getResponse().getHeaders().getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER);
        assertThat(responseHeader).isNotBlank();
        assertThat(responseHeader).matches("^[0-9a-fA-F-]{36}$"); // UUID format
    }

    @Test
    @DisplayName("Should preserve existing correlation ID if sent by client")
    void shouldPreserveExistingCorrelationId() {
        String existingId = "client-tx-9999-custom";
        MockServerHttpRequest request = MockServerHttpRequest.get("/oauth/authorize")
                .header(CorrelationIdFilter.CORRELATION_ID_HEADER, existingId)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        String responseHeader = exchange.getResponse().getHeaders().getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER);
        assertThat(responseHeader).isEqualTo(existingId);
    }
}
