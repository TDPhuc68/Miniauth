# 🚀 Giai đoạn 6: Ứng dụng Demo Client & Đóng gói Docker

> **Trạng thái**: 📋 **CHỜ XỬ LÝ (THEO KẾ HOẠCH)**  
> **Kiến thức thực chiến**: Tích hợp OAuth 2.0 / OIDC vào Single Page App (SPA) & Container hóa

---

## 1. Mục tiêu và Thiết kế Ứng dụng Demo Client

### 1.1. Tại sao phải có Demo Client thực tế?
Lý thuyết về OAuth 2.0 / OIDC thường rất trừu tượng. Cách tốt nhất để nắm vững bản chất là:
- Đóng vai trò là một **Ứng dụng bên thứ ba (Client Application)**.
- Tự tay kích hoạt luồng PKCE từ trình duyệt.
- Tận mắt chứng kiến trình duyệt chuyển hướng sang Auth Server, đăng nhập, cấp quyền và nhận kết quả hồ sơ người dùng.

### 1.2. Kiến trúc Ứng dụng Demo (`demo-client/`)
* **Nguyên tắc thiết kế**: Tinh gọn tối đa, sử dụng **HTML5 + Vanilla JavaScript + Web Crypto API** (Không cần cài đặt `node_modules` nặng nề).
* **Màn hình chính (`index.html`)**:
  - Giao diện đẹp, hiện đại.
  - Nút bấm: *"🔑 Đăng nhập với MiniAuth"*.
  - Khi bấm nút:
    1. Trình duyệt tự sinh chuỗi ngẫu nhiên `code_verifier` (43-128 ký tự).
    2. Dùng Web Crypto API (`window.crypto.subtle.digest('SHA-256', ...)`) để tính `code_challenge`.
    3. Lưu `code_verifier` vào `sessionStorage`.
    4. Chuyển hướng URL sang:
       ```
       http://localhost:8080/oauth/authorize?
         client_id=demo-client&
         redirect_uri=http://localhost:3000/callback.html&
         response_type=code&
         scope=openid profile email&
         code_challenge=xyz&
         code_challenge_method=S256
       ```
* **Màn hình Callback (`callback.html`)**:
  - Đọc mã `code` từ Query Parameter trên URL.
  - Lấy `code_verifier` từ `sessionStorage`.
  - Gửi request `POST http://localhost:8080/oauth/token` để lấy Access Token và ID Token.
  - Gửi request `GET http://localhost:8080/userinfo` kèm Bearer Token.
  - Hiển thị thẻ người dùng: *"Xin chào: John Doe (Email: john.doe@example.com) - Avatar - Token JWT chi tiết"*.

---

## 2. Đóng gói Hệ thống với Docker Compose

Tạo file `docker-compose.yml` để khởi chạy toàn bộ hệ sinh thái chỉ với 1 câu lệnh:

```yaml
version: '3.8'

services:
  # 1. Cơ sở dữ liệu PostgreSQL
  postgres:
    image: postgres:16-alpine
    container_name: miniauth-postgres
    environment:
      POSTGRES_DB: miniauth
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: secretpassword
    ports:
      - "5432:5432"

  # 2. MiniAuth Authorization Server
  miniauth-server:
    build: .
    container_name: miniauth-server
    depends_on:
      - postgres
    environment:
      SPRING_R2DBC_URL: r2dbc:postgresql://postgres:5432/miniauth
      SPRING_R2DBC_USERNAME: postgres
      SPRING_R2DBC_PASSWORD: secretpassword
    ports:
      - "8080:8080"

  # 3. Demo Client Web Application
  demo-client:
    image: nginx:alpine
    container_name: miniauth-demo-client
    volumes:
      - ./demo-client:/usr/share/nginx/html
    ports:
      - "3000:80"
```

---

## 3. Tiêu chí Nghiệm thu Toàn diện (End-to-End Verification)

1. Mở terminal gõ: `docker-compose up -d`.
2. Mở trình duyệt truy cập: `http://localhost:3000`.
3. Bấm nút **"Đăng nhập với MiniAuth"**.
4. Trình duyệt chuyển sang `http://localhost:8080` $\rightarrow$ Nhập `john_doe` / `password123`.
5. Màn hình xin quyền xuất hiện $\rightarrow$ Bấm **"Đồng ý cấp quyền"**.
6. Trình duyệt tự động chuyển về `http://localhost:3000/callback.html` $\rightarrow$ Hiển thị đầy đủ thông tin cá nhân của John Doe và chuỗi Access Token được ký bằng RS256.
7. Toàn bộ trải nghiệm hoàn tất trơn tru chỉ bằng click chuột.
