# 🌿 Quy chuẩn Phân nhánh và Quản lý Mã nguồn với Git (Git Branching Strategy)

> **Mục tiêu**: Chuẩn hóa quy trình làm việc chuyên nghiệp, giúp kiểm soát mã nguồn theo từng giai đoạn tính năng, dễ dàng review, rollback và hạn chế tối đa xung đột (conflict).

---

## 1. Mô hình Phân nhánh (Branching Model)

Dự án áp dụng mô hình **Feature Branch Workflow** (biến thể tinh gọn của Git Flow phù hợp cho các hệ thống hiện đại / CI/CD):

```
(main) ──●─────────●───────────────●───────────────────────● (Stable / Production)
          \       /               /                       /
(phase-3)  ●─────● (merged)      /                       /
                                /                       /
(phase-4)                      ●───────●───────●───────● (feat/phase-4-oauth2-oidc-pkce)
```

### 1.1. Nhánh Chính (`main`)
* **Tính chất**: Nhánh sản phẩm ổn định nhất (Production-ready).
* **Quy tắc**:
  - Không bao giờ commit code dang dở hoặc chưa qua kiểm thử trực tiếp lên `main`.
  - Chỉ nhận code sau khi một giai đoạn tính năng đã hoàn thành và vượt qua 100% các bài kiểm thử (`mvn clean test`).

### 1.2. Nhánh Tính năng (`feat/*`)
* **Quy ước đặt tên**:
  - `feat/phase-<số_thứ_tự>-<tên_ngắn_gọn>`
  - Ví dụ:
    - `feat/phase-4-oauth2-oidc-pkce`
    - `feat/phase-5-middleware-audit`
    - `feat/phase-6-demo-client-docker`
* **Mục đích**: Toàn bộ quá trình code, refactor, viết test cho giai đoạn đó sẽ diễn ra trên nhánh này mà không ảnh hưởng tới sự ổn định của nhánh `main`.

---

## 2. Quy trình Thực hiện cho Mỗi Giai đoạn (Standard Operating Procedure)

Mỗi khi bắt đầu một Giai đoạn mới, chúng ta sẽ tuân thủ nghiêm ngặt 4 bước:

### 🔹 Bước 1: Tạo nhánh tính năng mới từ `main`
```bash
git checkout main
git pull origin main
git checkout -b feat/phase-X-ten-tinh-nang
```

### 🔹 Bước 2: Phát triển và Commit theo chuẩn Conventional Commits
Quy ước tiền tố commit:
* `feat:` Tính năng mới (ví dụ: `feat(oidc): implement authorization code grant with pkce`)
* `fix:` Sửa lỗi (ví dụ: `fix(crypto): handle unhandled jose exception in key manager`)
* `test:` Bổ sung hoặc cập nhật unit/integration test
* `docs:` Cập nhật tài liệu, roadmap, diagrams

### 🔹 Bước 3: Nghiệm thu và Kiểm thử Toàn diện
Trước khi merge, bắt buộc chạy kiểm thử:
```bash
mvn clean test
```
Chỉ tiến hành khi thông báo hiển thị: **`BUILD SUCCESS`** (0 Failures, 0 Errors).

### 🔹 Bước 4: Hợp nhất vào `main` (Merge) & Đẩy lên Remote
```bash
git checkout main
git merge --no-ff feat/phase-X-ten-tinh-nang -m "merge: complete phase X into main"
git push origin main
```
*(Tham số `--no-ff` (No Fast-Forward) giúp lưu lại dấu mốc nhánh tính năng trên biểu đồ Git Graph để sau này dễ tra cứu lại lịch sử).*

---

## 3. Danh sách Nhánh theo Lộ trình Dự án

| Giai đoạn | Tên nhánh đề xuất | Nhánh đích | Trạng thái |
| :--- | :--- | :---: | :---: |
| **Giai đoạn 1 & 2** | `main` (Khởi tạo nền tảng) | `main` |  Đã merge |
| **Giai đoạn 3** | `main` / `feat/phase-3-auth-strategy-idp` | `main` |  Đã merge |
| **Giai đoạn 4** | `feat/phase-4-oauth2-oidc-pkce` | `main` | ⏳ **ĐANG THỰC HIỆN** |
| **Giai đoạn 5** | `feat/phase-5-middleware-audit` | `main` | 📋 Chờ xử lý |
| **Giai đoạn 6** | `feat/phase-6-demo-client-docker` | `main` | 📋 Chờ xử lý |
