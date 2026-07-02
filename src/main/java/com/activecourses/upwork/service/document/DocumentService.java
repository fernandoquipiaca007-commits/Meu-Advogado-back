package com.activecourses.upwork.service.document;

import com.activecourses.upwork.dto.ContractDocumentDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface DocumentService {
    ContractDocumentDTO uploadDocument(int contractId, MultipartFile file, String description, String category);
    List<ContractDocumentDTO> getDocuments(int contractId);
    void deleteDocument(int documentId);
    ContractDocumentDTO getDocumentById(int documentId);
}
