package com.activecourses.upwork.repository.document;

import com.activecourses.upwork.model.SecureDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SecureDocumentRepository extends JpaRepository<SecureDocument, Long> {
    List<SecureDocument> findByContractContractIdAndIsDeletedFalse(Integer contractId);
    List<SecureDocument> findByJobJobIdAndIsDeletedFalse(Integer jobId);
    List<SecureDocument> findByOwnerIdAndIsDeletedFalse(Integer ownerId);
    Optional<SecureDocument> findByIdAndIsDeletedFalse(Long id);
    Optional<SecureDocument> findBySha256Hash(String sha256Hash);
}
