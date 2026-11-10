# 🔄 Giai đoạn 4: Chuẩn OAuth 2.0 / OIDC Flow & Cơ chế PKCE

> **Trạng thái**:  **HOÀN THÀNH**  
> **Kiến thức kế thừa**: `gravitee-am-gateway-handler-oidc`

---

## 1. Bản chất Tiêu chuẩn: Tại sao lại chọn thiết kế này?

### 1.1. Luồng Authorization Code Flow + PKCE (RFC 7636)

```
+-------------+                     +-------------------+                   +------------------+
| Demo Client |                     |  MiniAuth Server  |                   | Resource Server  |
|  (Frontend) |                     |  (Authorization)  |                   |    (/userinfo)   |
+-------------+                     +-------------------+                   +------------------+
       |                                      |                                       |
       | 1. Sinh code_verifier &              |                                       |
       |    code_challenge = SHA256(verifier) |                                       |
       |                                      |                                       |
       | 2. GET /oauth/authorize?             |                                       |
       |    client_id=demo-client             |                                       |
       |    &redirect_uri=http://client/cb    |                                       |
       |    &response_type=code               |                                       |
       |    &code_challenge=xyz               |                                       |
       |    &code_challenge_method=S256       |                                       |
       |------------------------------------->|                                       |
       |                                      |                                       |
       | 3. Render Login & Consent Page       |                                       |
       |<-------------------------------------|                                       |
       |                                      |                                       |
       | 4. User nhập tài khoản, bấm Consent  |                                       |
       |------------------------------------->|                                       |
       |                                      |                                       |
       | 5. Redirect về Client kèm:           |                                       |
       |    http://client/cb?code=AUTH_CODE   |                                       |
       |<-------------------------------------|                                       |
       |                                      |                                       |
       | 6. POST /oauth/token                 |                                       |
       |    code=AUTH_CODE                    |                                       |
       |    &code_verifier=ORIGINAL_VERIFIER  |                                       |
       |------------------------------------->|                                       |
       |                                      |                                       |
       |                                      |-- 7. Validate SHA256(verifier)        |
       |                                      |      == code_challenge ban đầu?       |
       |                                      |                                       |
       | 8. Trả về access_token + id_token    |                                       |
       |<-------------------------------------|                                       |
       |                                                                              |
       | 9. GET /userinfo (Header: Bearer eyJhbGciOi...)                              |
       |----------------------------------------------------------------------------->|
       |                                                                              |
       | 10. Trả về hồ sơ JSON { "sub": "101", "name": "John Doe", "email": ... }     |
       |<-----------------------------------------------------------------------------|
```

### 1.2. Tại sao PKCE (Proof Key for Code Exchange) là BẮT BUỘC?
* **Điểm yếu của ứng dụng Web/SPA/Mobile (Public Client)**:
  - Một ứng dụng chạy trên trình duyệt (JavaScript) hoặc điện thoại không thể lưu giữ bí mật `client_secret` một cách an toàn (người dùng mở F12 hoặc decompile app là thấy mã secret).
  - Kẻ tấn công hoặc một ứng dụng độc hại trên máy có thể bắt trộm URL chứa mã `code` khi hệ thống redirect về trình duyệt.
* **Cơ chế phòng thủ của PKCE**:
  - Trình duyệt tự sinh chuỗi ngẫu nhiên bí mật `code_verifier` và chỉ gửi bản băm mã hóa `code_challenge = Base64URL(SHA-256(verifier))` lên Auth Server ở Bước 2.
  - Khi đổi code ở Bước 6, Client mới gửi chuỗi bí mật `code_verifier` ban đầu.
  - Kẻ nghe lén chỉ bắt được mã `code` nhưng **không biết chuỗi `code_verifier` nguyên bản**, do đó không thể đổi lấy Access Token.

---

## 2. Các bước triển khai chi tiết

### 2.1. Quản lý Authorization Code tạm thời
- Tạo `AuthorizationCodeService`:
  - Lưu trữ mã code trong In-memory Cache / ConcurrentHashMap (hỗ trợ TTL 5 phút).
  - Tự động hủy mã code ngay sau 1 lần sử dụng (One-time use).

### 2.2. Giao diện Login & Consent tập trung
- Sử dụng **Thymeleaf** (hoặc Reactive HTML template):
  - Trang đăng nhập: Nhập Username & Password, hiển thị thông báo lỗi khi sai.
  - Trang xác nhận quyền (Consent Page): Hiển thị tên Client muốn truy cập vào các Scopes (`openid`, `profile`, `email`) của người dùng.

### 2.3. Endpoints OIDC Core
1. `GET /oauth/authorize`:
   - Xác thực `client_id`, `redirect_uri`, `scope`.
   - Nếu chưa đăng nhập $\rightarrow$ Chuyển hướng sang màn hình Login.
   - Nếu đã đăng nhập $\rightarrow$ Chuyển sang màn hình Consent.
2. `POST /oauth/token`:
   - Hỗ trợ `grant_type=authorization_code`.
   - Xác thực `code_verifier` qua SHA-256 đối chiếu với `code_challenge`.
   - Gọi `TokenService` để sinh `access_token` và `id_token`.
3. `GET /userinfo`:
   - Đọc Bearer token từ Header `Authorization: Bearer <token>`.
   - Gọi `tokenService.validateAndExtractClaims(token)`.
   - Trả về JSON hồ sơ người dùng.

---

## 3. Tiêu chí Kiểm chứng (Verification)

1. Mở Postman hoặc Trình duyệt truy cập link `/oauth/authorize`:
   - Hiển thị màn hình Login $\rightarrow$ Nhập `john_doe` / `password123`.
   - Hiển thị màn hình Consent $\rightarrow$ Bấm Đồng ý.
   - Trình duyệt tự động chuyển hướng về URL callback kèm tham số `?code=...`.
2. Dùng Postman gửi `POST /oauth/token` kèm `code_verifier`:
   - Nhận về `access_token` và `id_token`.
3. Gửi `GET /userinfo` kèm Token:
   - Nhận về thông tin cá nhân của `john_doe`.
