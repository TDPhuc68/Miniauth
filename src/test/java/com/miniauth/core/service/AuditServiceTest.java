package com.miniauth.core.service;

import com.miniauth.core.model.AuditLog;
import com.miniauth.core.port.AuditRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuditServiceTest {

    private AuditRepository auditRepository;
    private AuditService auditService;

    @BeforeEach
    void setUp() {
        auditRepository = mock(AuditRepository.class);
        when(auditRepository.save(any(AuditLog.class))).thenReturn(Mono.just(new AuditLog()));
        auditService = new AuditService(auditRepository);
    }

    @Test
    @DisplayName("Should save audit log asynchronously")
    void shouldSaveAuditLogAsynchronously() throws InterruptedException {
        auditService.recordEvent(
                "USER_LOGIN",
                "john_doe",
                "demo-client",
                "SUCCESS",
                "Test login",
                "127.0.0.1"
        );

        // Chờ ngắn để luồng bất đồng bộ boundedElastic thực thi
        Thread.sleep(150);

        verify(auditRepository, times(1)).save(any(AuditLog.class));
    }
}
