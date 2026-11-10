package com.miniauth.core.service;

import com.miniauth.core.model.AuditLog;
import com.miniauth.core.port.AuditRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Dịch vụ ghi vết kiểm toán (Audit Trail) bất đồng bộ.
 * Kế thừa triết lý từ Gravitee AM: Mọi sự kiện định danh quan trọng đều được ghi nhận
 * trên luồng riêng (Schedulers.boundedElastic) để tuyệt đối không làm chậm luồng xử lý chính.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditRepository auditRepository;

    /**
     * Ghi lại một sự kiện kiểm toán theo mô hình Fire-and-forget phi phong tỏa.
     *
     * @param eventType Loại sự kiện: USER_LOGIN, TOKEN_ISSUED, CONSENT_GRANTED, etc.
     * @param actor     Người hoặc client thực hiện: "john_doe", "demo-client", "system"
     * @param target    Đối tượng chịu tác động: "miniauth-server", "demo-client"
     * @param status    Trạng thái: "SUCCESS", "FAILURE"
     * @param details   Thông tin bổ sung hoặc nguyên nhân lỗi
     * @param ipAddress Địa chỉ IP của client gửi request
     */
    public void recordEvent(
            String eventType,
            String actor,
            String target,
            String status,
            String details,
            String ipAddress
    ) {
        AuditLog auditLog = AuditLog.builder()
                .id("aud-" + UUID.randomUUID().toString().substring(0, 8))
                .eventType(eventType)
                .actor(actor != null ? actor : "ANONYMOUS")
                .target(target != null ? target : "SYSTEM")
                .status(status != null ? status : "UNKNOWN")
                .details(details)
                .ipAddress(ipAddress != null ? ipAddress : "UNKNOWN")
                .createdAt(LocalDateTime.now())
                .build();

        auditRepository.save(auditLog)
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(saved -> log.debug("Audit log recorded: [{}] actor=[{}] status=[{}]", saved.getEventType(), saved.getActor(), saved.getStatus()))
                .doOnError(err -> log.warn("Failed to persist async audit log: {}", err.getMessage()))
                .subscribe();
    }
}
