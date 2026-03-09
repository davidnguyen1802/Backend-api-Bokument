# 📘 Frontend API Documentation — Authentication Module

> **Project:** QLDAPM  
> **Base URL:** `http://localhost:8080`  
> **Version:** 1.0.0  
> **Date:** 2026-02-26  
> **Author:** Backend Team

---

## 📌 Tổng quan

Backend sử dụng **Spring Boot + Spring Security + JWT (JSON Web Token)**.  
Tất cả các API xác thực đều nằm dưới prefix `/api/auth`.  
Mọi response (kể cả lỗi) đều trả về HTTP status `200 OK` với cấu trúc JSON thống nhất dạng:

```json
{
  "statusCode": 200,
  "message": "Registration successful",
  "data": "jwt-token-or-other-data"
}
```

Trong đó:

- `statusCode`: mã trạng thái nghiệp vụ (200, 400, 401, 404, ...)  
- `message`: mô tả kết quả xử lý (thành công / lỗi)  
- `data`: dữ liệu trả về, với auth sẽ là **JWT token dạng String**

Sau khi đăng nhập hoặc đăng ký thành công (`statusCode = 200`), frontend nhận được một **JWT token** trong field `data` và cần lưu lại để gửi kèm trong các request cần xác thực.

---

## 🔐 Cơ chế xác thực (Authentication Flow)

```
┌─────────────┐         POST /api/auth/register hoặc /api/auth/login         ┌─────────────┐
│   Frontend  │ ──────────────────────────────────────────────────────────► │   Backend   │
│             │ ◄──────── { statusCode, message, data (JWT) } ───────────── │             │
└─────────────┘                                                               └─────────────┘
       │
       │  Lưu token vào localStorage / sessionStorage / cookie
       │
       ▼
┌─────────────┐   Request có header: Authorization: Bearer <token>  ┌─────────────┐
│   Frontend  │ ─────────────────────────────────────────────────► │   Backend   │
│             │ ◄──────────────────── Response data ─────────────── │             │
└─────────────┘                                                      └─────────────┘
```

---

## 📡 API Endpoints

### 1. Đăng ký — Register

| Thông tin    | Chi tiết                      |
|-------------|-------------------------------|
| **URL**     | `POST /api/auth/register`     |
| **Auth**    | ❌ Không yêu cầu token        |
| **Content-Type** | `application/json`       |

#### ➤ Request Body

```json
{
  "username": "johndoe",
  "email": "johndoe@example.com",
  "password": "secret123"
}
```

| Field      | Type   | Required | Validation                              |
|-----------|--------|----------|-----------------------------------------|
| `username` | String | ✅ Yes   | Không được để trống, độ dài 3–50 ký tự  |
| `email`    | String | ✅ Yes   | Phải đúng định dạng email               |
| `password` | String | ✅ Yes   | Không được để trống, tối thiểu 6 ký tự  |

#### ✅ Response thành công — HTTP `200 OK`, `statusCode = 200`

