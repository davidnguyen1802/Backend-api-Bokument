package com.qldapm_L01.backend_api.Service;

import com.qldapm_L01.backend_api.Entity.Document;
import com.qldapm_L01.backend_api.Repository.DocumentRepository;
import com.qldapm_L01.backend_api.Util.DocumentValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
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

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentStorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final DocumentRepository repository;
    private final DocumentValidator validator;

    @Value("${app.storage.bucket}")
    private String bucket;

    @Value("${app.storage.signed-url-minutes}")
    private long signedUrlMinutes;

    @Transactional
    public Document upload(int ownerId, MultipartFile file) throws IOException {
        DocumentValidator.ValidatedDocument validated = validator.validate(file);

        String objectKey = buildObjectKey(ownerId, validated.extension());

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType(validated.contentType())
                .contentDisposition("attachment; filename=\"" + validated.originalName() + "\"")
                .build();

        try (InputStream inputStream = file.getInputStream()) {
            s3Client.putObject(request, RequestBody.fromInputStream(inputStream, file.getSize()));
        }

        try {
            Document doc = new Document();
            doc.setOwnerId(ownerId);
            doc.setBucketName(bucket);
            doc.setObjectKey(objectKey);
            doc.setOriginalName(validated.originalName());
            doc.setContentType(validated.contentType());
            doc.setSize(validated.size());

            return repository.save(doc);
        } catch (RuntimeException ex) {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .build());
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public List<Document> getAllVisibleDocuments() {
        return repository.findByVisibleTrueOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<Document> getDocumentsByOwner(int ownerId) {
        return repository.findByOwnerIdOrderByCreatedAtDesc(ownerId);
    }

    @Transactional
    public Document toggleVisibility(int ownerId, UUID documentId, boolean visible) {
        Document doc = repository.findByIdAndOwnerId(documentId, ownerId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        doc.setVisible(visible);
        return repository.save(doc);
    }

    @Transactional(readOnly = true)
    public Document getDocument(int ownerId, UUID documentId) {
        return repository.findByIdAndOwnerId(documentId, ownerId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
    }

    @Transactional
    public Document rename(int ownerId, UUID documentId, String newName) {
        Document doc = repository.findByIdAndOwnerId(documentId, ownerId)
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

        return repository.save(doc);
    }

    @Transactional
    public Document replaceFile(int ownerId, UUID documentId, MultipartFile newFile) throws IOException {
        Document doc = repository.findByIdAndOwnerId(documentId, ownerId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));

        DocumentValidator.ValidatedDocument validated = validator.validate(newFile);

        // Ghi đè file cũ trên S3 (cùng objectKey)
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(doc.getBucketName())
                .key(doc.getObjectKey())
                .contentType(validated.contentType())
                .contentDisposition("attachment; filename=\"" + validated.originalName() + "\"")
                .build();

        try (InputStream inputStream = newFile.getInputStream()) {
            s3Client.putObject(request, RequestBody.fromInputStream(inputStream, newFile.getSize()));
        }

        doc.setOriginalName(validated.originalName());
        doc.setContentType(validated.contentType());
        doc.setSize(validated.size());

        return repository.save(doc);
    }

    @Transactional(readOnly = true)
    public String createDownloadUrl(int ownerId, UUID documentId) {
        Document doc = repository.findByIdAndOwnerId(documentId, ownerId)
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
                        .build()
        ).url().toString();
    }

    @Transactional
    public void delete(int ownerId, UUID documentId) {
        Document doc = repository.findByIdAndOwnerId(documentId, ownerId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));

        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(doc.getBucketName())
                .key(doc.getObjectKey())
                .build());

        repository.delete(doc);
    }

    private String buildObjectKey(int ownerId, String extension) {
        return "users/" + ownerId + "/" + UUID.randomUUID() + "." + extension;
    }
}

