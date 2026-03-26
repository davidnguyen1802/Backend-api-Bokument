# Frontend API Documentation — Document Management Module

> **Project:** QLDAPM
> **Base URL:** `http://localhost:8080`
> **Version:** 2.0.0
> **Date:** 2026-03-09
> **Author:** Backend Team

---

## Tong quan

Module quan ly tai lieu cho phep nguoi dung **upload**, **xem danh sach**, **download** (qua signed URL), **doi ten**, **thay the file**, **an/hien**, va **xoa** tai lieu.
Tai lieu duoc luu tru tren **Supabase Storage (S3-compatible)** va metadata luu trong **PostgreSQL**.
Tat ca cac API deu yeu cau **JWT token** (xem [Authentication Module](./frontend-auth-api.md)).

### Phan quyen

| Hanh dong | Ai duoc phep |
|-----------|-------------|
| Xem tat ca tai lieu (`GET /api/documents`) | Moi user da dang nhap (chi thay tai lieu **visible**) |
| Xem tai lieu cua minh (`GET /api/documents/my`) | Moi user da dang nhap (thay ca an lan hien) |
| Xem chi tiet tai lieu (`GET /api/documents/{id}`) | Chi owner |
| Upload tai lieu | Moi user da dang nhap |
| Download tai lieu | Chi owner |
| Doi ten tai lieu | Chi owner |
| Thay the file tai lieu | Chi owner |
| An/hien tai lieu | Chi owner |
| Xoa tai lieu | Chi owner |

### Tinh nang an tai lieu (visibility)

- Moi tai lieu co truong `visible` (mac dinh `true` — cong khai)
- Owner co the **an tai lieu** bang cach set `visible = false`
- Tai lieu bi an **se khong hien** trong `GET /api/documents` (danh sach chung)
- Tai lieu bi an **van hien** trong `GET /api/documents/my` (danh sach cua owner)
- Owner van co the download/sua/xoa tai lieu bi an binh thuong

---

## Kien truc tong quan

```
                          GET /api/documents (tat ca)
                          GET /api/documents/my (cua toi)
+-----------+             POST /api/documents (upload)          +-----------+     +--------------+
| Frontend  | --------------------------------------------------> | Backend  | --> | Supabase S3  |
|           | <---- BaseResponse (metadata / list / url) -------- |          |     |   Storage    |
+-----------+             PUT /{id}/rename                       +-----+-----+     +--------------+
                          PUT /{id}/replace (upload moi)               |
                          DELETE /{id}                                 |
                          GET /{id}/download-url --> signed URL -------+
```

---

## API Endpoints

### Header bat buoc

```
Authorization: Bearer <token>
```

### Response chung — `BaseResponse`

```json
{
  "statusCode": 200,
  "message": "...",
  "data": { ... }
}
```

| Field        | Type    | Mo ta                                         |
|-------------|---------|------------------------------------------------|
| `statusCode` | int    | Ma trang thai (200 = thanh cong, 400 = loi)   |
| `message`    | String | Thong bao ket qua                              |
| `data`       | Object | Du lieu tra ve (hoac `null` neu khong co)     |

---

### 1. Lay tat ca tai lieu

| Thong tin         | Chi tiet                                   |
|------------------|---------------------------------------------|
| **URL**          | `GET /api/documents`                        |
| **Auth**         | Yeu cau Bearer token                        |
| **Mo ta**        | Lay danh sach tat ca tai lieu **cong khai** (visible = true) |

#### Response thanh cong — `200 OK`

```json
{
  "statusCode": 200,
  "message": "Documents retrieved successfully",
  "data": [
    {
      "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
      "originalName": "report.pdf",
      "contentType": "application/pdf",
      "size": 204800,
      "ownerId": 1,
      "createdAt": "2026-03-09T10:30:00Z"
    },
    {
      "id": "b2c3d4e5-f6a7-8901-bcde-f12345678901",
      "originalName": "proposal.docx",
      "contentType": "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
      "size": 102400,
      "ownerId": 2,
      "createdAt": "2026-03-08T14:20:00Z"
    }
  ]
}
```

