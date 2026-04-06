package com.qldapm_L01.backend_api.Service.Imp;

import com.qldapm_L01.backend_api.DTO.DocumentResponse;
import com.qldapm_L01.backend_api.Entity.Document;
import com.qldapm_L01.backend_api.Entity.User;
import com.qldapm_L01.backend_api.Exception.DataNotFoundException;
import com.qldapm_L01.backend_api.Repository.DocumentRepository;
import com.qldapm_L01.backend_api.Repository.UserRepository;
import com.qldapm_L01.backend_api.Service.DocumentService;
import com.qldapm_L01.backend_api.Service.SupabaseStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DocumentServiceImp implements DocumentService {

    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final SupabaseStorageService supabaseStorageService;

    @Override
    public DocumentResponse uploadDocument(MultipartFile file) {
        User currentUser = getCurrentUser();

        String originalFileName = file.getOriginalFilename();
        String fileName = UUID.randomUUID() + "_" + originalFileName;
        String storagePath = "documents/" + currentUser.getId() + "/" + fileName;

        try {
            supabaseStorageService.uploadFile(
                    storagePath,
                    file.getBytes(),
                    file.getContentType() != null ? file.getContentType() : "application/octet-stream"
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file content: " + e.getMessage());
        }

        Document document = new Document();
        document.setFileName(fileName);
        document.setOriginalFileName(originalFileName);
        document.setFileSize(file.getSize());
        document.setContentType(file.getContentType());
        document.setStoragePath(storagePath);
        document.setUser(currentUser);

        Document saved = documentRepository.save(document);
        return mapToResponse(saved);
    }

    @Override
    public List<DocumentResponse> getUserDocuments() {
        User currentUser = getCurrentUser();
        List<Document> documents = documentRepository.findAllByUserId(currentUser.getId());
        return documents.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public byte[] downloadDocument(Long id) {
        Document document = getDocumentForCurrentUser(id);
        return supabaseStorageService.downloadFile(document.getStoragePath());
    }

    @Override
    public String getOriginalFileName(Long id) {
        Document document = getDocumentForCurrentUser(id);
        return document.getOriginalFileName();
    }

    @Override
    public String getContentType(Long id) {
        Document document = getDocumentForCurrentUser(id);
        return document.getContentType();
    }

    @Override
    public void deleteDocument(Long id) {
        Document document = getDocumentForCurrentUser(id);
        supabaseStorageService.deleteFile(document.getStoragePath());
        documentRepository.delete(document);
    }

    private User getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new DataNotFoundException("User not found"));
    }

    private Document getDocumentForCurrentUser(Long id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new DataNotFoundException("Document not found"));

        User currentUser = getCurrentUser();
        if (document.getUser().getId() != currentUser.getId()) {
            throw new RuntimeException("You do not have permission to access this document");
        }

        return document;
    }

    private DocumentResponse mapToResponse(Document document) {
        DocumentResponse response = new DocumentResponse();
        response.setId(document.getId());
        response.setFileName(document.getFileName());
        response.setOriginalFileName(document.getOriginalFileName());
        response.setFileSize(document.getFileSize());
        response.setContentType(document.getContentType());
        response.setStoragePath(document.getStoragePath());
        response.setUploadedAt(document.getUploadedAt());
        response.setUserId(document.getUser().getId());
        return response;
    }
}

