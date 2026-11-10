# 🗺️ Lộ trình Triển khai MiniAuth (Lightweight IAM Platform)

> **Mục tiêu**: Xây dựng một Authorization Server độc lập chuẩn **OAuth 2.0 / OpenID Connect (OIDC)** theo mô hình Reactive Non-blocking (Spring WebFlux + R2DBC + Nimbus JOSE+JWT), kế thừa các kiến trúc tinh hoa từ **Gravitee Access Management**.

---

## 📊 Bảng Theo dõi Tiến độ Tổng thể (Progress Tracker)

> 💡 *Xem quy chuẩn quản lý nhánh tại [docs/git_workflow.md](docs/git_workflow.md).*

| Giai đoạn | Tên phân hệ | Git Branch | Trạng thái | Tài liệu chi tiết |
| :--- | :--- | :---: | :---: | :--- |
| **Giai đoạn 1** | Tầng Cơ sở (Clean Architecture, R2DBC Reactive DB, Exceptions) | `main` |  **HOÀN THÀNH** | [Xem chi tiết Phase 1](docs/phase1_clean_architecture_persistence.md) |
| **Giai đoạn 2** | Động cơ Mật mã & Quản lý Token (RSA RS256, Nimbus JWT, JWKS, OIDC Discovery) | `main` |  **HOÀN THÀNH** | [Xem chi tiết Phase 2](docs/phase2_cryptography_jwt_jwks.md) |
| **Giai đoạn 3** | Tầng Xác thực đa nhà cung cấp (Strategy Pattern & IDP Registry) | `main` |  **HOÀN THÀNH** | [Xem chi tiết Phase 3](docs/phase3_authentication_strategy_idp.md) |
| **Giai đoạn 4** | Chuẩn OAuth 2.0 / OIDC Flow (Authorize Endpoint, PKCE, Token Exchange) | `feat/phase-4-oauth2-oidc-pkce` |  **HOÀN THÀNH** | [Xem chi tiết Phase 4](docs/phase4_oauth2_oidc_pkce_flows.md) |
| **Giai đoạn 5** | Reactive Middleware & Audit Logging (CorrelationId, Brute-force, Audit Trail) | `feat/phase-5-middleware-audit` |  **HOÀN THÀNH** | [Xem chi tiết Phase 5](docs/phase5_reactive_middleware_audit.md) |
| **Giai đoạn 6** | Demo Client WebApp & Docker Packaging | `feat/phase-6-demo-client-docker` |  **HOÀN THÀNH** | [Xem chi tiết Phase 6](docs/phase6_demo_client_docker.md) |

---

## 🏗️ Kiến trúc Tổng thể (High-Level Architecture)

```
                       +---------------------------------------+
                       |           Demo Client App             |
                       |      (SPA / HTML5 + Vanilla JS)       |
                       +---------------------------------------+
                                        |  ^
                      1. Redirect Login |  | 3. Trả Token & Profile
                                        v  |
+----------------------------------------------------------------------------------------+
|                                  MINIAUTH SERVER                                       |
|                                                                                        |
|  [ Reactive Middleware Pipeline: CorrelationID | RateLimiter | BruteForce Defense ]    |
|                                        |                                               |
|  +-------------------------------------+--------------------------------------------+  |
|  |                                OIDC Endpoints                                    |  |
|  |   - GET  /.well-known/openid-configuration                                       |  |
|  |   - GET  /.well-known/jwks.json                                                  |  |
|  |   - GET  /oauth/authorize  (Login & Consent Page)                                |  |
|  |   - POST /oauth/token      (PKCE Verification, Authorization Code Exchange)      |  |
|  |   - GET  /userinfo         (Profile Endpoint)                                    |  |
|  +-------------------------------------+--------------------------------------------+  |
|                                        |                                               |
|  +-------------------------------------+--------------------------------------------+  |
|  |                             Core Business Domain                                 |  |
|  |   - AuthService                                                                  |  |
|  |   - AuthenticationProviderRegistry (Strategy Pattern)                            |  |
|  |       ├── LocalDatabaseAuthenticationProvider (BCrypt Hash)                      |  |
|  |       └── MockSocialAuthenticationProvider (Google / GitHub Mock)                |  |
|  |   - TokenService (Nimbus JOSE+JWT + RSA 2048-bit RS256)                          |  |
|  |   - AuditService (Async Logging via Schedulers.boundedElastic)                   |  |
|  +-------------------------------------+--------------------------------------------+  |
|                                        |                                               |
|  +-------------------------------------+--------------------------------------------+  |
|  |                    Ports & Adapters (Persistence Layer)                          |  |
|  |   - Ports (Core): UserRepository, ClientRepository, AuditRepository              |  |
|  |   - Adapters: Spring Data R2DBC Repository Adapters (Non-blocking I/O)           |  |
|  +-------------------------------------+--------------------------------------------+  |
|                                        |                                               |
+----------------------------------------|-----------------------------------------------+
                                         v
                         +-------------------------------+
                         |   Reactive DB (PostgreSQL/H2) |
                         +-------------------------------+
```

---

## 📑 Danh mục các tài liệu chi tiết từng phần

1. [docs/phase1_clean_architecture_persistence.md](docs/phase1_clean_architecture_persistence.md):
   - Bản thiết kế Clean Architecture (Ports & Adapters).
   - Mô hình dữ liệu Reactive R2DBC (`User`, `ClientApplication`, `AuditLog`, `AuthorizationCode`).
   - Cây ngoại lệ OAuth2 Exception Hierarchy.
2. [docs/phase2_cryptography_jwt_jwks.md](docs/phase2_cryptography_jwt_jwks.md):
   - Động cơ sinh và quản lý cặp khóa RSA 2048-bit (`KeyManager`).
   - Triển khai ký và xác thực Token chuẩn RFC 7519 bằng Nimbus JOSE+JWT.
   - Endpoint JWKS và OIDC Discovery (`/.well-known/*`).
3. [docs/phase3_authentication_strategy_idp.md](docs/phase3_authentication_strategy_idp.md):
   - Thiết kế Strategy Pattern cho Identity Providers (IDP).
   - IDP Registry động (tương tự cơ chế plugin của Gravitee AM).
   - Local DB Authentication & BCrypt Password Encoder.
4. [docs/phase4_oauth2_oidc_pkce_flows.md](docs/phase4_oauth2_oidc_pkce_flows.md):
   - Luồng Authorization Code Flow kết hợp PKCE (Proof Key for Code Exchange).
   - Thiết kế màn hình đăng nhập (Login) và xin cấp quyền (Consent).
   - Cấp phát Access Token, ID Token và endpoint `/userinfo`.
5. [docs/phase5_reactive_middleware_audit.md](docs/phase5_reactive_middleware_audit.md):
   - Reactive Filter Pipeline: Correlation ID gắn vào MDC Context.
   - Phòng thủ Brute-force theo IP và Username.
   - Cơ chế ghi Audit Trail bất đồng bộ không phong tỏa luồng chính.
6. [docs/phase6_demo_client_docker.md](docs/phase6_demo_client_docker.md):
   - Ứng dụng Demo Client giao diện Web.
   - Kịch bản kiểm thử End-to-End từ trình duyệt.
   - Đóng gói toàn bộ hệ thống bằng Docker Compose (PostgreSQL + MiniAuth + Demo Client).
