# 📘 Frontend API Documentation — Document Management Module

> **Project:** QLDAPM  
> **Base URL:** `http://localhost:8080`  
> **Version:** 1.0.0  
> **Date:** 2026-03-09  
> **Author:** Backend Team

---

## 📌 Tổng quan

Module quản lý tài liệu cho phép người dùng **upload**, **download** (qua signed URL), và **xóa** tài liệu.  
Tài liệu được lưu trữ trên **Supabase Storage (S3-compatible)** và metadata lưu trong **PostgreSQL**.  
Tất cả các API đều yêu cầu **JWT token** (xem [Authentication Module](./frontend-auth-api.md)).

---

## 🏗️ Kiến trúc tổng quan

```
┌─────────────┐    POST /api/documents (multipart/form-data)    ┌─────────────┐     ┌──────────────┐
│   Frontend  │ ──────────────────────────────────────────────► │   Backend   │ ──► │  Supabase S3 │
│             │ ◄────────────── BaseResponse (metadata) ──────── │             │     │   Storage    │
└─────────────┘                                                  └──────┬──────┘     └──────────────┘
       │                                                                │
       │  GET /api/documents/{id}/download-url                          │
       │ ──────────────────────────────────────────────────────────────► │
       │ ◄─────────── BaseResponse (signed URL, 15 phút) ───────────── │
       │                                                                │
       │  Mở signed URL trên trình duyệt → Tải file trực tiếp từ S3    │
       └────────────────────────────────────────────────────────────────►│
```

---

## 📡 API Endpoints

### Tất cả các endpoint đều cần header:

```
Authorization: Bearer <token>
```

### Response chung — `BaseResponse`

Tất cả API trả về cùng format:

```json
{
  "statusCode": 200,
  "message": "...",
  "data": { ... }
}
```

| Field        | Type    | Mô tả                                         |
|-------------|---------|------------------------------------------------|
| `statusCode` | int    | Mã trạng thái (200 = thành công, 400 = lỗi)   |
| `message`    | String | Thông báo kết quả                              |
| `data`       | Object | Dữ liệu trả về (hoặc `null` nếu không có)     |

---

### 1. Upload tài liệu

| Thông tin         | Chi tiết                                   |
|------------------|---------------------------------------------|
| **URL**          | `POST /api/documents`                       |
| **Auth**         | ✅ Yêu cầu Bearer token                    |
| **Content-Type** | `multipart/form-data`                       |

#### ➤ Request

Gửi file dưới dạng `multipart/form-data` với key là `file`.

| Field  | Type           | Required | Mô tả                                |
|-------|----------------|----------|---------------------------------------|
| `file` | MultipartFile | ✅ Yes   | File cần upload                       |

#### ⚠️ Ràng buộc file (Validation)

| Ràng buộc          | Giá trị                                          |
|--------------------|--------------------------------------------------|
| **Định dạng**       | Chỉ chấp nhận: `pdf`, `doc`, `docx`             |
| **Kích thước tối đa** | 10 MB (10,485,760 bytes)                       |
| **Content-Type**    | `application/pdf`, `application/msword`, `application/vnd.openxmlformats-officedocument.wordprocessingml.document` |
| **Kiểm tra nội dung** | Backend xác minh magic bytes của file (chống đổi đuôi file) |

#### ✅ Response thành công — `200 OK`

```json
{
  "statusCode": 200,
  "message": "Document uploaded successfully",
  "data": {
    "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "originalName": "report.pdf",
    "contentType": "application/pdf",
    "size": 204800
  }
}
```

| Field trong `data` | Type   | Mô tả                                      |
|-------------------|--------|----------------------------------------------|
| `id`              | UUID   | ID của tài liệu (dùng cho download/delete)  |
| `originalName`    | String | Tên file gốc                                |
| `contentType`     | String | MIME type của file                           |
| `size`            | Long   | Kích thước file (bytes)                      |

#### ❌ Response lỗi

**File rỗng:**
```json
{
  "statusCode": 400,
  "message": "File is empty",
  "data": null
}
```

**File quá lớn:**
```json
{
  "statusCode": 400,
  "message": "File exceeds max size",
  "data": null
}
```

**Sai định dạng:**
```json
{
  "statusCode": 400,
  "message": "Only pdf, doc, docx are allowed",
  "data": null
}
```

**Nội dung file không khớp đuôi file:**
```json
{
  "statusCode": 400,
  "message": "File content is not a valid PDF",
  "data": null
}
```

---

### 2. Lấy URL tải file (Signed Download URL)