| Field trong moi item | Type   | Mo ta                            |
|---------------------|--------|----------------------------------|
| `id`                | UUID   | ID tai lieu                      |
| `originalName`      | String | Ten file goc                     |
| `contentType`       | String | MIME type                        |
| `size`              | Long   | Kich thuoc file (bytes)          |
| `ownerId`           | int    | ID cua nguoi upload              |
| `createdAt`         | String | Thoi gian upload (ISO 8601)      |

---

### 2. Lay tai lieu cua toi

| Thong tin         | Chi tiet                                         |
|------------------|--------------------------------------------------|
| **URL**          | `GET /api/documents/my`                           |
| **Auth**         | Yeu cau Bearer token                              |
| **Mo ta**        | Lay danh sach tai lieu cua user dang dang nhap (ca an lan hien) |

#### Response thanh cong — `200 OK`

```json
{
  "statusCode": 200,
  "message": "Documents retrieved successfully",
  "data": [
    {
      "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
      "originalName": "report.pdf",
      "contentType": "application/pdf",
      "size": 204800,
      "visible": true,
      "createdAt": "2026-03-09T10:30:00Z"
    },
    {
      "id": "c3d4e5f6-a7b8-9012-cdef-123456789012",
      "originalName": "draft.docx",
      "contentType": "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
      "size": 51200,
      "visible": false,
      "createdAt": "2026-03-07T08:00:00Z"
    }
  ]
}
```

> Luu y: Response co them truong `visible`. Tai lieu an (`visible: false`) chi hien o day, khong hien trong `GET /api/documents`.

---

### 3. Lay chi tiet 1 tai lieu

| Thong tin         | Chi tiet                                   |
|------------------|---------------------------------------------|
| **URL**          | `GET /api/documents/{id}`                   |
| **Auth**         | Yeu cau Bearer token                        |
| **Mo ta**        | Chi owner moi xem duoc                      |

#### Path Parameters

| Field | Type | Required | Mo ta               |
|-------|------|----------|----------------------|
| `id`  | UUID | Yes      | ID cua tai lieu      |

#### Response thanh cong — `200 OK`

```json
{
  "statusCode": 200,
  "message": "Document retrieved successfully",
  "data": {
    "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "originalName": "report.pdf",
    "contentType": "application/pdf",
    "size": 204800,
    "visible": true,
    "createdAt": "2026-03-09T10:30:00Z"
  }
}
```

#### Response loi

```json
{
  "statusCode": 400,
  "message": "Document not found",
  "data": null
}
```

---

### 4. Upload tai lieu

| Thong tin         | Chi tiet                                   |
|------------------|---------------------------------------------|
| **URL**          | `POST /api/documents`                       |
| **Auth**         | Yeu cau Bearer token                        |
| **Content-Type** | `multipart/form-data`                       |

#### Request

Gui file duoi dang `multipart/form-data` voi key la `file`.

| Field  | Type           | Required | Mo ta              |
|-------|----------------|----------|--------------------|
| `file` | MultipartFile | Yes      | File can upload     |

#### Rang buoc file (Validation)

| Rang buoc             | Gia tri                                          |
|-----------------------|--------------------------------------------------|
| **Dinh dang**         | Chi chap nhan: `pdf`, `doc`, `docx`              |
| **Kich thuoc toi da** | 10 MB (10,485,760 bytes)                          |
| **Content-Type**      | `application/pdf`, `application/msword`, `application/vnd.openxmlformats-officedocument.wordprocessingml.document` |
| **Kiem tra noi dung** | Backend xac minh magic bytes cua file (chong doi duoi file) |

#### Response thanh cong — `200 OK`

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

#### Response loi

