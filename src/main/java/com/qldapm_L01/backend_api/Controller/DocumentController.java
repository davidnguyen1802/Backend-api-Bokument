package com.qldapm_L01.backend_api.Controller;

import com.qldapm_L01.backend_api.Entity.Document;
import com.qldapm_L01.backend_api.Entity.User;
import com.qldapm_L01.backend_api.Payload.Response.BaseResponse;
import com.qldapm_L01.backend_api.Repository.UserRepository;
import com.qldapm_L01.backend_api.Service.DocumentStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentStorageService storageService;
    private final UserRepository userRepository;

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
}

