# 🔐 Giai đoạn 2: Động cơ Mật mã & Quản lý Token (Cryptography & Nimbus JWT)

> **Trạng thái**:  **HOÀN THÀNH**  
> **Kiến thức kế thừa**: `gravitee-am-jwt`

---

## 1. Bản chất Mật mã: Tại sao lại chọn thiết kế này?

### 1.1. Khóa đối xứng (Symmetric HMAC-SHA256) vs Khóa bất đối xứng (Asymmetric RSA-RS256)

| Tiêu chí | Khóa đối xứng (HS256) | Khóa bất đối xứng (RS256) |
| :--- | :--- | :--- |
| **Cơ chế khóa** | Dùng chung 1 Secret Key để vừa Ký vừa Xác minh. | Tách làm 2 khóa: **Private Key** (để ký) và **Public Key** (để xác minh). |
| **Bảo mật Microservices** | ⚠️ **Kém an toàn**: Muốn các Resource Server tự verify token, Auth Server phải chia sẻ Secret Key cho tất cả các service. Nếu 1 service bị hack, hacker có thể tự làm giả token Admin. | 🛡️ **Rất an toàn**: Auth Server giữ bí mật Private Key. Mọi Resource Server chỉ cần lấy Public Key để kiểm tra tính toàn vẹn. Hacker có lấy được Public Key cũng không thể ký giả mạo token. |
| **Khả năng mở rộng (Scalability)** | Các service phụ thuộc chặt vào việc chia sẻ chuỗi bí mật tĩnh qua cấu hình. | Các service tự động tải Public Key qua endpoint chuẩn `/.well-known/jwks.json` và tự động cập nhật khi xoay vòng khóa (Key Rotation). |

### 1.2. JSON Web Key Set (JWKS - RFC 7517) & Header `kid` (Key ID)
* Trong thực tế doanh nghiệp, các cặp khóa RSA thường xuyên được xoay vòng (Key Rotation) định kỳ 30 - 90 ngày.
* Khi xoay vòng khóa, JWKS sẽ chứa cả khóa cũ (để xác minh các token còn hiệu lực) và khóa mới (để ký các token mới phát hành).
* Header `kid` (Key ID) trên mỗi JWT giúp Resource Server biết chính xác Public Key nào trong danh sách JWKS được dùng để xác minh chữ ký của token đó.

### 1.3. Tại sao chọn Nimbus JOSE+JWT thay vì JJWT?
* **Nimbus JOSE+JWT** là thư viện chuẩn công nghiệp được chính **Gravitee AM**, **Spring Authorization Server**, và **Connect2ID** sử dụng.
* Hỗ trợ chuẩn xác các RFC: RFC 7515 (JWS), RFC 7517 (JWK/JWKS), RFC 7519 (JWT), RFC 7518 (JWA).
* Việc chuyển đổi trực tiếp giữa RSA `KeyPair` và `RSAKey` (JWK) chỉ tốn 1-2 dòng code mà không cần tự parse định dạng Base64URL thủ công.

---

## 2. Chi tiết các thành phần đã triển khai

### 2.1. Quản lý Khóa RSA: [`KeyManager.java`](file:///c:/Users/Admin/miniauth/src/main/java/com/miniauth/infrastructure/crypto/KeyManager.java)
- Lúc khởi động (`@PostConstruct`), sinh một cặp khóa RSA 2048-bit bằng `java.security.KeyPairGenerator`.
- Tạo `RSAKey` của Nimbus với định danh ngẫu nhiên `kid` (ví dụ: `miniauth-key-xxxx`).
- Tạo `JWSSigner` (dùng Private Key để ký).
- Phương thức `getPublicJwkSet()`: Gọi `rsaJwk.toPublicJWK()`, đảm bảo **loại bỏ hoàn toàn các thành phần nhạy cảm của Private Key** (`d`, `p`, `q`) trước khi xuất bản ra bên ngoài.

### 2.2. Dịch vụ Token: [`TokenService.java`](file:///c:/Users/Admin/miniauth/src/main/java/com/miniauth/core/port/TokenService.java) & [`NimbusJwtTokenService.java`](file:///c:/Users/Admin/miniauth/src/main/java/com/miniauth/infrastructure/crypto/NimbusJwtTokenService.java)
- `generateAccessToken`:
  - Ký bằng thuật toán `RS256`.
  - Claims tiêu chuẩn: `iss` (Issuer), `sub` (User ID), `aud` (Client ID), `exp` (1 giờ), `iat`, `jti`, `username`, `scope`.
- `generateIdToken`:
  - Token định danh chuẩn OpenID Connect.
  - Bổ sung các claim hồ sơ: `name`, `email`, `preferred_username`, `auth_time`, `nonce`.
- `validateAndExtractClaims`:
  - Parse JWT và dùng Public Key từ `KeyManager.createVerifier()` để kiểm tra chữ ký.
  - Kiểm tra tính hợp lệ về thời gian (`exp`) và nhà phát hành (`iss`).

### 2.3. Endpoints OIDC Discovery: [`WellKnownController.java`](file:///c:/Users/Admin/miniauth/src/main/java/com/miniauth/infrastructure/web/WellKnownController.java)
- `GET /.well-known/jwks.json`: Trả về JSON Web Key Set chứa Public Key.
- `GET /.well-known/openid-configuration`: Cung cấp Discovery Document chuẩn OIDC giúp các Client SDK tự nhận diện các endpoint và thuật toán được hỗ trợ.

### 2.4. Xử lý Lỗi chuẩn OAuth 2.0: [`OAuth2ExceptionHandler.java`](file:///c:/Users/Admin/miniauth/src/main/java/com/miniauth/infrastructure/web/OAuth2ExceptionHandler.java)
- Bắt mọi ngoại lệ `OAuth2Exception`.
- Trả về JSON đúng định dạng RFC 6749 Section 5.2:
  ```json
  {
    "error": "invalid_token",
    "error_description": "JWT signature verification failed"
  }
  ```

---

## 3. Tiêu chí Kiểm chứng (Verification)

Bộ Unit Test tại [`TokenServiceTest.java`](file:///c:/Users/Admin/miniauth/src/test/java/com/miniauth/infrastructure/crypto/TokenServiceTest.java) đã xác nhận:
1. `shouldGenerateAndVerifyAccessToken`: Token sinh ra có định dạng chuẩn 3 phần (`header.payload.signature`), được verify thành công với các claims chính xác.
2. `shouldGeneratePublicJwks`: Endpoint JWKS trả về đúng `kty: "RSA"`, `alg: "RS256"`, `use: "sig"` và **không chứa các trường Private Key** (`d`, `p`, `q`).