| Truong hop                    | message                              |
|-------------------------------|--------------------------------------|
| File rong                     | `"File is empty"`                    |
| File qua lon (> 10MB)        | `"File exceeds max size"`            |
| Sai dinh dang                 | `"Only pdf, doc, docx are allowed"`  |
| Noi dung khong khop duoi file | `"File content is not a valid PDF"`  |

---

### 5. Lay URL tai file (Signed Download URL)

| Thong tin         | Chi tiet                                        |
|------------------|-------------------------------------------------|
| **URL**          | `GET /api/documents/{id}/download-url`          |
| **Auth**         | Yeu cau Bearer token                             |
| **Mo ta**        | Chi owner moi lay duoc URL                       |

#### Path Parameters

| Field | Type | Required | Mo ta                                     |
|-------|------|----------|-------------------------------------------|
| `id`  | UUID | Yes      | ID cua tai lieu (nhan duoc khi upload)    |

#### Response thanh cong — `200 OK`

```json
{
  "statusCode": 200,
  "message": "Download URL generated successfully",
  "data": {
    "url": "https://xxx.supabase.co/storage/v1/s3/documents/users/1/abc123.pdf?X-Amz-Algorithm=..."
  }
}
```

| Field | Type   | Mo ta                                               |
|-------|--------|-----------------------------------------------------|
| `url` | String | Signed URL de tai file, **het han sau 15 phut**      |

> Luu y: URL co hieu luc trong **15 phut**. Sau do can goi lai API de lay URL moi.

#### Response loi

```json
{
  "statusCode": 400,
  "message": "Document not found",
  "data": null
}
```

---

### 6. Doi ten tai lieu

| Thong tin         | Chi tiet                                   |
|------------------|---------------------------------------------|
| **URL**          | `PUT /api/documents/{id}/rename`            |
| **Auth**         | Yeu cau Bearer token                        |
| **Content-Type** | `application/json`                          |
| **Mo ta**        | Chi owner moi doi ten duoc                  |

#### Path Parameters

| Field | Type | Required | Mo ta               |
|-------|------|----------|----------------------|
| `id`  | UUID | Yes      | ID cua tai lieu      |

#### Request Body

```json
{
  "newName": "bao-cao-moi"
}
```

| Field     | Type   | Required | Mo ta                                              |
|-----------|--------|----------|----------------------------------------------------|
| `newName` | String | Yes      | Ten moi (khong can extension, backend tu giu duoi cu) |

> Vi du: File goc la `report.pdf`, gui `"newName": "bao-cao-moi"` -> ket qua: `bao-cao-moi.pdf`

#### Response thanh cong — `200 OK`

```json
{
  "statusCode": 200,
  "message": "Document renamed successfully",
  "data": {
    "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "originalName": "bao-cao-moi.pdf"
  }
}
```

#### Response loi

| Truong hop            | message                    |
|-----------------------|----------------------------|
| Tai lieu khong ton tai | `"Document not found"`    |
| Ten rong              | `"New name must not be empty"` |

---

### 7. Thay the file tai lieu

| Thong tin         | Chi tiet                                            |
|------------------|------------------------------------------------------|
| **URL**          | `PUT /api/documents/{id}/replace`                    |
| **Auth**         | Yeu cau Bearer token                                 |
| **Content-Type** | `multipart/form-data`                                |
| **Mo ta**        | Upload file moi thay the file cu (chi owner)         |

#### Path Parameters

| Field | Type | Required | Mo ta               |
|-------|------|----------|----------------------|
| `id`  | UUID | Yes      | ID cua tai lieu      |

#### Request

| Field  | Type           | Required | Mo ta                |
|-------|----------------|----------|----------------------|
| `file` | MultipartFile | Yes      | File moi thay the    |

> Rang buoc file giong nhu upload (pdf/doc/docx, max 10MB, kiem tra magic bytes).

#### Response thanh cong — `200 OK`

