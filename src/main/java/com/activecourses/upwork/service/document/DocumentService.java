package com.activecourses.upwork.service.document;

import com.activecourses.upwork.dto.ContractDocumentDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface DocumentService {
    ContractDocumentDTO uploadDocument(int contractId, MultipartFile file, String description, String category);
    List<ContractDocumentDTO> getDocuments(int contractId);
    void deleteDocument(int documentId);
    ContractDocumentDTO getDocumentById(int documentId);

    /**
     * Returns download metadata for a document (storage path, content type, file name).
     * Internal path is not exposed via the public DTO.
     */
    DocumentDownloadInfo getDocumentDownloadInfo(int documentId);

    /** Internal record for download — not exposed to clients. */
    record DocumentDownloadInfo(String storagePath, String contentType, String fileName) {}
}
