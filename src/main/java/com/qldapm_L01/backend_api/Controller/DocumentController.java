package com.qldapm_L01.backend_api.Controller;

import com.qldapm_L01.backend_api.Entity.Document;
import com.qldapm_L01.backend_api.Entity.User;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentStorageService storageService;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<?> getAllDocuments() {
        List<Document> documents = storageService.getAllVisibleDocuments();

        List<Map<String, Object>> list = documents.stream().map(doc -> Map.<String, Object>of(
                "id", doc.getId(),
                "originalName", doc.getOriginalName(),
                "contentType", doc.getContentType(),
                "size", doc.getSize(),
                "ownerId", doc.getOwnerId(),
                "createdAt", doc.getCreatedAt().toString()
        )).toList();

        BaseResponse response = new BaseResponse();
        response.setStatusCode(200);
        response.setMessage("Documents retrieved successfully");
        response.setData(list);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/my")
    public ResponseEntity<?> getMyDocuments(Authentication authentication) {
        int ownerId = currentUserId(authentication);
        List<Document> documents = storageService.getDocumentsByOwner(ownerId);

        List<Map<String, Object>> list = documents.stream().map(doc -> Map.<String, Object>of(
                "id", doc.getId(),
                "originalName", doc.getOriginalName(),
                "contentType", doc.getContentType(),
                "size", doc.getSize(),
                "visible", doc.isVisible(),
                "createdAt", doc.getCreatedAt().toString()
        )).toList();

        BaseResponse response = new BaseResponse();
        response.setStatusCode(200);
        response.setMessage("Documents retrieved successfully");
        response.setData(list);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getDocument(
            @PathVariable UUID id,
            Authentication authentication
    ) {
        int ownerId = currentUserId(authentication);
        Document doc = storageService.getDocument(ownerId, id);

        BaseResponse response = new BaseResponse();
        response.setStatusCode(200);
        response.setMessage("Document retrieved successfully");
        response.setData(Map.of(
                "id", doc.getId(),
                "originalName", doc.getOriginalName(),
                "contentType", doc.getContentType(),
                "size", doc.getSize(),
                "visible", doc.isVisible(),
                "createdAt", doc.getCreatedAt().toString()
        ));
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/visibility")
    public ResponseEntity<?> toggleVisibility(
            @PathVariable UUID id,
            @RequestBody Map<String, Boolean> body,
            Authentication authentication
    ) {
        int ownerId = currentUserId(authentication);
        boolean visible = body.getOrDefault("visible", true);
        Document updated = storageService.toggleVisibility(ownerId, id, visible);

        BaseResponse response = new BaseResponse();
        response.setStatusCode(200);
        response.setMessage(visible ? "Document is now visible" : "Document is now hidden");
        response.setData(Map.of(
                "id", updated.getId(),
                "visible", updated.isVisible()
        ));
        return ResponseEntity.ok(response);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> upload(
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) throws IOException {
        int ownerId = currentUserId(authentication);

        Document saved = storageService.upload(ownerId, file);

        BaseResponse response = new BaseResponse();
        response.setStatusCode(200);
        response.setMessage("Document uploaded successfully");
        response.setData(Map.of(
                "id", saved.getId(),
                "originalName", saved.getOriginalName(),
                "contentType", saved.getContentType(),
                "size", saved.getSize()
        ));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/download-url")
    public ResponseEntity<?> getDownloadUrl(
            @PathVariable UUID id,
            Authentication authentication
    ) {
        int ownerId = currentUserId(authentication);
        String url = storageService.createDownloadUrl(ownerId, id);

        BaseResponse response = new BaseResponse();
        response.setStatusCode(200);
        response.setMessage("Download URL generated successfully");
        response.setData(Map.of("url", url));
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/rename")
    public ResponseEntity<?> rename(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body,
            Authentication authentication
    ) {
        int ownerId = currentUserId(authentication);
        String newName = body.get("newName");
        Document updated = storageService.rename(ownerId, id, newName);

        BaseResponse response = new BaseResponse();
        response.setStatusCode(200);
        response.setMessage("Document renamed successfully");
        response.setData(Map.of(
                "id", updated.getId(),
                "originalName", updated.getOriginalName()
        ));
        return ResponseEntity.ok(response);
    }

    @PutMapping(value = "/{id}/replace", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> replaceFile(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) throws IOException {
        int ownerId = currentUserId(authentication);
        Document updated = storageService.replaceFile(ownerId, id, file);

        BaseResponse response = new BaseResponse();
        response.setStatusCode(200);
        response.setMessage("Document replaced successfully");
        response.setData(Map.of(
                "id", updated.getId(),
                "originalName", updated.getOriginalName(),
                "contentType", updated.getContentType(),
                "size", updated.getSize()
        ));
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(
            @PathVariable UUID id,
            Authentication authentication
    ) {
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
                .orElseThrow(() -> new RuntimeException("User not found"));
        return user.getId();
    }

    @GetMapping("/{listAll}")
    public ResponseEntity<?> getAllDocuments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<Document> docs = storageService.listAll(page, size);

        BaseResponse response = new BaseResponse();
        response.setStatusCode(200);
        response.setMessage("Documents fetched successfully");
        response.setData(Map.of(
                "items", docs.getContent().stream().map(doc -> Map.of(
                        "id", doc.getId(),
                        "originalName", doc.getOriginalName(),
                        "contentType", doc.getContentType(),
                        "size", doc.getSize(),
                        "createdAt", doc.getCreatedAt()
                )).toList(),
                "page", docs.getNumber(),
                "size", docs.getSize(),
                "totalItems", docs.getTotalElements(),
                "totalPages", docs.getTotalPages()
        ));
        return ResponseEntity.ok(response);
    }
}

