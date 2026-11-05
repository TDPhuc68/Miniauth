# 🛡️ Giai đoạn 5: Reactive Middleware & Vết kiểm toán (Audit Trail)

> **Trạng thái**: 📋 **CHỜ XỬ LÝ (THEO KẾ HOẠCH)**  
> **Kiến thức kế thừa**: `gravitee-am-gateway` & `gravitee-am-service`

---

## 1. Bản chất Kiến trúc: Tại sao lại chọn thiết kế này?

### 1.1. Reactive Context & Correlation ID (Distributed Tracing)
* **Vấn đề của ThreadLocal trong WebFlux**:
  - Trong Spring MVC truyền thống, bạn thường lưu `userId` hoặc `correlationId` vào `ThreadLocal` (thông qua MDC logging).
  - Tuy nhiên, trong mô hình Reactive Non-blocking, **một request có thể nhảy qua nhiều Thread khác nhau** trong quá trình xử lý, hoặc một Thread có thể phục vụ xen kẽ nhiều request cùng lúc. Sử dụng `ThreadLocal` sẽ gây hiện tượng **rò rỉ dữ liệu (Data Leak)** giữa các request của người dùng khác nhau!
* **Giải pháp: Reactor Context**:
  - Gắn `correlationId` (UUID) vào luồng `contextWrite(Context.of("correlationId", ...))`.
  - Mọi câu lệnh log từ WebFilter $\rightarrow$ Business Service $\rightarrow$ Database Adapter đều được đính kèm đúng Correlation ID của request đó.

### 1.2. Phòng vệ Brute-Force phi phong tỏa (Rate Limiting)
* IAM là đối tượng tấn công hàng đầu của các cuộc tấn công dò quét mật khẩu (Credential Stuffing / Brute-force).
* Xây dựng bộ lọc chặn ngay ở cửa ngõ (Gateway Filter):
  - Theo dõi số lần đăng nhập sai theo cặp `(IP, Username)`.
  - Nếu sai quá 5 lần trong 5 phút $\rightarrow$ Lập tức trả về mã lỗi `429 Too Many Requests` hoặc `401 Account Locked` mà không cần tốn tài nguyên gọi tiếp xuống Database.

### 1.3. Ghi vết kiểm toán (Audit Trail) bất đồng bộ
* Mọi hành vi: Đăng nhập thành công, Đăng nhập thất bại, Cấp phát Token, Khóa tài khoản đều phải được ghi lại vĩnh viễn để phục vụ thanh tra an ninh.
* **Tại sao phải là Bất đồng bộ (Async Non-blocking)?**
  - Người dùng không cần phải chờ việc ghi Audit Log vào đĩa cứng hoàn tất thì mới được nhận Token.
  - Tách luồng Audit sang `Schedulers.boundedElastic()` chạy độc lập (Fire-and-forget an toàn), đảm bảo nếu database audit có chậm thì tốc độ trả token cho user vẫn giữ ở mức dưới 10ms.

---

## 2. Các bước triển khai chi tiết

### 2.1. `CorrelationIdFilter` (WebFilter)
- Kiểm tra Header `X-Correlation-Id`. Nếu chưa có, tự sinh `UUID.randomUUID()`.
- Gắn vào Response Header để Client đối chiếu khi báo lỗi.
- Đưa vào Reactor Context phục vụ in log có cấu trúc.

### 2.2. `BruteForceProtectionFilter` / `BruteForceService`
- Sử dụng ConcurrentMap có cơ chế tự dọn dẹp hoặc Sliding Window để đếm số lần thất bại.
- Cấu hình qua `application.yml`:
  ```yaml
  miniauth:
    security:
      max-failed-attempts: 5
      lockout-duration-minutes: 15
  ```

### 2.3. `AuditService`
- Cung cấp hàm: `logEvent(String eventType, String actor, String target, String status, String details, String ipAddress)`.
- Ghi vào bảng `audit_logs` qua `AuditRepository` hoàn toàn non-blocking.

---

## 3. Tiêu chí Kiểm chứng (Verification)

1. Gửi request bất kỳ lên Server $\rightarrow$ Kiểm tra Response Header có chứa `X-Correlation-Id: xxxxxxxx-xxxx-...`.
2. Kiểm tra Console Log thấy mỗi dòng log đều mang `[correlationId]`.
3. Cố tình nhập sai mật khẩu 5 lần liên tiếp:
   - Lần thứ 6 nhận về lỗi bị khóa ngay lập tức.
   - Bảng `audit_logs` trong cơ sở dữ liệu xuất hiện 5 bản ghi `LOGIN_FAILURE` và 1 bản ghi `ACCOUNT_LOCKED`.