```json
{
  "statusCode": 200,
  "message": "Document replaced successfully",
  "data": {
    "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "originalName": "report-v2.pdf",
    "contentType": "application/pdf",
    "size": 310000
  }
}
```

#### Response loi

| Truong hop            | message                              |
|-----------------------|--------------------------------------|
| Tai lieu khong ton tai | `"Document not found"`              |
| File rong             | `"File is empty"`                    |
| Sai dinh dang         | `"Only pdf, doc, docx are allowed"` |

---

### 8. Xoa tai lieu

| Thong tin         | Chi tiet                           |
|------------------|------------------------------------|
| **URL**          | `DELETE /api/documents/{id}`       |
| **Auth**         | Yeu cau Bearer token               |
| **Mo ta**        | Chi owner moi xoa duoc             |

#### Path Parameters

| Field | Type | Required | Mo ta                     |
|-------|------|----------|---------------------------|
| `id`  | UUID | Yes      | ID cua tai lieu can xoa   |

#### Response thanh cong — `200 OK`

```json
{
  "statusCode": 200,
  "message": "Document deleted successfully",
  "data": null
}
```

#### Response loi

```json
{
  "statusCode": 400,
  "message": "Document not found",
  "data": null
}
```

---

### 9. An/hien tai lieu (Toggle Visibility)

| Thong tin         | Chi tiet                                        |
|------------------|-------------------------------------------------|
| **URL**          | `PUT /api/documents/{id}/visibility`            |
| **Auth**         | Yeu cau Bearer token                             |
| **Content-Type** | `application/json`                               |
| **Mo ta**        | Chi owner moi bat/tat an tai lieu                |

#### Path Parameters

| Field | Type | Required | Mo ta               |
|-------|------|----------|----------------------|
| `id`  | UUID | Yes      | ID cua tai lieu      |

#### Request Body

```json
{
  "visible": false
}
```

| Field     | Type    | Required | Mo ta                                    |
|-----------|---------|----------|------------------------------------------|
| `visible` | boolean | Yes      | `true` = cong khai, `false` = an         |

#### Response thanh cong — `200 OK`

**An tai lieu:**
```json
{
  "statusCode": 200,
  "message": "Document is now hidden",
  "data": {
    "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "visible": false
  }
}
```

**Hien tai lieu:**
```json
{
  "statusCode": 200,
  "message": "Document is now visible",
  "data": {
    "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "visible": true
  }
}
```

#### Response loi

```json
{
  "statusCode": 400,
  "message": "Document not found",
  "data": null
}
```

---

## Bang tom tat API

| #  | Method   | URL                                | Quyen        | Mo ta                              |
|----|----------|------------------------------------|-------------|-------------------------------------|
| 1  | `GET`    | `/api/documents`                   | Moi user    | Lay tat ca tai lieu (chi visible)   |
| 2  | `GET`    | `/api/documents/my`                | Moi user    | Lay tai lieu cua toi (ca an/hien)   |
| 3  | `GET`    | `/api/documents/{id}`              | Chi owner   | Chi tiet 1 tai lieu                 |
| 4  | `POST`   | `/api/documents`                   | Moi user    | Upload tai lieu                     |
| 5  | `GET`    | `/api/documents/{id}/download-url` | Chi owner   | Lay signed URL download             |
| 6  | `PUT`    | `/api/documents/{id}/rename`       | Chi owner   | Doi ten tai lieu                    |
| 7  | `PUT`    | `/api/documents/{id}/replace`      | Chi owner   | Thay the file                       |
| 8  | `DELETE` | `/api/documents/{id}`              | Chi owner   | Xoa tai lieu                        |
| 9  | `PUT`    | `/api/documents/{id}/visibility`   | Chi owner   | An/hien tai lieu                    |

---

## Huong dan test tren Postman

### Buoc 0: Lay JWT Token

