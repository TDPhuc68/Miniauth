package com.miniauth.infrastructure.web.filter;

import com.miniauth.core.service.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RateLimitFilterTest {

    private RateLimitFilter filter;
    private AuditService auditService;
    private WebFilterChain chain;

    @BeforeEach
    void setUp() {
        auditService = mock(AuditService.class);
        filter = new RateLimitFilter(auditService);
        chain = mock(WebFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    @Test
    @DisplayName("Should allow normal requests within limit")
    void shouldAllowNormalRequests() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/oauth/token")
                .remoteAddress(new InetSocketAddress("192.168.1.100", 54321))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain, times(1)).filter(exchange);
    }

    @Test
    @DisplayName("Should block requests with 429 when exceeding rate limit")
    void shouldBlockWhenExceedingLimit() {
        String ip = "10.0.0.99";

        // Gửi 30 request đầu tiên (hợp lệ)
        for (int i = 0; i < 30; i++) {
            MockServerHttpRequest req = MockServerHttpRequest.post("/oauth/token")
                    .remoteAddress(new InetSocketAddress(ip, 50000 + i))
                    .build();
            MockServerWebExchange ex = MockServerWebExchange.from(req);
            filter.filter(ex, chain).block();
        }

        // Request thứ 31 -> Phải bị chặn với mã 429 TOO_MANY_REQUESTS
        MockServerHttpRequest excessReq = MockServerHttpRequest.post("/oauth/token")
                .remoteAddress(new InetSocketAddress(ip, 51000))
                .build();
        MockServerWebExchange excessEx = MockServerWebExchange.from(excessReq);

        StepVerifier.create(filter.filter(excessEx, chain))
                .verifyComplete();

        assertThat(excessEx.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        verify(auditService, atLeastOnce()).recordEvent(eq("RATE_LIMIT_EXCEEDED"), any(), any(), any(), any(), any());
    }
}
