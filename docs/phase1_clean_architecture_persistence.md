# 📘 Giai đoạn 1: Tầng Cơ sở (Clean Architecture & Reactive Persistence)

> **Trạng thái**:  **HOÀN THÀNH**  
> **Kiến thức kế thừa**: `gravitee-am-model` & `gravitee-am-repository`

---

## 1. Bản chất Kiến trúc: Tại sao lại chọn thiết kế này?

### 1.1. Ports and Adapters (Hexagonal Architecture) vs 3-Tier truyền thống

Trong mô hình Spring Boot thông thường (Controller $\rightarrow$ Service $\rightarrow$ Repository), `Service` thường `import org.springframework.data.jpa...` hoặc phụ thuộc trực tiếp vào Spring Data Interfaces.
* **Hậu quả**: Nghiệp vụ (Business Core) bị **trói chặt** vào Framework và Database Engine. Khi muốn đổi DB, nâng cấp framework, hoặc viết Unit Test độc lập không có Spring Context, ta gặp muôn vàn khó khăn.
* **Giải pháp Clean Architecture**:
  - Tách thành **Core Port (Contract)**: Interface Java thuần túy đặt tại `com.miniauth.core.port`. Interface này chỉ sử dụng Reactive Stream (`Mono`, `Flux`) và Model nghiệp vụ. Nó định nghĩa: *"Tôi cần tìm User theo username, hệ thống lưu ở đâu tôi không quan tâm"*.
  - Tách thành **Infrastructure Adapter**: Triển khai tại `com.miniauth.infrastructure.persistence.r2dbc`. Adapter này bọc Spring Data R2DBC và thực thi các Interface của Port.
  - **Lợi ích**: Khi đổi từ PostgreSQL/H2 sang MongoDB hoặc In-memory Cache, ta chỉ cần viết thêm một Adapter mới, toàn bộ Core Domain được giữ nguyên 100%.

### 1.2. Tại sao lại là R2DBC & Project Reactor (`Mono`/`Flux`) thay vì Spring MVC + JDBC?

* **Hệ thống IAM là trung tâm cổ chai (Bottleneck)**: Mọi dịch vụ khác trong hệ thống microservices đều phải gọi về IAM để xác thực token hoặc xin cấp quyền.
* **Vấn đề của mô hình Blocking (Spring MVC + JDBC)**: Mỗi kết nối chiếm giữ 1 Thread của OS. Khi Database xử lý chậm hoặc mạng chập chờn, hàng trăm thread rơi vào trạng thái chờ (I/O Wait), nhanh chóng cạn kiệt Thread Pool và làm sập ứng dụng.
* **Sức mạnh của Reactive Non-blocking (R2DBC + WebFlux)**:
  - Chỉ cần số lượng thread rất nhỏ (bằng số CPU cores).
  - Khi gửi câu lệnh truy vấn xuống R2DBC driver, Thread được giải phóng ngay lập tức để đón nhận request tiếp theo.
  - Khi Database có kết quả, R2DBC kích hoạt Callback để tiếp tục pipeline xử lý luồng dữ liệu (`Mono`/`Flux`).
  - Đây chính là cách **Vert.x** bên trong Gravitee AM đạt được hàng trăm ngàn TPS (Transactions Per Second).

---

## 2. Chi tiết các thành phần đã triển khai

### 2.1. Domain Models (`com.miniauth.core.model`)
- [`User.java`](file:///c:/Users/Admin/miniauth/src/main/java/com/miniauth/core/model/User.java): Chứa thông tin định danh, mật khẩu đã hash, số lần đăng nhập sai (`failedAttempts`), trạng thái khóa tài khoản (`lockedUntil`, `isLocked()`).
- [`ClientApplication.java`](file:///c:/Users/Admin/miniauth/src/main/java/com/miniauth/core/model/ClientApplication.java): Đại diện cho ứng dụng bên thứ 3 đăng ký vào hệ thống (Client ID, Client Secret Hash, Redirect URIs hợp lệ, Authorized Grant Types, Scopes).
- [`AuthorizationCode.java`](file:///c:/Users/Admin/miniauth/src/main/java/com/miniauth/core/model/AuthorizationCode.java): Lưu thông tin mã xác thực tạm thời trong luồng Authorization Code Flow, tích hợp sẵn PKCE (`codeChallenge`, `codeChallengeMethod`).
- [`AuditLog.java`](file:///c:/Users/Admin/miniauth/src/main/java/com/miniauth/core/model/AuditLog.java): Thực thể ghi vết hành vi kiểm toán (Đăng nhập, Cấp token, Thất bại, v.v.).

### 2.2. Core Ports (`com.miniauth.core.port`)
- [`UserRepository.java`](file:///c:/Users/Admin/miniauth/src/main/java/com/miniauth/core/port/UserRepository.java):
  ```java
  public interface UserRepository {
      Mono<User> findById(String id);
      Mono<User> findByUsername(String username);
      Mono<User> save(User user);
  }
  ```
- [`ClientRepository.java`](file:///c:/Users/Admin/miniauth/src/main/java/com/miniauth/core/port/ClientRepository.java): Tìm kiếm và quản lý thông tin Client Applications.
- [`AuditRepository.java`](file:///c:/Users/Admin/miniauth/src/main/java/com/miniauth/core/port/AuditRepository.java): Lưu trữ sự kiện Audit bất đồng bộ.

### 2.3. R2DBC Persistence Adapters (`com.miniauth.infrastructure.persistence.r2dbc`)
- Áp dụng **Delegation Pattern**:
  - `SpringDataUserRepository`, `SpringDataClientRepository`, `SpringDataAuditRepository` kế thừa `R2dbcRepository`.
  - `R2dbcUserRepositoryAdapter`, `R2dbcClientRepositoryAdapter`, `R2dbcAuditRepositoryAdapter` implement các Core Ports và ủy quyền xử lý cho Spring Data R2DBC.

### 2.4. Cấu trúc Database (`src/main/resources/schema.sql`)
- Khởi tạo 3 bảng chính: `users`, `client_applications`, `audit_logs`.
- Cung cấp sẵn Seed Data cho:
  - User: `john_doe` / `password123` (BCrypt hash).
  - Client: `demo-client` / `demo-secret` (Hỗ trợ grant type: `authorization_code`, scopes: `openid,profile,email`).

### 2.5. Cây Ngoại lệ chuẩn OAuth 2.0 (`com.miniauth.core.exception`)
- `OAuth2Exception`: Lớp cha định dạng chuẩn mã lỗi RFC 6749 (`error`, `error_description`, `httpStatus`).
- `InvalidClientException`: Trả về khi `client_id` không tồn tại hoặc `client_secret` không đúng.
- `InvalidGrantException`: Trả về khi `authorization_code` hết hạn hoặc không khớp PKCE.
- `AccountLockedException`: Trả về khi tài khoản bị khóa do vượt ngưỡng brute-force.

---

## 3. Tiêu chí Kiểm chứng (Verification)

1. Khởi động ứng dụng với R2DBC H2 / Postgres, bảng được tự động khởi tạo thông qua `schema.sql`.
2. Seed User `john_doe` và Client `demo-client` được nạp sẵn vào database.
3. Các Adapter gọi bất đồng bộ trả về `Mono<User>` và `Mono<ClientApplication>` mà không phong tỏa thread.
