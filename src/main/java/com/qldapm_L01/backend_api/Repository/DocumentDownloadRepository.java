package com.qldapm_L01.backend_api.Repository;

import com.qldapm_L01.backend_api.Entity.DocumentDownload;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DocumentDownloadRepository extends JpaRepository<DocumentDownload, UUID> {

    /** Tổng số lần user này đã tải về (dùng kiểm tra quota). */
    long countByUserId(int userId);

    /** Tổng lượt tải xuống của một tài liệu cụ thể. */
    long countByDocumentId(UUID documentId);
}
