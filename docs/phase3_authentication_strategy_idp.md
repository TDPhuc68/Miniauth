# 🔑 Giai đoạn 3: Tầng Xác thực đa nhà cung cấp (Strategy Pattern & IDP Registry)

> **Trạng thái**: ⏳ **ĐANG THỰC HIỆN / TIẾP THEO**  
> **Kiến thức kế thừa**: `gravitee-am-identityprovider` & `gravitee-am-plugins-handlers`

---

## 1. Bản chất Kiến trúc: Tại sao lại chọn thiết kế này?

### 1.1. Vấn đề của phương thức xác thực cố định (Hardcoded Authentication)
Trong nhiều ứng dụng nghiệp dư, logic kiểm tra đăng nhập thường được viết cứng vào một Service:
```java
// Anti-pattern: Viết cứng trong Service
if (type.equals("DB")) {
    // query database...
} else if (type.equals("LDAP")) {
    // kết nối ldap...
} else if (type.equals("GOOGLE")) {
    // gọi google api...
}
```
* **Nhược điểm**: Vi phạm nguyên lý **Open-Closed Principle (OCP)** trong SOLID. Mỗi khi muốn thêm một nhà cung cấp mới (như Facebook, Azure AD, SAML2, WebAuthn), bạn buộc phải chỉnh sửa vào mã nguồn lõi, tăng rủi ro gây lỗi hồi quy (Regression Bugs).

### 1.2. Giải pháp: Strategy Pattern & Plugin Registry (Kế thừa từ Gravitee AM)
* Gravitee AM xem mỗi nguồn người dùng là một **Identity Provider Plugin**.
* **Định nghĩa Interface Chiến lược (Strategy Contract)**:
  Mọi IDP đều phải tuân thủ một giao diện duy nhất: nhận vào yêu cầu xác thực (`AuthenticationRequest`) và trả về định danh người dùng phi phong tỏa (`Mono<User>`).
* **Registry (Bộ điều phối động)**:
  Tự động quét và gom toàn bộ các Beans triển khai `AuthenticationProvider` vào một Map (`Map<String, AuthenticationProvider>`). Khi có yêu cầu đăng nhập, hệ thống chỉ việc rút đúng Provider tương ứng để thi hành.
* **Lợi ích**: Muốn thêm một phương thức đăng nhập mới? Bạn chỉ cần tạo một class mới có `@Component implements AuthenticationProvider`. Toàn bộ hệ thống tự động nhận diện mà **không cần sửa một dòng code cũ nào**.

---

## 2. Các bước triển khai chi tiết

### 2.1. Cấu trúc Lớp (Class Diagram)

```
                 +-------------------------------+
                 |    AuthenticationProvider     |  (Interface Strategy)
                 +-------------------------------+
                 | + getProviderType(): String   |
                 | + authenticate(...): Mono<User>|
                 +-------------------------------+
                                 ^
                                 | implements
          +----------------------+----------------------+
          |                                             |
+-----------------------------------+   +------------------------------------+
|   DatabaseAuthenticationProvider  |   |   MockSocialAuthenticationProvider |
+-----------------------------------+   +------------------------------------+
| - UserRepository                  |   | - Giả lập đăng nhập qua Google     |
| - PasswordEncoder (BCrypt)        |   | - Tự động tạo user nếu chưa có     |
+-----------------------------------+   +------------------------------------+
                                 ^
                                 | Managed by
                 +-------------------------------+
                 | AuthenticationProviderRegistry|
                 +-------------------------------+
                 | - providers: Map<String, Prov>|
                 | + getProvider(type): Prov     |
                 +-------------------------------+
```

### 2.2. Chi tiết các file sẽ tạo

1. **`com.miniauth.core.model.AuthenticationRequest`**:
   - `username`, `password`, `providerType` (Mặc định: `"LOCAL"`), `additionalParams`.
2. **`com.miniauth.core.port.AuthenticationProvider`**:
   - Interface hợp đồng cho các nhà cung cấp định danh.
3. **`com.miniauth.infrastructure.idp.DatabaseAuthenticationProvider`**:
   - Kiểm tra user có tồn tại trong Database không.
   - Kiểm tra tài khoản có bị khóa không (`user.isLocked()`).
   - Sử dụng `PasswordEncoder.matches()` để so khớp mật khẩu đã hash bằng BCrypt.
4. **`com.miniauth.infrastructure.idp.MockSocialAuthenticationProvider`**:
   - Giả lập xác thực bên thứ ba (Google OAuth2).
   - Minh chứng tính đa dạng của Strategy Pattern.
5. **`com.miniauth.infrastructure.idp.AuthenticationProviderRegistry`**:
   - Tận dụng cơ chế Spring Dependency Injection để gom danh sách `List<AuthenticationProvider>`.
6. **`com.miniauth.core.service.AuthService`**:
   - Quản lý logic nghiệp vụ đăng nhập tập trung.
   - Xử lý tăng số lần đăng nhập sai (`failedAttempts`), tự động khóa tài khoản nếu sai quá ngưỡng quy định (`max-failed-attempts: 5`).

---

## 3. Tiêu chí Kiểm chứng (Verification)

1. Viết Unit Test cho `DatabaseAuthenticationProvider`:
   - Đăng nhập đúng mật khẩu $\rightarrow$ Trả về `Mono<User>` hợp lệ.
   - Đăng nhập sai mật khẩu $\rightarrow$ Ném lỗi `OAuth2Exception` ("invalid_grant", "Invalid username or password").
   - Đăng nhập khi tài khoản bị khóa $\rightarrow$ Ném lỗi `AccountLockedException`.
2. Viết Unit Test cho `AuthenticationProviderRegistry`:
   - Xác minh Registry nạp đầy đủ các providers `"LOCAL"` và `"MOCK_SOCIAL"`.
