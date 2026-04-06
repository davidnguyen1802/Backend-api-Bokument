package com.qldapm_L01.backend_api.Repository;

import com.qldapm_L01.backend_api.Entity.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {
    Optional<Document> findByIdAndOwnerId(UUID id, int ownerId);
    Optional<Document> findByIdAndVisibleTrue(UUID id);
    long countByOwnerId(int ownerId);

    /**
     * Tăng download_count lên 1 một cách nguyên tử tại DB.
     * PostgreSQL đảm bảo UPDATE single row là atomic — không cần lock thêm.
     */
    @Modifying
    @Query(value = "UPDATE documents SET download_count = download_count + 1 WHERE id = :id", nativeQuery = true)
    void incrementDownloadCount(@Param("id") java.util.UUID id);

    // Fallback queries for legacy schema where original_name is bytea
    Page<Document> findByVisibleTrueOrderByCreatedAtDesc(Pageable pageable);

    Page<Document> findByVisibleTrueAndContentTypeOrderByCreatedAtDesc(String contentType, Pageable pageable);

    Page<Document> findByOwnerIdOrderByCreatedAtDesc(int ownerId, Pageable pageable);

    Page<Document> findByOwnerIdAndVisibleOrderByCreatedAtDesc(int ownerId, boolean visible, Pageable pageable);

    @Query(value = """
            SELECT d.* FROM documents d
            WHERE d.visible = true
              AND (CAST(:q AS text) IS NULL OR d.fts @@ websearch_to_tsquery('simple', unaccent(CAST(:q AS text))))
              AND (CAST(:contentType AS text) IS NULL OR d.content_type = CAST(:contentType AS text))
              AND (CAST(:tagsCount AS int) = 0 OR EXISTS (
                 SELECT 1 FROM document_tags dt JOIN tags t ON dt.tag_id = t.id 
                 WHERE dt.document_id = d.id AND t.name IN (:tags)
              ))
            ORDER BY d.created_at DESC
            """,
            countQuery = """
            SELECT count(d.id) FROM documents d
            WHERE d.visible = true
              AND (CAST(:q AS text) IS NULL OR d.fts @@ websearch_to_tsquery('simple', unaccent(CAST(:q AS text))))
              AND (CAST(:contentType AS text) IS NULL OR d.content_type = CAST(:contentType AS text))
              AND (CAST(:tagsCount AS int) = 0 OR EXISTS (
                 SELECT 1 FROM document_tags dt JOIN tags t ON dt.tag_id = t.id 
                 WHERE dt.document_id = d.id AND t.name IN (:tags)
              ))
            """,
            nativeQuery = true)
    Page<Document> searchVisible(
            @Param("q") String q,
            @Param("contentType") String contentType,
            @Param("tags") java.util.List<String> tags,
            @Param("tagsCount") int tagsCount,
            Pageable pageable
    );

    @Query(value = """
            SELECT d.* FROM documents d
            WHERE d.owner_id = :ownerId
              AND (CAST(:q AS text) IS NULL OR d.fts @@ websearch_to_tsquery('simple', unaccent(CAST(:q AS text))))
              AND (CAST(:visible AS boolean) IS NULL OR d.visible = CAST(:visible AS boolean))
              AND (CAST(:tagsCount AS int) = 0 OR EXISTS (
                  SELECT 1 FROM document_tags dt JOIN tags t ON dt.tag_id = t.id 
                  WHERE dt.document_id = d.id AND t.name IN (:tags)
              ))
            ORDER BY d.created_at DESC
            """,
            countQuery = """
            SELECT count(d.id) FROM documents d
            WHERE d.owner_id = :ownerId
              AND (CAST(:q AS text) IS NULL OR d.fts @@ websearch_to_tsquery('simple', unaccent(CAST(:q AS text))))
              AND (CAST(:visible AS boolean) IS NULL OR d.visible = CAST(:visible AS boolean))
              AND (CAST(:tagsCount AS int) = 0 OR EXISTS (
                  SELECT 1 FROM document_tags dt JOIN tags t ON dt.tag_id = t.id 
                  WHERE dt.document_id = d.id AND t.name IN (:tags)
              ))
            """,
            nativeQuery = true)
    Page<Document> searchByOwner(
            @Param("ownerId") int ownerId,
            @Param("q") String q,
            @Param("visible") Boolean visible,
            @Param("tags") java.util.List<String> tags,
            @Param("tagsCount") int tagsCount,
            Pageable pageable
    );
}