| Thông tin         | Chi tiết                                        |
|------------------|-------------------------------------------------|
| **URL**          | `GET /api/documents/{id}/download-url`          |
| **Auth**         | ✅ Yêu cầu Bearer token                        |
| **Content-Type** | Không cần (GET request)                          |

#### ➤ Path Parameters

| Field | Type | Required | Mô tả                                     |
|-------|------|----------|--------------------------------------------|
| `id`  | UUID | ✅ Yes   | ID của tài liệu (nhận được khi upload)    |

#### ✅ Response thành công — `200 OK`

```json
{
  "statusCode": 200,
  "message": "Download URL generated successfully",
  "data": {
    "url": "https://sxiyrycwruzxwpqehdmo.storage.supabase.co/storage/v1/s3/documents/users/1/abc123.pdf?X-Amz-Algorithm=..."
  }
}
```

| Field trong `data` | Type   | Mô tả                                               |
|-------------------|--------|-------------------------------------------------------|
| `url`             | String | Signed URL để tải file, **hết hạn sau 15 phút**      |

> ⚠️ **Lưu ý:** URL có hiệu lực trong **15 phút**. Sau đó cần gọi lại API để lấy URL mới.

#### ❌ Response lỗi

**Tài liệu không tồn tại hoặc không thuộc user hiện tại:**
```json
{
  "statusCode": 400,
  "message": "Document not found",
  "data": null
}
```

---

### 3. Xóa tài liệu

| Thông tin         | Chi tiết                           |
|------------------|------------------------------------|
| **URL**          | `DELETE /api/documents/{id}`       |
| **Auth**         | ✅ Yêu cầu Bearer token           |
| **Content-Type** | Không cần (DELETE request)          |

#### ➤ Path Parameters

| Field | Type | Required | Mô tả                                     |
|-------|------|----------|--------------------------------------------|
| `id`  | UUID | ✅ Yes   | ID của tài liệu cần xóa                   |

#### ✅ Response thành công — `200 OK`

```json
{
  "statusCode": 200,
  "message": "Document deleted successfully",
  "data": null
}
```

#### ❌ Response lỗi

**Tài liệu không tồn tại hoặc không thuộc user hiện tại:**
```json
{
  "statusCode": 400,
  "message": "Document not found",
  "data": null
}
```

---

## 🧪 Hướng dẫn test trên Postman

### Bước 0: Lấy JWT Token

1. Gửi request đăng nhập:
   - **Method:** `POST`
   - **URL:** `http://localhost:8080/api/auth/login`
   - **Body** → `raw` → `JSON`:
     ```json
     {
       "username": "johndoe",
       "password": "secret123"
     }
     ```
2. Copy giá trị `token` từ response.

### Bước 1: Upload tài liệu

1. Tạo request mới trong Postman:
   - **Method:** `POST`
   - **URL:** `http://localhost:8080/api/documents`
2. Tab **Authorization**:
   - Type: `Bearer Token`
   - Token: `<paste token ở bước 0>`
3. Tab **Body**:
   - Chọn **form-data**
   - Thêm key: `file`
   - Chuyển type sang **File** (click dropdown bên trái key)
   - Chọn file PDF/DOC/DOCX từ máy (≤ 10MB)
4. Click **Send**
5. Response thành công sẽ trả về `id` — **lưu lại giá trị này** để test các bước tiếp theo.

> 📸 **Lưu ý Postman:** Khi chọn `form-data` và type `File`, Postman tự động set `Content-Type: multipart/form-data`. **Không cần** set thêm header `Content-Type` thủ công.

### Bước 2: Lấy Download URL

1. Tạo request mới:
   - **Method:** `GET`
   - **URL:** `http://localhost:8080/api/documents/{id}/download-url`
   - Thay `{id}` bằng UUID nhận được ở bước 1 (ví dụ: `http://localhost:8080/api/documents/a1b2c3d4-e5f6-7890-abcd-ef1234567890/download-url`)
2. Tab **Authorization**:
   - Type: `Bearer Token`
   - Token: `<paste token>`
3. Click **Send**
4. Copy URL từ `data.url` trong response
5. **Mở URL đó trên trình duyệt** → File sẽ được tải xuống tự động

### Bước 3: Xóa tài liệu

1. Tạo request mới:
   - **Method:** `DELETE`
   - **URL:** `http://localhost:8080/api/documents/{id}`
   - Thay `{id}` bằng UUID của tài liệu cần xóa
2. Tab **Authorization**:
   - Type: `Bearer Token`
   - Token: `<paste token>`
3. Click **Send**
4. Response thành công: `"Document deleted successfully"`

### 🔴 Test các trường hợp lỗi

