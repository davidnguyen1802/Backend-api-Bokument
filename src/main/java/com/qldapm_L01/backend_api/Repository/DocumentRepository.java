package com.qldapm_L01.backend_api.Repository;

import com.qldapm_L01.backend_api.Entity.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {
    Optional<Document> findByIdAndOwnerId(UUID id, int ownerId);
    Page<Document> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