```json
{
  "statusCode": 200,
  "message": "Registration successful",
  "data": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

| Field         | Type   | Mô tả                                      |
|--------------|--------|---------------------------------------------|
| `statusCode` | Number | 200 nếu đăng ký thành công                  |
| `message`    | String | Thông báo `"Registration successful"`       |
| `data`       | String | JWT token dùng để xác thực các request sau  |

#### ❌ Response lỗi

> Lưu ý: HTTP status vẫn là `200 OK`, nhưng `statusCode` trong body sẽ khác `200`.

- **Username đã tồn tại**  

  Khi username đã tồn tại, backend ném `RuntimeException("Username already exists")` và được xử lý chung:

  ```json
  {
    "statusCode": 400,
    "message": "Username already exists",
    "data": null
  }
  ```

- **Validation thất bại** (`@Valid + @Size/@Email/@NotBlank`)  

  Các lỗi validation được gom thành 1 chuỗi message:

  ```json
  {
    "statusCode": 400,
    "message": "username: Username must be between 3 and 50 characters, email: Email should be valid, password: Password must be at least 6 characters",
    "data": null
  }
  ```

---

### 2. Đăng nhập — Login

| Thông tin    | Chi tiết                      |
|-------------|-------------------------------|
| **URL**     | `POST /api/auth/login`        |
| **Auth**    | ❌ Không yêu cầu token        |
| **Content-Type** | `application/json`       |

#### ➤ Request Body

```json
{
  "username": "johndoe",
  "password": "secret123"
}
```

| Field      | Type   | Required | Validation              |
|-----------|--------|----------|--------------------------|
| `username` | String | ✅ Yes   | Không được để trống      |
| `password` | String | ✅ Yes   | Không được để trống      |

#### ✅ Response thành công — HTTP `200 OK`, `statusCode = 200`

```json
{
  "statusCode": 200,
  "message": "Login successful",
  "data": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

#### ❌ Response lỗi

> HTTP status vẫn là `200 OK`, phân biệt lỗi theo `statusCode` trong body.

- **Sai username hoặc password** (`BadCredentialsException`)  

  ```json
  {
    "statusCode": 401,
    "message": "Invalid username or password",
    "data": null
  }
  ```

- **User không tồn tại** (`RuntimeException("User not found")`)  

  ```json
  {
    "statusCode": 400,
    "message": "User not found",
    "data": null
  }
  ```

- **Validation thất bại** (`@NotBlank`)  

  ```json
  {
    "statusCode": 400,
    "message": "username: Username is required, password: Password is required",
    "data": null
  }
  ```

---

## 💾 Hướng dẫn lưu token phía Frontend

Sau khi đăng ký / đăng nhập thành công (`statusCode = 200`), frontend cần lấy JWT token từ field `data` để sử dụng cho các request tiếp theo.

### Ví dụ với JavaScript / TypeScript

```javascript
// Lưu token vào localStorage
localStorage.setItem('token', response.data);

// Đọc token
const token = localStorage.getItem('token');

// Xóa token (logout)
localStorage.removeItem('token');
```

> ⚠️ **Lưu ý bảo mật:** Với ứng dụng production, nên cân nhắc sử dụng `httpOnly cookie` thay vì `localStorage` để tránh tấn công XSS.

---

## 📤 Cách gửi token trong các request cần xác thực

Tất cả các API cần xác thực (ngoài `/api/auth/**`) phải kèm token vào header `Authorization`:

```
Authorization: Bearer <token>
```

### Ví dụ với Axios

```javascript
import axios from 'axios';

const api = axios.create({
  baseURL: 'http://localhost:8080',
});

// Interceptor tự động gắn token vào mỗi request
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

export default api;
```

### Ví dụ với Fetch API

```javascript
const response = await fetch('http://localhost:8080/api/some-protected-endpoint', {
  method: 'GET',
  headers: {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${localStorage.getItem('token')}`,
  },
});
```

---

## 🎨 Gợi ý cấu trúc Form cho Frontend

### Form Đăng ký (Register)

```
┌────────────────────────────────────┐
│          TẠO TÀI KHOẢN             │
├────────────────────────────────────┤
│  Username *                        │
│  ┌──────────────────────────────┐  │
│  │ Nhập tên đăng nhập           │  │
│  └──────────────────────────────┘  │
│  ℹ️ 3-50 ký tự                     │
│                                    │
│  Email *                           │
│  ┌──────────────────────────────┐  │
│  │ example@email.com            │  │
│  └──────────────────────────────┘  │
│                                    │
│  Mật khẩu *                        │
│  ┌──────────────────────────────┐  │
│  │ ••••••••                     │  │
│  └──────────────────────────────┘  │
│  ℹ️ Tối thiểu 6 ký tự              │
│                                    │
│  ┌──────────────────────────────┐  │
│  │       ĐĂNG KÝ               │  │
│  └──────────────────────────────┘  │
│                                    │
│  Đã có tài khoản? [Đăng nhập]      │
└────────────────────────────────────┘
```

### Form Đăng nhập (Login)

```
┌────────────────────────────────────┐
│            ĐĂNG NHẬP               │
├────────────────────────────────────┤
│  Username *                        │
│  ┌──────────────────────────────┐  │
│  │ Nhập tên đăng nhập           │  │
│  └──────────────────────────────┘  │
│                                    │
│  Mật khẩu *                        │
│  ┌──────────────────────────────┐  │
│  │ ••••••••                     │  │
│  └──────────────────────────────┘  │
│                                    │
│  ┌──────────────────────────────┐  │
│  │        ĐĂNG NHẬP            │  │
│  └──────────────────────────────┘  │
│                                    │
│  Chưa có tài khoản? [Đăng ký]      │
└────────────────────────────────────┘
```

---

## 🔄 Luồng xử lý gợi ý (Recommended Flow)

### Đăng ký

```
1. User nhập form → Frontend validate (trước khi gửi)
2. POST /api/auth/register
3. Thành công (200):
   - Lưu token vào localStorage
   - Redirect đến trang chủ / dashboard
