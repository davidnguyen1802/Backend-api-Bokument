package com.qldapm_L01.backend_api.Service;

import com.qldapm_L01.backend_api.Entity.Document;
import com.qldapm_L01.backend_api.Entity.DocumentDownload;
import com.qldapm_L01.backend_api.Repository.DocumentDownloadRepository;
import com.qldapm_L01.backend_api.Repository.DocumentRepository;
import com.qldapm_L01.backend_api.Util.DocumentValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.MetadataDirective;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentStorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final DocumentRepository documentRepository;
    private final DocumentDownloadRepository downloadRepository;
    private final DocumentValidator validator;
    private final JdbcTemplate jdbcTemplate;
    private final PdfConversionService pdfConversionService;

    @Value("${app.storage.bucket}")
    private String bucket;

    @Value("${app.storage.signed-url-minutes}")
    private long signedUrlMinutes;

    private volatile Boolean originalNameByteaSchema;

    @Transactional
    public Document upload(int ownerId, MultipartFile file) throws IOException {
        DocumentValidator.ValidatedDocument validated = validator.validate(file);

        String objectKey = buildObjectKey(ownerId, validated.extension());

        File tempFile = File.createTempFile("upload-", ".pdf");
        file.transferTo(tempFile);

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType(validated.contentType())
                .contentDisposition("attachment; filename=\"" + validated.originalName() + "\"")
                .build();

        try {
            s3Client.putObject(request, RequestBody.fromFile(tempFile));
        } catch (Exception e) {
            tempFile.delete();
            throw e;
        }

        try {
            Document doc = new Document();
            doc.setOwnerId(ownerId);
            doc.setBucketName(bucket);
            doc.setObjectKey(objectKey);
            doc.setOriginalName(validated.originalName());
            doc.setContentType(validated.contentType());
            doc.setSize(validated.size());
            doc.setProcessingStatus("PENDING");

            Document saved = documentRepository.save(doc);
            
            pdfConversionService.processAndUploadImages(saved, tempFile);
            
            return saved;
        } catch (RuntimeException ex) {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .build());
            tempFile.delete();
            throw ex;
        }
    }

    @Transactional
    public Document toggleVisibility(int ownerId, UUID documentId, boolean visible) {
        Document doc = documentRepository.findByIdAndOwnerId(documentId, ownerId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        doc.setVisible(visible);
        return documentRepository.save(doc);
    }

    @Transactional(readOnly = true)
    public Document getDocument(int ownerId, UUID documentId) {
        return documentRepository.findByIdAndOwnerId(documentId, ownerId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
    }

    @Transactional(readOnly = true)
    public Document getDocumentForRead(Integer requesterId, UUID documentId) {
        if (requesterId != null) {
            return documentRepository.findByIdAndOwnerId(documentId, requesterId)
                    .or(() -> documentRepository.findByIdAndVisibleTrue(documentId))
                    .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        }

        return documentRepository.findByIdAndVisibleTrue(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
    }

    @Transactional
    public Document rename(int ownerId, UUID documentId, String newName) {
        Document doc = documentRepository.findByIdAndOwnerId(documentId, ownerId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));

        if (newName == null || newName.isBlank()) {
            throw new IllegalArgumentException("New name must not be empty");
        }

        // Giữ nguyên extension gốc
        String originalExt = doc.getOriginalName().substring(doc.getOriginalName().lastIndexOf('.'));
        String cleanName = newName.strip();
        if (!cleanName.endsWith(originalExt)) {
            cleanName = cleanName + originalExt;
        }

        doc.setOriginalName(cleanName);

        // Cập nhật Content-Disposition trên S3
        CopyObjectRequest copyReq = CopyObjectRequest.builder()
                .sourceBucket(doc.getBucketName())
                .sourceKey(doc.getObjectKey())
                .destinationBucket(doc.getBucketName())
                .destinationKey(doc.getObjectKey())
                .contentDisposition("attachment; filename=\"" + cleanName + "\"")
                .contentType(doc.getContentType())
                .metadataDirective(MetadataDirective.REPLACE)
                .build();
        s3Client.copyObject(copyReq);

        return documentRepository.save(doc);
    }

    @Transactional
    public Document replaceFile(int ownerId, UUID documentId, MultipartFile newFile) throws IOException {
        Document doc = documentRepository.findByIdAndOwnerId(documentId, ownerId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));

        DocumentValidator.ValidatedDocument validated = validator.validate(newFile);

        File tempFile = File.createTempFile("replace-", ".pdf");
        newFile.transferTo(tempFile);

        // Ghi đè file cũ trên S3 (cùng objectKey)
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(doc.getBucketName())
                .key(doc.getObjectKey())
                .contentType(validated.contentType())
                .contentDisposition("attachment; filename=\"" + validated.originalName() + "\"")
                .build();

        try {
            s3Client.putObject(request, RequestBody.fromFile(tempFile));
        } catch (Exception e) {
            tempFile.delete();
            throw e;
        }

        doc.setOriginalName(validated.originalName());
        doc.setContentType(validated.contentType());
        doc.setSize(validated.size());
        doc.setProcessingStatus("PENDING");
        doc.setPageCount(0);

        Document saved = documentRepository.save(doc);
        
        pdfConversionService.processAndUploadImages(saved, tempFile);
        
        return saved;
    }

    @Transactional(readOnly = true)
    public String createDownloadUrl(int ownerId, UUID documentId) {
        Document doc = documentRepository.findByIdAndOwnerId(documentId, ownerId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(doc.getBucketName())
                .key(doc.getObjectKey())
                .responseContentDisposition("attachment; filename=\"" + doc.getOriginalName() + "\"")
                .build();

        return s3Presigner.presignGetObject(
                GetObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofMinutes(signedUrlMinutes))
                        .getObjectRequest(getObjectRequest)
                        .build())
                .url().toString();
    }

    /**
     * Cấp Pre-signed URL tải về — áp dụng quota (uploads >= downloads + 1).
     *
     * Dùng SERIALIZABLE isolation để tránh race condition:
     * Postgres đảm bảo không có 2 giao dịch đồng thời nào cùng vượt quota.
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public String createDownloadUrlForRead(Integer requesterId, UUID documentId) {
        long uploads = documentRepository.countByOwnerId(requesterId);
        long downloads = downloadRepository.countByUserId(requesterId);

        if (uploads <= downloads) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Bạn cần đóng góp ít nhất bằng số bài bạn đã tải về. " +
                            "Hiện tại: đã upload " + uploads + " bài, đã tải về " + downloads + " bài.");
        }

        // Ghi nhận lượt tải: nguyên tử INSERT vào document_downloads (dùng cho quota
        // user)
        // + UPDATE cột download_count trên documents (O(1), thân thiện với display)
        DocumentDownload record = new DocumentDownload();
        record.setUserId(requesterId);
        record.setDocumentId(documentId);
        downloadRepository.save(record);
        documentRepository.incrementDownloadCount(documentId);

        Document doc = getDocumentForRead(requesterId, documentId);

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(doc.getBucketName())
                .key(doc.getObjectKey())
                .responseContentDisposition("attachment; filename=\"" + doc.getOriginalName() + "\"")
                .build();

        return s3Presigner.presignGetObject(
                GetObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofMinutes(signedUrlMinutes))
                        .getObjectRequest(getObjectRequest)
                        .build())
                .url().toString();
    }

    public String createPageUrl(Document doc, int pageNumber) {
        String key = "users/" + doc.getOwnerId() + "/" + doc.getId() + "/pages/page_" + pageNumber + ".jpg";
        
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        return s3Presigner.presignGetObject(
                GetObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofMinutes(15))
                        .getObjectRequest(getObjectRequest)
                        .build()
        ).url().toString();
    }

    /**
     * Đếm lượt tải xuống của một tài liệu — đọc từ cột download_count, O(1).
     * Không dùng COUNT(*) trên document_downloads để tránh full scan khi có nhiều
     * lượt tải.
     */
    @Transactional(readOnly = true)
    public long countDownloadsByDocument(UUID documentId) {
        return documentRepository.findById(documentId)
                .map(Document::getDownloadCount)
                .orElse(0L);
    }

    @Transactional
    public void delete(int ownerId, UUID documentId) {
        Document doc = documentRepository.findByIdAndOwnerId(documentId, ownerId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));

        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(doc.getBucketName())
                .key(doc.getObjectKey())
                .build());

        documentRepository.delete(doc);
    }

    private String buildObjectKey(int ownerId, String extension) {
        return "users/" + ownerId + "/" + UUID.randomUUID() + "." + extension;
    }

    public long countByOwnerId(int ownerId) {
        return documentRepository.countByOwnerId(ownerId);
    }

    public Page<Document> listVisible(int page, int size, String q, String contentType, java.util.List<String> tags) {
        Pageable pageable = PageRequest.of(page, size);
        String normalizedQ = normalize(q);
        String normalizedContentType = normalize(contentType);

        java.util.List<String> validTags = tags == null ? new java.util.ArrayList<String>()
                : tags.stream().filter(t -> t != null && !t.isBlank()).map(String::trim).toList();
        int tagsCount = validTags.size();
        if (validTags.isEmpty()) {
            validTags = java.util.List.of("");
        }

        if (isOriginalNameByteaSchema()) {
            if (normalizedQ != null) {
                log.warn(
                        "Ignoring q filter because documents.original_name is bytea. Apply DB migration to enable name search.");
            }
            return listVisibleWithoutNameSearch(pageable, normalizedContentType);
        }

        try {
            return documentRepository.searchVisible(normalizedQ, normalizedContentType, validTags, tagsCount, pageable);
        } catch (DataAccessException ex) {
            if (!isLowerByteaError(ex)) {
                throw ex;
            }
            log.warn("Fallback listVisible: schema mismatch on original_name (bytea). q filter is skipped.");
            return listVisibleWithoutNameSearch(pageable, normalizedContentType);
        }
    }

    public Page<Document> listByOwner(int ownerId, int page, int size, String q, Boolean visible,
            java.util.List<String> tags) {
        Pageable pageable = PageRequest.of(page, size);
        String normalizedQ = normalize(q);

        java.util.List<String> validTags = tags == null ? new java.util.ArrayList<String>()
                : tags.stream().filter(t -> t != null && !t.isBlank()).map(String::trim).toList();
        int tagsCount = validTags.size();
        if (validTags.isEmpty()) {
            validTags = java.util.List.of("");
        }

        if (isOriginalNameByteaSchema()) {
            if (normalizedQ != null) {
                log.warn(
                        "Ignoring q filter because documents.original_name is bytea. Apply DB migration to enable name search.");
            }
            return listByOwnerWithoutNameSearch(ownerId, visible, pageable);
        }

        try {
            return documentRepository.searchByOwner(ownerId, normalizedQ, visible, validTags, tagsCount, pageable);
        } catch (DataAccessException ex) {
            if (!isLowerByteaError(ex)) {
                throw ex;
            }
            log.warn("Fallback listByOwner: schema mismatch on original_name (bytea). q filter is skipped.");
            return listByOwnerWithoutNameSearch(ownerId, visible, pageable);
        }
    }

    private Page<Document> listVisibleWithoutNameSearch(Pageable pageable, String contentType) {
        if (contentType == null) {
            return documentRepository.findByVisibleTrueOrderByCreatedAtDesc(pageable);
        }
        return documentRepository.findByVisibleTrueAndContentTypeOrderByCreatedAtDesc(contentType, pageable);
    }

    private Page<Document> listByOwnerWithoutNameSearch(int ownerId, Boolean visible, Pageable pageable) {
        if (visible == null) {
            return documentRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId, pageable);
        }
        return documentRepository.findByOwnerIdAndVisibleOrderByCreatedAtDesc(ownerId, visible, pageable);
    }

    private boolean isLowerByteaError(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains("function lower(bytea) does not exist")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean isOriginalNameByteaSchema() {
        Boolean cached = originalNameByteaSchema;
        if (cached != null) {
            return cached;
        }

        synchronized (this) {
            if (originalNameByteaSchema != null) {
                return originalNameByteaSchema;
            }

            String dataType = jdbcTemplate.query(
                    """
                            SELECT data_type
                            FROM information_schema.columns
                            WHERE table_schema = 'public'
                              AND table_name = 'documents'
                              AND column_name = 'original_name'
                            """,
                    rs -> rs.next() ? rs.getString("data_type") : null);

            originalNameByteaSchema = dataType != null && "bytea".equalsIgnoreCase(dataType);
            return originalNameByteaSchema;
        }
    }

    private String normalize(String input) {
        if (input == null) {
            return null;
        }
        String cleaned = input.trim().replaceAll("\\s+", " ");
        return cleaned.isEmpty() ? null : cleaned;
    }
}
