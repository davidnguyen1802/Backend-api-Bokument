package com.qldapm_L01.backend_api.Controller;

import com.qldapm_L01.backend_api.Entity.Document;
import com.qldapm_L01.backend_api.Entity.User;
import com.qldapm_L01.backend_api.Exception.DataNotFoundException;
import com.qldapm_L01.backend_api.Payload.Response.BaseResponse;
import com.qldapm_L01.backend_api.Repository.UserRepository;
import com.qldapm_L01.backend_api.Service.DocumentStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentStorageService storageService;
    private final UserRepository userRepository;

    /**
     * [Public] Lấy danh sách tài liệu công khai, hỗ trợ tìm kiếm Full Text Search
     * và lọc theo tag.
     * GET /api/documents?q=từ
     * khóa&tags=Math,Physics&contentType=application/pdf&page=0&size=10
     */
    @GetMapping
    public ResponseEntity<?> getAllDocuments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String contentType,
            @RequestParam(required = false) java.util.List<String> tags) {
        Page<Document> docs = storageService.listVisible(page, size, q, contentType, tags);

        BaseResponse response = new BaseResponse();
        response.setStatusCode(200);
        response.setMessage("Documents retrieved successfully");
        response.setData(toPageData(docs, false));
        return ResponseEntity.ok(response);
    }

    /**
     * [Auth] Lấy kho tài liệu cá nhân của user đang đăng nhập (bao gồm cả tài liệu
     * ẩn).
     * GET /api/documents/my?q=...&visible=true&tags=...&page=0&size=20
     */
    @GetMapping("/my")
    public ResponseEntity<?> getMyDocuments(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean visible,
            @RequestParam(required = false) java.util.List<String> tags) {
        int ownerId = currentUserId(authentication);
        Page<Document> docs = storageService.listByOwner(ownerId, page, size, q, visible, tags);

        BaseResponse response = new BaseResponse();
        response.setStatusCode(200);
        response.setMessage("Documents retrieved successfully");
        response.setData(toPageData(docs, true));
        return ResponseEntity.ok(response);
    }

    /**
     * [Auth + Đóng góp] Cấp Pre-signed URL tải về 1 tài liệu (PBI-16).
     * Quota: số bài đã upload phải >= số bài đã tải về (tính cả lần này).
     * - 401: Chưa đăng nhập.
     * - 403: Đa đăng nhập nhưng hết quota (uploads <= downloads đang có).
     * GET /api/documents/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getDocument(
            @PathVariable UUID id,
            Authentication authentication) {
        Integer requesterId = currentUserIdOrNull(authentication);

        if (requesterId == null) {
            BaseResponse errorResponse = new BaseResponse();
            errorResponse.setStatusCode(401);
            errorResponse.setMessage("Khách chưa login: Yêu cầu Đăng nhập");
            return ResponseEntity.status(401).body(errorResponse);
        }

        // Quota check (uploads >= downloads) và ghi lượt tải được xử lý trong Service
        // với SERIALIZABLE transaction. Nếu không đủ quota, Service ném
        // AccessDeniedException -> 403.
        String url = storageService.createDownloadUrlForRead(requesterId, id);

        BaseResponse response = new BaseResponse();
        response.setStatusCode(200);
        response.setMessage("Document URL generated successfully");
        response.setData(Map.of("url", url));
        return ResponseEntity.ok(response);
    }

    /**
     * [Public] Xem thông tin metadata của 1 tài liệu (tên, loại, dung lượng,
    /**
     * [Public/Auth] Đọc từng trang của bản preview giống StuDocu.
     * Khách (chưa login) chỉ đọc được tối đa 50% số trang.
     * GET /api/documents/{id}/pages/{pageNumber}
     */
    @GetMapping("/{id}/pages/{pageNumber}")
    public ResponseEntity<?> getDocumentPage(
            @PathVariable UUID id,
            @PathVariable int pageNumber,
            Authentication authentication
    ) {
        Integer requesterId = currentUserIdOrNull(authentication);
        Document doc = storageService.getDocumentForRead(requesterId, id);

        if (!"READY".equals(doc.getProcessingStatus())) {
            BaseResponse errorResponse = new BaseResponse();
            errorResponse.setStatusCode(400);
            errorResponse.setMessage("Tài liệu chưa được xử lý xong để đọc trực tuyến. Đang ở trạng thái: " + doc.getProcessingStatus());
            return ResponseEntity.status(400).body(errorResponse);
        }
        
        if (pageNumber < 1 || pageNumber > doc.getPageCount()) {
            BaseResponse errorResponse = new BaseResponse();
            errorResponse.setStatusCode(400);
            errorResponse.setMessage("Trang không tồn tại");
            return ResponseEntity.status(400).body(errorResponse);
        }

        if (requesterId == null) {
            int maxAllowed = Math.max(1, (int) Math.ceil(doc.getPageCount() / 2.0));
            if (pageNumber > maxAllowed) {
                BaseResponse errorResponse = new BaseResponse();
                errorResponse.setStatusCode(401);
                errorResponse.setMessage("Tài liệu quá dài. Bạn cần Đăng nhập để đọc toàn bộ!");
                return ResponseEntity.status(401).body(errorResponse);
            }
        }

        String url = storageService.createPageUrl(doc, pageNumber);
        
        // Trả về 302 Redirect để trình duyệt tự mở ảnh (rất phù hợp đặt trong thẻ <img src="...">)
        return ResponseEntity.status(org.springframework.http.HttpStatus.FOUND)
                .location(java.net.URI.create(url))
                .build();
    }

    /**
     * [Public/Auth] Lấy thông tin chi tiết (metadata) của 1 tài liệu cụ thể (id, tên gốc, tags,
     * lượt tải, thời gian tải lên, chủ nhân, trạng thái xử lý trang,...
     * v.v...).
     * Nếu tài liệu bị ẩn, chỉ chủ nhân mới xem được.
     * GET /api/documents/{id}/metadata
     */
    @GetMapping("/{id}/metadata")
    public ResponseEntity<?> getDocumentMetadata(
            @PathVariable UUID id,
            Authentication authentication) {
        Integer requesterId = currentUserIdOrNull(authentication);
        Document doc = storageService.getDocumentForRead(requesterId, id);

        BaseResponse response = new BaseResponse();
        response.setStatusCode(200);
        response.setMessage("Document metadata retrieved successfully");
        Map<String, Object> data = new HashMap<>();
        data.put("id", doc.getId());
        data.put("originalName", doc.getOriginalName());
        data.put("contentType", doc.getContentType());
        data.put("size", doc.getSize());
        data.put("visible", doc.isVisible());
        data.put("ownerId", doc.getOwnerId());
        data.put("createdAt", doc.getCreatedAt());
        data.put("uploadedAt", doc.getUploadedAt());
        data.put("downloadCount", doc.getDownloadCount());
        data.put("pageCount", doc.getPageCount());
        data.put("processingStatus", doc.getProcessingStatus());
        response.setData(data);
        return ResponseEntity.ok(response);
    }

    /**
     * [Auth] Ẩn hoặc Hiện tài liệu của bản thân.
     * visible=true => Tài liệu Public, hiển thị cho mọi người tìm kiếm.
     * visible=false => Tài liệu Private, chỉ chủ nhân thấy trong /my.
     * PUT /api/documents/{id}/visibility Body: { "visible": false }
     */
    @PutMapping("/{id}/visibility")
    public ResponseEntity<?> toggleVisibility(
            @PathVariable UUID id,
            @RequestBody Map<String, Boolean> body,
            Authentication authentication) {
        int ownerId = currentUserId(authentication);
        boolean visible = body.getOrDefault("visible", true);
        Document updated = storageService.toggleVisibility(ownerId, id, visible);

        BaseResponse response = new BaseResponse();
        response.setStatusCode(200);
        response.setMessage(visible ? "Document is now visible" : "Document is now hidden");
        response.setData(Map.of(
                "id", updated.getId(),
                "visible", updated.isVisible()));
        return ResponseEntity.ok(response);
    }

    /**
     * [Auth] Upload file mới lên hệ thống.
     * POST /api/documents (multipart/form-data, key: "file")
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> upload(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) throws IOException {
        int ownerId = currentUserId(authentication);

        Document saved = storageService.upload(ownerId, file);

        BaseResponse response = new BaseResponse();
        response.setStatusCode(200);
        response.setMessage("Document uploaded successfully");
        response.setData(Map.of(
                "id", saved.getId(),
                "originalName", saved.getOriginalName(),
                "contentType", saved.getContentType(),
                "size", saved.getSize()));
        return ResponseEntity.ok(response);
    }

    /**
     * [Auth] Dành cho chủ nhân file tự lấy Pre-signed URL đọc/tải bài riêng của
     * mình.
     * Khác với GET /{id}: Không yêu cầu đóng góp PBI-16, chỉ cần là chủ nhân file.
     * GET /api/documents/{id}/download-url
     */
    @GetMapping("/{id}/download-url")
    public ResponseEntity<?> getDownloadUrl(
            @PathVariable UUID id,
            Authentication authentication) {
        int ownerId = currentUserId(authentication);
        String url = storageService.createDownloadUrl(ownerId, id);

        BaseResponse response = new BaseResponse();
        response.setStatusCode(200);
        response.setMessage("Download URL generated successfully");
        response.setData(Map.of("url", url));
        return ResponseEntity.ok(response);
    }

    /**
     * [Auth] Đổi tên hiển thị (originalName) của tài liệu. Extension gốc được tự
     * giữ lại.
     * PUT /api/documents/{id}/rename Body: { "newName": "Tên mới" }
     */
    @PutMapping("/{id}/rename")
    public ResponseEntity<?> rename(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body,
            Authentication authentication) {
        int ownerId = currentUserId(authentication);
        String newName = body.get("newName");
        Document updated = storageService.rename(ownerId, id, newName);

        BaseResponse response = new BaseResponse();
        response.setStatusCode(200);
        response.setMessage("Document renamed successfully");
        response.setData(Map.of(
                "id", updated.getId(),
                "originalName", updated.getOriginalName()));
        return ResponseEntity.ok(response);
    }

    /**
     * [Auth] Thay thế nội dung file bằng file mới, giữ nguyên ID, tag và các
     * metadata cũ.
     * PUT /api/documents/{id}/replace (multipart/form-data, key: "file")
     */
    @PutMapping(value = "/{id}/replace", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> replaceFile(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file,
            Authentication authentication) throws IOException {
        int ownerId = currentUserId(authentication);
        Document updated = storageService.replaceFile(ownerId, id, file);

        BaseResponse response = new BaseResponse();
        response.setStatusCode(200);
        response.setMessage("Document replaced successfully");
        response.setData(Map.of(
                "id", updated.getId(),
                "originalName", updated.getOriginalName(),
                "contentType", updated.getContentType(),
                "size", updated.getSize()));
        return ResponseEntity.ok(response);
    }

    /**
     * [Auth] Xóa vĩnh viễn tài liệu khỏi Database và S3 Storage.
     * DELETE /api/documents/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(
            @PathVariable UUID id,
            Authentication authentication) {
        int ownerId = currentUserId(authentication);
        storageService.delete(ownerId, id);

        BaseResponse response = new BaseResponse();
        response.setStatusCode(200);
        response.setMessage("Document deleted successfully");
        response.setData(null);
        return ResponseEntity.ok(response);
    }

    private int currentUserId(Authentication authentication) {
        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new DataNotFoundException("User not found"));
        return user.getId();
    }

    private Integer currentUserIdOrNull(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return null;
        }

        String username = authentication.getName();
        return userRepository.findByUsername(username)
                .map(User::getId)
                .orElse(null);
    }

    private Map<String, Object> toPageData(Page<Document> docs, boolean includeVisibility) {
        return Map.of(
                "items", docs.getContent().stream().map(doc -> toSummary(doc, includeVisibility)).toList(),
                "page", docs.getNumber(),
                "size", docs.getSize(),
                "totalItems", docs.getTotalElements(),
                "totalPages", docs.getTotalPages());
    }

    private Map<String, Object> toSummary(Document doc, boolean includeVisibility) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("id", doc.getId());
        summary.put("originalName", doc.getOriginalName());
        summary.put("title", doc.getTitle());
        summary.put("description", doc.getDescription());
        if (doc.getTags() != null) {
            summary.put("tags", doc.getTags().stream().map(t -> t.getName()).toList());
        }
        summary.put("contentType", doc.getContentType());
        summary.put("size", doc.getSize());

        if (includeVisibility) {
            summary.put("visible", doc.isVisible());
        } else {
            summary.put("ownerId", doc.getOwnerId());
        }

        // createdAt may be null for legacy rows, avoid Map.of null crash
        summary.put("createdAt", doc.getCreatedAt());
        summary.put("downloadCount", doc.getDownloadCount());
        summary.put("pageCount", doc.getPageCount());
        summary.put("processingStatus", doc.getProcessingStatus());

        return summary;
    }
}