1. Gui request dang nhap:
   - **Method:** `POST`
   - **URL:** `http://localhost:8080/api/auth/login`
   - **Body** -> `raw` -> `JSON`:
     ```json
     {
       "username": "johndoe",
       "password": "secret123"
     }
     ```
2. Copy gia tri `token` tu response.

### Buoc 1: Xem tat ca tai lieu

- **Method:** `GET`
- **URL:** `http://localhost:8080/api/documents`
- **Authorization:** Bearer Token -> paste token

### Buoc 2: Xem tai lieu cua toi

- **Method:** `GET`
- **URL:** `http://localhost:8080/api/documents/my`
- **Authorization:** Bearer Token -> paste token

### Buoc 3: Upload tai lieu

1. **Method:** `POST`
2. **URL:** `http://localhost:8080/api/documents`
3. **Authorization:** Bearer Token -> paste token
4. **Body:** form-data -> key: `file`, type: **File** -> chon file PDF/DOC/DOCX (max 10MB)
5. Luu lai `id` tu response de test cac buoc tiep.

### Buoc 4: Xem chi tiet tai lieu

- **Method:** `GET`
- **URL:** `http://localhost:8080/api/documents/{id}`
- Thay `{id}` bang UUID nhan duoc o buoc 3

### Buoc 5: Lay Download URL

- **Method:** `GET`
- **URL:** `http://localhost:8080/api/documents/{id}/download-url`
- Copy URL tu `data.url` -> mo tren trinh duyet -> file tu tai xuong

### Buoc 6: Doi ten tai lieu

- **Method:** `PUT`
- **URL:** `http://localhost:8080/api/documents/{id}/rename`
- **Body:** raw -> JSON:
  ```json
  {
    "newName": "ten-moi"
  }
  ```

### Buoc 7: Thay the file

- **Method:** `PUT`
- **URL:** `http://localhost:8080/api/documents/{id}/replace`
- **Body:** form-data -> key: `file`, type: **File** -> chon file moi

### Buoc 8: An tai lieu

- **Method:** `PUT`
- **URL:** `http://localhost:8080/api/documents/{id}/visibility`
- **Body:** raw -> JSON:
  ```json
  {
    "visible": false
  }
  ```
- Sau khi an, goi lai `GET /api/documents` se khong thay tai lieu nay nua
- Goi `GET /api/documents/my` van thay (voi `visible: false`)

### Buoc 9: Xoa tai lieu

- **Method:** `DELETE`
- **URL:** `http://localhost:8080/api/documents/{id}`

### Test cac truong hop loi

| Test case                        | Cach test                                            | Expected                              |
|---------------------------------|------------------------------------------------------|---------------------------------------|
| Upload file rong                | Gui POST khong co file                               | `400` — "File is empty"              |
| Upload file qua 10MB            | Chon file > 10MB                                     | `400` — "File exceeds max size"      |
| Upload file .txt                | Chon file text                                       | `400` — "Only pdf, doc, docx are allowed" |
| Upload file doi duoi gia        | Doi ten file .txt thanh .pdf roi upload              | `400` — "File content is not a valid PDF" |
| Xem chi tiet tai lieu nguoi khac| Login user A, GET /api/documents/{id cua user B}     | `400` — "Document not found"         |
| Download tai lieu nguoi khac    | Login user A, dung document ID cua user B            | `400` — "Document not found"         |
| Doi ten tai lieu nguoi khac     | Login user A, PUT rename voi ID cua user B           | `400` — "Document not found"         |
| Thay the file nguoi khac        | Login user A, PUT replace voi ID cua user B          | `400` — "Document not found"         |
| Xoa tai lieu nguoi khac         | Login user A, DELETE voi ID cua user B               | `400` — "Document not found"         |
| Doi ten rong                    | Gui `{"newName": ""}`                                | `400` — "New name must not be empty" |
| An tai lieu nguoi khac          | Login user A, PUT visibility voi ID cua user B       | `400` — "Document not found"         |
| Tai lieu an khong hien o all    | An tai lieu, goi GET /api/documents                  | Tai lieu khong co trong danh sach    |
| Tai lieu an van hien o my       | An tai lieu, goi GET /api/documents/my               | Tai lieu van co voi `visible: false` |
| Khong co token                  | Gui request khong co Authorization header            | `401` hoac `403`                     |

