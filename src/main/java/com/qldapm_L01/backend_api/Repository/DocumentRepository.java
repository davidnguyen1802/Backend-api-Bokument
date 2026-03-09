package com.qldapm_L01.backend_api.Repository;

import com.qldapm_L01.backend_api.Entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {
    Optional<Document> findByIdAndOwnerId(UUID id, int ownerId);
    List<Document> findByOwnerIdOrderByCreatedAtDesc(int ownerId);
    List<Document> findByVisibleTrueOrderByCreatedAtDesc();
}
