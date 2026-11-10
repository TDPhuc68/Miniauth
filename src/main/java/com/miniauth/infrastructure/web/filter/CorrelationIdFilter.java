package com.miniauth.infrastructure.web.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.UUID;

/**
 * Reactive WebFilter tự động khởi tạo và duy trì Correlation ID (X-Correlation-Id)
 * cho mỗi request.
 *
 * Tại sao cần bộ lọc này?
 * Trong kiến trúc Non-blocking (WebFlux), một request không gắn cố định vào một Thread,
 * do đó ThreadLocal truyền thống sẽ gây hiện tượng rò rỉ dữ liệu giữa các request.
 * CorrelationIdFilter giải quyết vấn đề này bằng cách lưu trữ ID vào Reactor Context
 * và đính kèm vào Response Header để phục vụ Distributed Tracing.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE) // Luôn chạy đầu tiên trong Pipeline
public class CorrelationIdFilter implements WebFilter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String CORRELATION_ID_KEY = "correlationId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String correlationId = exchange.getRequest().getHeaders().getFirst(CORRELATION_ID_HEADER);

        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        // Đính kèm vào Response Header để Client/Gateway có thể đối chiếu
        exchange.getResponse().getHeaders().add(CORRELATION_ID_HEADER, correlationId);

        String finalCorrelationId = correlationId;
        return chain.filter(exchange)
                .contextWrite(Context.of(CORRELATION_ID_KEY, finalCorrelationId))
                .doOnEach(signal -> {
                    if (signal.isOnComplete()) {
                        log.debug("Completed request [{}] {} - Correlation ID: {}",
                                exchange.getRequest().getMethod(),
                                exchange.getRequest().getPath(),
                                finalCorrelationId);
                    }
                });
    }
}
