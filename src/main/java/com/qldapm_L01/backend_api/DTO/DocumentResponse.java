package com.qldapm_L01.backend_api.DTO;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DocumentResponse {
    private Long id;
    private String fileName;
    private String originalFileName;
    private Long fileSize;
    private String contentType;
    private String storagePath;
    private LocalDateTime uploadedAt;
    private int userId;
}

