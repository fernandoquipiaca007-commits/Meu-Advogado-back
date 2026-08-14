package com.activecourses.upwork.service.document;

import com.activecourses.upwork.dto.DocumentAccessLogDto;
import com.activecourses.upwork.dto.SecureDocumentDto;
import com.activecourses.upwork.model.DocumentClassification;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

public interface SecureDocumentService {

    record SecureDownloadInfo(
            String fileName,
            String contentType,
            String sha256Hash,
            long fileSize,
            Resource resource
    ) {}

    SecureDocumentDto uploadSecureDocument(
            MultipartFile file,
            Integer contractId,
            Integer jobId,
            DocumentClassification classification,
            String description,
            HttpServletRequest request
    );

    SecureDownloadInfo downloadSecureDocument(Long documentId, HttpServletRequest request);

    List<SecureDocumentDto> getDocumentsByContract(Integer contractId);

    List<SecureDocumentDto> getDocumentsByJob(Integer jobId);

    Optional<SecureDocumentDto> getDocumentById(Long documentId);

    void deleteSecureDocument(Long documentId, HttpServletRequest request);

    List<DocumentAccessLogDto> getAccessLogs(Long documentId, HttpServletRequest request);
}