| Test case                        | Cách test                                            | Expected                              |
|---------------------------------|------------------------------------------------------|---------------------------------------|
| Upload file rỗng                | Gửi POST không có file                               | `400` — "File is empty"              |
| Upload file quá 10MB            | Chọn file > 10MB                                     | `400` — "File exceeds max size"      |
| Upload file .txt                | Chọn file text                                       | `400` — "Only pdf, doc, docx are allowed" |
| Upload file đổi đuôi giả       | Đổi tên file .txt thành .pdf rồi upload             | `400` — "File content is not a valid PDF" |
| Download tài liệu không tồn tại | GET với UUID ngẫu nhiên                             | `400` — "Document not found"         |
| Download tài liệu người khác   | Login user A, dùng document ID của user B            | `400` — "Document not found"         |
| Xóa tài liệu không tồn tại    | DELETE với UUID ngẫu nhiên                           | `400` — "Document not found"         |
| Không có token                  | Gửi request không có Authorization header            | `401` hoặc `403`                     |

---

## 📦 Ví dụ code tích hợp cho Frontend (React + Axios + TypeScript)

### Service Layer

```typescript
// documentService.ts
import axios from 'axios';

const BASE_URL = 'http://localhost:8080/api/documents';

// Lấy token từ localStorage
const getAuthHeader = () => ({
  Authorization: `Bearer ${localStorage.getItem('token')}`,
});

// ===== Interfaces =====

interface BaseResponse<T = any> {
  statusCode: number;
  message: string;
  data: T;
}

interface DocumentUploadData {
  id: string;
  originalName: string;
  contentType: string;
  size: number;
}

interface DownloadUrlData {
  url: string;
}

// ===== API Calls =====

/**
 * Upload tài liệu (pdf, doc, docx — tối đa 10MB)
 */
export const uploadDocument = async (file: File): Promise<BaseResponse<DocumentUploadData>> => {
  const formData = new FormData();
  formData.append('file', file);

  const response = await axios.post<BaseResponse<DocumentUploadData>>(BASE_URL, formData, {
    headers: {
      ...getAuthHeader(),
      'Content-Type': 'multipart/form-data',
    },
  });
  return response.data;
};

/**
 * Lấy signed URL để tải file (hết hạn sau 15 phút)
 */
export const getDownloadUrl = async (documentId: string): Promise<BaseResponse<DownloadUrlData>> => {
  const response = await axios.get<BaseResponse<DownloadUrlData>>(
    `${BASE_URL}/${documentId}/download-url`,
    { headers: getAuthHeader() }
  );
  return response.data;
};

/**
 * Xóa tài liệu
 */
export const deleteDocument = async (documentId: string): Promise<BaseResponse<null>> => {
  const response = await axios.delete<BaseResponse<null>>(
    `${BASE_URL}/${documentId}`,
    { headers: getAuthHeader() }
  );
  return response.data;
};
```

### Component ví dụ — Upload

```tsx
// DocumentUpload.tsx
import React, { useState } from 'react';
import { uploadDocument } from './documentService';

const ALLOWED_TYPES = [
  'application/pdf',
  'application/msword',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
];
const MAX_SIZE = 10 * 1024 * 1024; // 10MB

const DocumentUpload: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState('');

  const handleUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    // Validate phía client trước khi gửi
    if (!ALLOWED_TYPES.includes(file.type)) {
      setMessage('Chỉ chấp nhận file PDF, DOC, DOCX');
      return;
    }
    if (file.size > MAX_SIZE) {
      setMessage('File không được vượt quá 10MB');
      return;
    }

    setLoading(true);
    try {
      const result = await uploadDocument(file);
      if (result.statusCode === 200) {
        setMessage(`Upload thành công: ${result.data.originalName}`);
        // Lưu result.data.id để dùng cho download/delete
      } else {
        setMessage(`Lỗi: ${result.message}`);
      }
    } catch (error: any) {
      setMessage(error.response?.data?.message || 'Upload thất bại');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      <input
        type="file"
        accept=".pdf,.doc,.docx"
        onChange={handleUpload}
        disabled={loading}
      />
      {loading && <p>Đang upload...</p>}
      {message && <p>{message}</p>}
    </div>
  );
};

export default DocumentUpload;
```

### Component ví dụ — Download

