package com.qldapm_L01.backend_api.Service;

import com.qldapm_L01.backend_api.DTO.DocumentResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface DocumentService {
    DocumentResponse uploadDocument(MultipartFile file);
    List<DocumentResponse> getUserDocuments();
    byte[] downloadDocument(Long id);
    String getOriginalFileName(Long id);
    String getContentType(Long id);
    void deleteDocument(Long id);
}

