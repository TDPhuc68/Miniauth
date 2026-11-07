# 🛡️ MiniAuth - Lightweight Identity & Access Management Platform

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.5-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Spring WebFlux](https://img.shields.io/badge/Engine-Reactive%20WebFlux-blue.svg)](https://projectreactor.io/)
[![Database](https://img.shields.io/badge/R2DBC-PostgreSQL%20%2F%20H2-blueviolet.svg)](https://r2dbc.io/)
[![Nimbus JOSE+JWT](https://img.shields.io/badge/Crypto-Nimbus%20RS256-lightgrey.svg)](https://connect2id.com/products/nimbus-jose-jwt)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

> **MiniAuth** là một Authorization Server / Identity Provider (IDP) độc lập chuẩn **OAuth 2.0 / OpenID Connect (OIDC)**, được xây dựng theo mô hình **Reactive Non-blocking I/O** (Spring WebFlux + R2DBC + Nimbus JOSE+JWT). Dự án được chắt lọc từ kiến trúc backend tinh hoa của **Gravitee Access Management (AM)**, loại bỏ các thành phần cồng kềnh nhưng giữ lại 100% tư duy thiết kế chuyên nghiệp của một hệ thống IAM doanh nghiệp.

---

## 🎯 Điểm nổi bật & Tính năng cốt lõi

* ⚡ **Reactive Non-blocking Engine**: Xây dựng hoàn toàn trên Spring WebFlux và Spring Data R2DBC. Không bao giờ phong tỏa (block) thread khi tương tác với cơ sở dữ liệu, cho phép phục vụ hàng chục ngàn kết nối đồng thời với mức tiêu hao tài nguyên tối thiểu.
* 🏗️ **Clean Architecture (Ports and Adapters)**: Tách biệt hoàn toàn giữa Core Business Domain và Persistence/Framework Adapters. Dễ dàng hoán đổi cơ sở dữ liệu (PostgreSQL, H2, MongoDB, Redis) mà không cần chỉnh sửa một dòng code nghiệp vụ nào.
* 🔐 **Mật mã & Ký số Chuẩn mực (RSA 2048-bit RS256)**:
  - Sử dụng cặp khóa bất đối xứng RSA: Private Key tuyệt mật để ký Token, Public Key công khai.
  - Cung cấp endpoint công khai chuẩn RFC 7517: `GET /.well-known/jwks.json` và RFC 8414: `GET /.well-known/openid-configuration`.
  - Resource Server có thể tự động tải Public Key về để kiểm tra chữ ký mà Auth Server không cần chia sẻ bất kỳ Secret Key nào.
* 🔌 **Tầng Xác thực đa nhà cung cấp (Strategy Pattern & IDP Registry)**:
  - Cắm/rút nguồn người dùng (Database nội bộ hash BCrypt, Google OAuth2, Mock LDAP) mà không vi phạm nguyên lý Open-Closed Principle.
  - Cơ chế tự động khóa tài khoản khi nhập sai mật khẩu quá số lần quy định (Brute-force protection).
* 🔄 **OAuth 2.0 / OIDC Flow + PKCE (RFC 7636)**:
  - Luồng Authorization Code Flow kết hợp cơ chế PKCE (Proof Key for Code Exchange) bắt buộc, ngăn chặn hoàn toàn nguy cơ nghe lén và đánh cắp Authorization Code trên Single Page Apps (SPA) và Mobile Apps.
* 🌿 **Quản lý Nhánh Git Chuẩn mực**: Áp dụng mô hình Feature Branch Workflow kết hợp Conventional Commits cho từng giai đoạn tính năng.

---

## 📐 Kiến trúc Hệ thống (System Architecture)

```mermaid
graph TD
    subgraph Client Application (SPA / Web / Mobile)
        AppUI["Client App (HTML5 / JS / React)"]
    end

    subgraph MiniAuth Server (Reactive IAM Core)
        Gateway["Reactive Filter Pipeline: CorrelationID, RateLimit, BruteForce"]
        
        subgraph Endpoints (OIDC Core)
            AuthEP["/oauth/authorize (Login & Consent UI)"]
            TokenEP["/oauth/token (Code Exchange + PKCE)"]
            JWKSEP["/.well-known/jwks.json (Public Keys)"]
            DiscoveryEP["/.well-known/openid-configuration"]
            UserEP["/userinfo (Profile Service)"]
        end

        subgraph Business Core
            AuthService[Authentication Service]
            TokenService[JWT & Crypto Service]
            AuditService[Audit Log Service]
            IDPRegistry[Identity Provider Registry]
        end

        subgraph Identity Adapters (Strategy Pattern)
            LocalIDP[Local Database IDP - BCrypt]
            SocialIDP[Google / Mock Social IDP]
        end

        subgraph Ports & Adapters (Persistence)
            UserRepoPort[UserRepository Port]
            ClientRepoPort[ClientAppRepository Port]
            AuditRepoPort[AuditRepository Port]
        end
    end

    subgraph Database
        R2DBC[(Reactive Database: PostgreSQL / H2)]
    end

    AppUI -->|1. Redirect Login| AuthEP
    AppUI -->|2. Exchange Code for Token| TokenEP
    AppUI -->|3. Get Public Key to Verify JWT| JWKSEP

    AuthEP --> Gateway
    TokenEP --> Gateway
    Gateway --> AuthService
    AuthService --> IDPRegistry
    IDPRegistry --> LocalIDP
    IDPRegistry --> SocialIDP

    TokenEP --> TokenService
    AuthService --> AuditService

    LocalIDP --> UserRepoPort
    TokenService --> ClientRepoPort
    AuditService --> AuditRepoPort

    UserRepoPort --> R2DBC
    ClientRepoPort --> R2DBC
    AuditRepoPort --> R2DBC
```

---

## 🛠️ Tech Stack & Thư viện sử dụng

| Phân hệ | Công nghệ | Mục đích sử dụng |
| :--- | :--- | :--- |
| **Ngôn ngữ** | Java 21 LTS | Khai thác tính năng hiện đại (Pattern Matching, Sealed Classes, Record). |
| **Web Framework** | Spring Boot 3.3.5 / Spring WebFlux | Mô hình Non-blocking Event Loop tương tự Vert.x trong Gravitee AM. |
| **Database** | Spring Data R2DBC + H2 / PostgreSQL | Truy cập cơ sở dữ liệu phản ứng (Reactive Relational DB Connectivity). |
| **Mật mã & Token** | Nimbus JOSE+JWT (`nimbus-jose-jwt`) | Thư viện chuẩn mực Gravitee AM dùng để sinh RSA Key, ký JWT và xuất JWKS. |
| **Bảo mật mật khẩu** | Spring Security Crypto | Hash mật khẩu an toàn với BCrypt. |
| **Giao diện Demo** | Thymeleaf & HTML5 + Vanilla JS | Giao diện đăng nhập, màn hình xin quyền (Consent) và Demo Client. |
| **Testing** | JUnit 5 + Mockito + Reactor Test (`StepVerifier`) | Kiểm thử toàn diện các luồng Reactive bất đồng bộ. |

---

## 📂 Cấu trúc Thư mục Dự án (Clean Architecture)

```text
miniauth/
├── docs/                                   # Tài liệu chi tiết từng giai đoạn & Git workflow
│   ├── git_workflow.md                     # Quy chuẩn phân nhánh Git (Feature Branch Workflow)
│   ├── phase1_clean_architecture_persistence.md
│   ├── phase2_cryptography_jwt_jwks.md
│   ├── phase3_authentication_strategy_idp.md
│   ├── phase4_oauth2_oidc_pkce_flows.md
│   ├── phase5_reactive_middleware_audit.md
│   └── phase6_demo_client_docker.md
├── src/
│   ├── main/
│   │   ├── java/com/miniauth/
│   │   │   ├── MiniAuthApplication.java
│   │   │   ├── core/                      # DOMAIN CORE (Độc lập Framework)
│   │   │   │   ├── exception/             # Cây ngoại lệ chuẩn OAuth 2.0 (RFC 6749)
│   │   │   │   ├── model/                 # Domain Entities (User, Client, Audit, Token)
│   │   │   │   ├── port/                  # Ports/Contracts (UserRepository, TokenService, IDP)
│   │   │   │   └── service/               # Core Services (AuthService)
│   │   │   └── infrastructure/            # ADAPTERS & INFRASTRUCTURE
│   │   │       ├── config/                # Cấu hình Spring & PasswordEncoder
│   │   │       ├── crypto/                # RSA KeyManager & NimbusJwtTokenService
│   │   │       ├── idp/                   # Database & Social IDP Strategies + Registry
│   │   │       ├── persistence/r2dbc/     # R2DBC Spring Data Adapters
│   │   │       └── web/                   # Reactive Controllers & OAuth2ExceptionHandler
│   │   └── resources/
│   │       ├── application.yml            # Cấu hình Issuer, JWT, Security Brute-force
│   │       └── schema.sql                 # DDL & Seed Data cho H2 / PostgreSQL
│   └── test/                              # Bộ kiểm thử Unit & Reactive StepVerifier
├── pom.xml                                # Cấu hình Maven dependencies & compiler
├── ROADMAP.md                             # Bảng theo dõi tiến độ tổng thể các giai đoạn
└── README.md                              # Giới thiệu tổng quan dự án
```

---

## 🚀 Khởi động & Kiểm thử Dự án

### 1. Yêu cầu môi trường
* **Java**: JDK 21 trở lên
* **Maven**: 3.8+ (hoặc dùng Maven wrapper)

### 2. Kiểm thử dự án (Unit & Reactive Tests)
Chạy toàn bộ bộ test kiểm tra tính toàn vẹn của RSA Token, JWKS và IDP Strategy:
```bash
mvn clean test
```

### 3. Khởi động Server
```bash
mvn spring-boot:run
```
Sau khi khởi động thành công, máy chủ lắng nghe tại cổng `http://localhost:8080`.

### 4. Kiểm tra các Endpoint công khai (OIDC Discovery & JWKS)
* **JWKS Endpoint**: `http://localhost:8080/.well-known/jwks.json`
* **OpenID Configuration**: `http://localhost:8080/.well-known/openid-configuration`

### 5. Tài khoản mẫu (Seed Data)
Dữ liệu mẫu đã được nạp sẵn tự động trong `src/main/resources/schema.sql`:
* **User**: `john_doe` / Mật khẩu: `password123`
* **Client App**: `demo-client` / Secret: `demo-secret` (Hỗ trợ redirect URIs: `http://localhost:3000/callback.html`)

---

## 🗺️ Lộ trình Phát triển (Roadmap)

Xem chi tiết bảng theo dõi tiến độ và quy chuẩn phân nhánh tại:
* 📊 **[ROADMAP.md](ROADMAP.md)**: Tiến độ 6 giai đoạn phát triển.
* 🌿 **[docs/git_workflow.md](docs/git_workflow.md)**: Quy chuẩn phân nhánh Git (Feature Branch Workflow).

---

## 📄 Bản quyền (License)

Dự án được phân phối dưới giấy phép [MIT License](LICENSE).