---

## Vi du code tich hop cho Frontend (React + Axios + TypeScript)

### Interfaces

```typescript
interface BaseResponse<T = any> {
  statusCode: number;
  message: string;
  data: T;
}

interface DocumentItem {
  id: string;
  originalName: string;
  contentType: string;
  size: number;
  visible: boolean;
  createdAt: string;
}

interface DocumentItemAll {
  id: string;
  originalName: string;
  contentType: string;
  size: number;
  ownerId: number;
  createdAt: string;
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

interface RenameData {
  id: string;
  originalName: string;
}

interface VisibilityData {
  id: string;
  visible: boolean;
}
```

### Service Layer

```typescript
// documentService.ts
import axios from 'axios';

const BASE_URL = 'http://localhost:8080/api/documents';

const getAuthHeader = () => ({
  Authorization: `Bearer ${localStorage.getItem('token')}`,
});

/** Lay tat ca tai lieu */
export const getAllDocuments = async (): Promise<BaseResponse<DocumentItemAll[]>> => {
  const response = await axios.get(BASE_URL, { headers: getAuthHeader() });
  return response.data;
};

/** Lay tai lieu cua toi */
export const getMyDocuments = async (): Promise<BaseResponse<DocumentItem[]>> => {
  const response = await axios.get(`${BASE_URL}/my`, { headers: getAuthHeader() });
  return response.data;
};

/** Lay chi tiet 1 tai lieu */
export const getDocument = async (id: string): Promise<BaseResponse<DocumentItem>> => {
  const response = await axios.get(`${BASE_URL}/${id}`, { headers: getAuthHeader() });
  return response.data;
};

/** Upload tai lieu (pdf, doc, docx — toi da 10MB) */
export const uploadDocument = async (file: File): Promise<BaseResponse<DocumentUploadData>> => {
  const formData = new FormData();
  formData.append('file', file);
  const response = await axios.post(BASE_URL, formData, {
    headers: { ...getAuthHeader(), 'Content-Type': 'multipart/form-data' },
  });
  return response.data;
};

/** Lay signed URL de tai file (het han sau 15 phut) */
export const getDownloadUrl = async (id: string): Promise<BaseResponse<DownloadUrlData>> => {
  const response = await axios.get(`${BASE_URL}/${id}/download-url`, { headers: getAuthHeader() });
  return response.data;
};

/** Doi ten tai lieu */
export const renameDocument = async (id: string, newName: string): Promise<BaseResponse<RenameData>> => {
  const response = await axios.put(`${BASE_URL}/${id}/rename`, { newName }, { headers: getAuthHeader() });
  return response.data;
};

/** Thay the file tai lieu */
export const replaceDocument = async (id: string, file: File): Promise<BaseResponse<DocumentUploadData>> => {
  const formData = new FormData();
  formData.append('file', file);
  const response = await axios.put(`${BASE_URL}/${id}/replace`, formData, {
    headers: { ...getAuthHeader(), 'Content-Type': 'multipart/form-data' },
  });
  return response.data;
};

/** An/hien tai lieu */
export const toggleVisibility = async (id: string, visible: boolean): Promise<BaseResponse<VisibilityData>> => {
  const response = await axios.put(`${BASE_URL}/${id}/visibility`, { visible }, { headers: getAuthHeader() });
  return response.data;
};

/** Xoa tai lieu */
export const deleteDocument = async (id: string): Promise<BaseResponse<null>> => {
  const response = await axios.delete(`${BASE_URL}/${id}`, { headers: getAuthHeader() });
  return response.data;
};
```

---

