package com.miniauth.infrastructure.web.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniauth.core.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Reactive WebFilter bảo vệ hệ thống khỏi các cuộc tấn công Brute-force / DoS
 * bằng cách áp dụng thuật toán Rate Limiting (Sliding Window Counter) theo địa chỉ IP.
 *
 * Chặn ngay tại tầng Gateway trước khi request chạm vào Database hoặc Crypto Engine.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 50)
@RequiredArgsConstructor
public class RateLimitFilter implements WebFilter {

    private static final int MAX_REQUESTS_PER_MINUTE = 30;
    private static final long WINDOW_SIZE_MILLIS = 60_000L; // 1 phút

    private final Map<String, IpRequestTracker> ipTrackers = new ConcurrentHashMap<>();
    private final AuditService auditService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        // Chỉ áp dụng rate limiting cho các endpoint nhạy cảm (Đăng nhập & Cấp Token)
        if (!path.startsWith("/oauth/token") && !path.startsWith("/oauth/authorize/login")) {
            return chain.filter(exchange);
        }

        String clientIp = resolveClientIp(exchange);
        IpRequestTracker tracker = ipTrackers.compute(clientIp, (key, existingTracker) -> {
            long now = Instant.now().toEpochMilli();
            if (existingTracker == null || now - existingTracker.windowStart > WINDOW_SIZE_MILLIS) {
                return new IpRequestTracker(now, new AtomicInteger(1));
            }
            existingTracker.counter.incrementAndGet();
            return existingTracker;
        });

        if (tracker.counter.get() > MAX_REQUESTS_PER_MINUTE) {
            log.warn("Rate limit exceeded for IP [{}] on path [{}] - Count: {}", clientIp, path, tracker.counter.get());

            // Ghi nhận sự kiện Audit
            auditService.recordEvent(
                    "RATE_LIMIT_EXCEEDED",
                    clientIp,
                    path,
                    "FAILURE",
                    "Exceeded " + MAX_REQUESTS_PER_MINUTE + " requests per minute",
                    clientIp
            );

            return blockRequestWith429(exchange);
        }

        return chain.filter(exchange);
    }

    private String resolveClientIp(ServerWebExchange exchange) {
        String forwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        if (exchange.getRequest().getRemoteAddress() != null) {
            return exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        }
        return "UNKNOWN_IP";
    }

    private Mono<Void> blockRequestWith429(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> errorResponse = Map.of(
                "error", "slow_down",
                "error_description", "Rate limit exceeded. Too many requests. Please wait a minute."
        );

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsString(errorResponse).getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            bytes = "{\"error\":\"slow_down\"}".getBytes(StandardCharsets.UTF_8);
        }

        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private static class IpRequestTracker {
        final long windowStart;
        final AtomicInteger counter;

        IpRequestTracker(long windowStart, AtomicInteger counter) {
            this.windowStart = windowStart;
            this.counter = counter;
        }
    }
}