4. Thất bại:
   - 400: Hiển thị lỗi validation bên cạnh từng field
   - 500 (username exists): Hiển thị "Tên đăng nhập đã tồn tại"
```

### Đăng nhập

```
1. User nhập form → Frontend validate (trước khi gửi)
2. POST /api/auth/login
3. Thành công (200):
   - Lưu token vào localStorage
   - Redirect đến trang chủ / dashboard
4. Thất bại:
   - 400: Hiển thị lỗi validation
   - 401: Hiển thị "Tên đăng nhập hoặc mật khẩu không đúng"
```

### Logout

```
1. User click Đăng xuất
2. Xóa token khỏi localStorage
3. Redirect về trang đăng nhập
```

---

## 📋 Validation phía Frontend (khuyến nghị)

Nên validate trước khi gửi request để cải thiện UX:

| Field      | Rule                                      | Thông báo lỗi                                   |
|-----------|-------------------------------------------|--------------------------------------------------|
| `username` | Required, 3 ≤ length ≤ 50               | "Tên đăng nhập phải từ 3 đến 50 ký tự"           |
| `email`    | Required, valid email format             | "Email không hợp lệ"                             |
| `password` | Required, length ≥ 6                    | "Mật khẩu phải có ít nhất 6 ký tự"               |

---

## ⏰ Thông tin JWT Token

| Thuộc tính     | Giá trị                      |
|--------------|-------------------------------|
| **Algorithm** | HS256                        |
| **Expiration**| 86400000 ms = **24 giờ**     |
| **Type**      | Bearer Token                 |

> Token hết hạn sau **24 giờ**. Frontend cần xử lý trường hợp token hết hạn (nhận `401 Unauthorized`) bằng cách redirect user về trang đăng nhập.

---

## 📦 Ví dụ code tích hợp hoàn chỉnh (React + Axios)

```typescript
// authService.ts
import axios from 'axios';

const BASE_URL = 'http://localhost:8080/api/auth';

interface LoginRequest {
  username: string;
  password: string;
}

interface RegisterRequest {
  username: string;
  email: string;
  password: string;
}

interface AuthResponse {
  token: string;
  type: string;
  username: string;
  email: string;
}

export const register = async (data: RegisterRequest): Promise<AuthResponse> => {
  const response = await axios.post<AuthResponse>(`${BASE_URL}/register`, data);
  return response.data;
};

export const login = async (data: LoginRequest): Promise<AuthResponse> => {
  const response = await axios.post<AuthResponse>(`${BASE_URL}/login`, data);
  return response.data;
};

export const saveAuth = (authData: AuthResponse) => {
  localStorage.setItem('token', authData.token);
  localStorage.setItem('username', authData.username);
  localStorage.setItem('email', authData.email);
};

export const logout = () => {
  localStorage.removeItem('token');
  localStorage.removeItem('username');
  localStorage.removeItem('email');
};

export const getToken = (): string | null => {
  return localStorage.getItem('token');
};

export const isAuthenticated = (): boolean => {
  return !!getToken();
};
```

---

## ❓ FAQ

**Q: Token được gửi như thế nào?**  
A: Gửi trong HTTP Header: `Authorization: Bearer <token>`

**Q: Token hết hạn thì làm gì?**  
A: Server trả về `401 Unauthorized`. Frontend cần redirect về `/login`.

**Q: Có refresh token không?**  
A: Hiện tại chưa có. Token có thời hạn 24 giờ. Sẽ được bổ sung trong version sau.

**Q: CORS có được bật không?**  
A: Hiện tại CORS đang **disabled** ở backend. Nếu gặp lỗi CORS khi chạy frontend trên domain khác, hãy báo lại backend team để cấu hình.

**Q: API có dùng HTTPS không?**  
A: Môi trường dev dùng HTTP (`http://localhost:8080`). Production sẽ dùng HTTPS.

---

## 📞 Liên hệ

Nếu có thắc mắc về API, vui lòng liên hệ **Backend Team**.