## Validation phia Frontend (khuyen nghi)

Validate truoc khi gui request de cai thien trai nghiem nguoi dung:

| Rang buoc            | Rule                                                                 | Thong bao loi                             |
|---------------------|----------------------------------------------------------------------|-------------------------------------------|
| File bat buoc        | File khong duoc rong                                                 | "Vui long chon file"                     |
| Dinh dang file       | Chi `.pdf`, `.doc`, `.docx`                                          | "Chi chap nhan file PDF, DOC, DOCX"      |
| Kich thuoc file      | <= 10 MB                                                             | "File khong duoc vuot qua 10MB"           |
| MIME type            | `application/pdf`, `application/msword`, `application/vnd.openxmlformats-officedocument.wordprocessingml.document` | "Loai file khong hop le" |

> **Tip:** Su dung thuoc tinh `accept=".pdf,.doc,.docx"` tren the `<input type="file">` de gioi han file picker cua trinh duyet.

---

## Bao mat

| Tinh nang                     | Mo ta                                                                 |
|------------------------------|-------------------------------------------------------------------------|
| **Owner-based access**       | User chi co the xem chi tiet/download/sua/xoa tai lieu cua chinh minh   |
| **Visibility control**       | Owner co the an tai lieu — tai lieu an khong hien trong danh sach chung |
| **Public list**              | Moi user deu xem duoc danh sach tai lieu cong khai (visible = true)    |
| **Signed URL**               | URL tai file co thoi han 15 phut, khong the dung lai sau khi het han   |
| **Magic bytes validation**   | Backend kiem tra noi dung thuc su cua file, chong gia mao duoi file    |
| **File extension whitelist** | Chi chap nhan pdf, doc, docx                                           |
| **Content-Type validation**  | Kiem tra MIME type phai khop voi dinh dang cho phep                     |
| **Max file size**            | Gioi han 10MB o ca Spring Multipart config va business logic            |

---

## FAQ

**Q: Signed URL het han thi lam gi?**
A: Goi lai `GET /api/documents/{id}/download-url` de lay URL moi. URL co hieu luc 15 phut.

**Q: User A co the tai/sua/xoa file cua User B khong?**
A: Khong. API kiem tra `ownerId` — chi owner moi co the thao tac tai lieu cua minh. Nhung moi user deu xem duoc danh sach tat ca tai lieu.

**Q: Co the upload nhieu file cung luc khong?**
A: Hien tai moi request chi upload 1 file. Frontend co the gui nhieu request song song.

**Q: File duoc luu o dau?**
A: File luu tren Supabase Storage (S3-compatible). Metadata (ten, size, owner...) luu trong PostgreSQL.

**Q: Co the upload file .txt, .jpg, .png khong?**
A: Khong. Chi chap nhan `.pdf`, `.doc`, `.docx`.

**Q: Khi xoa tai lieu thi file tren storage co bi xoa khong?**
A: Co. API xoa ca file tren S3 lan metadata trong database.

**Q: Doi ten tai lieu co can gui kem extension khong?**
A: Khong can. Backend tu dong giu extension goc. Vi du: file goc la `report.pdf`, gui `"newName": "bao-cao"` -> ket qua: `bao-cao.pdf`.

**Q: Thay the file co thay doi ID khong?**
A: Khong. ID va URL giu nguyen, chi noi dung file va metadata (ten, size, content-type) duoc cap nhat.

**Q: An tai lieu thi nguoi khac con thay khong?**
A: Khong. Tai lieu bi an (`visible = false`) se khong hien trong `GET /api/documents`. Chi owner moi thay trong `GET /api/documents/my`.

**Q: Tai lieu moi upload mac dinh la an hay hien?**
A: Mac dinh la **hien** (`visible = true`). Owner can an thu cong bang `PUT /api/documents/{id}/visibility`.

---

## Lien he

Neu co thac mac ve API, vui long lien he **Backend Team**.
