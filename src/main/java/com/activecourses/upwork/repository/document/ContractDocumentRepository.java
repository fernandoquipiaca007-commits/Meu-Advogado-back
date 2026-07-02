package com.activecourses.upwork.repository.document;

import com.activecourses.upwork.model.ContractDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContractDocumentRepository extends JpaRepository<ContractDocument, Integer> {
    List<ContractDocument> findByContractContractIdOrderByCreatedAtDesc(int contractId);
    long countByContractContractId(int contractId);
}