```tsx
// DocumentDownload.tsx
import React from 'react';
import { getDownloadUrl } from './documentService';

interface Props {
  documentId: string;
  fileName: string;
}

const DocumentDownload: React.FC<Props> = ({ documentId, fileName }) => {
  const handleDownload = async () => {
    try {
      const result = await getDownloadUrl(documentId);
      if (result.statusCode === 200) {
        // Mở signed URL → trình duyệt tự tải file
        window.open(result.data.url, '_blank');
      } else {
        alert(`Lỗi: ${result.message}`);
      }
    } catch (error: any) {
      alert(error.response?.data?.message || 'Không thể tải file');
    }
  };

  return (
    <button onClick={handleDownload}>
      📥 Tải {fileName}
    </button>
  );
};

export default DocumentDownload;
```

---

## 🔄 Luồng xử lý gợi ý (Recommended Flow)

### Upload

```
1. User chọn file → Frontend validate (type + size) trước khi gửi
2. POST /api/documents (multipart/form-data)
3. Thành công (statusCode=200):
   - Hiển thị thông tin file (tên, kích thước)
   - Lưu document ID để dùng cho download/delete
4. Thất bại (statusCode=400):
   - Hiển thị message lỗi cho user
```

### Download

```
1. User click nút "Tải xuống"
2. GET /api/documents/{id}/download-url
3. Thành công (statusCode=200):
   - Mở data.url bằng window.open() hoặc tạo thẻ <a> tự click
   - File tự động tải xuống
4. Thất bại (statusCode=400):
   - Hiển thị thông báo lỗi
```

### Delete

```
1. User click nút "Xóa" → Hiện confirm dialog
2. DELETE /api/documents/{id}
3. Thành công (statusCode=200):
   - Xóa tài liệu khỏi danh sách hiển thị
   - Hiện thông báo "Đã xóa thành công"
4. Thất bại (statusCode=400):
   - Hiển thị thông báo lỗi
```

---

## 📋 Validation phía Frontend (khuyến nghị)

Validate trước khi gửi request để cải thiện trải nghiệm người dùng:

| Ràng buộc            | Rule                                                                 | Thông báo lỗi                             |
|---------------------|----------------------------------------------------------------------|-------------------------------------------|
| File bắt buộc        | File không được rỗng                                                 | "Vui lòng chọn file"                     |
| Định dạng file       | Chỉ `.pdf`, `.doc`, `.docx`                                          | "Chỉ chấp nhận file PDF, DOC, DOCX"      |
| Kích thước file      | ≤ 10 MB                                                              | "File không được vượt quá 10MB"           |
| MIME type            | `application/pdf`, `application/msword`, `application/vnd.openxmlformats-officedocument.wordprocessingml.document` | "Loại file không hợp lệ" |

> 💡 **Tip:** Sử dụng thuộc tính `accept=".pdf,.doc,.docx"` trên thẻ `<input type="file">` để giới hạn file picker của trình duyệt.

---

## 🛡️ Bảo mật

| Tính năng                     | Mô tả                                                                 |
|------------------------------|-------------------------------------------------------------------------|
| **Owner-based access**       | User chỉ có thể download/xóa tài liệu của chính mình                  |
| **Signed URL**               | URL tải file có thời hạn 15 phút, không thể dùng lại sau khi hết hạn  |
| **Magic bytes validation**   | Backend kiểm tra nội dung thực sự của file, chống giả mạo đuôi file   |
| **File extension whitelist** | Chỉ chấp nhận pdf, doc, docx                                           |
| **Content-Type validation**  | Kiểm tra MIME type phải khớp với định dạng cho phép                    |
| **Max file size**            | Giới hạn 10MB ở cả Spring Multipart config và business logic           |

---

## ❓ FAQ

**Q: Signed URL hết hạn thì làm gì?**  
A: Gọi lại `GET /api/documents/{id}/download-url` để lấy URL mới. URL có hiệu lực 15 phút.

**Q: User A có thể tải file của User B không?**  
A: Không. API kiểm tra `ownerId` — chỉ owner mới có thể truy cập tài liệu của mình.

**Q: Có thể upload nhiều file cùng lúc không?**  
A: Hiện tại mỗi request chỉ upload 1 file. Frontend có thể gửi nhiều request song song.

**Q: File được lưu ở đâu?**  
A: File lưu trên Supabase Storage (S3-compatible). Metadata (tên, size, owner...) lưu trong PostgreSQL.

**Q: Có thể upload file .txt, .jpg, .png không?**  
A: Không. Chỉ chấp nhận `.pdf`, `.doc`, `.docx`.

**Q: Khi xóa tài liệu thì file trên storage có bị xóa không?**  
A: Có. API xóa cả file trên S3 lẫn metadata trong database.

---

## 📞 Liên hệ

Nếu có thắc mắc về API, vui lòng liên hệ **Backend Team**.

