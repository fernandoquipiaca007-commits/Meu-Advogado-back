package com.activecourses.upwork.service.document;

import com.activecourses.upwork.dto.ContractDocumentDTO;
import com.activecourses.upwork.model.Contract;
import com.activecourses.upwork.model.ContractDocument;
import com.activecourses.upwork.model.User;
import com.activecourses.upwork.repository.contract.ContractRepository;
import com.activecourses.upwork.repository.document.ContractDocumentRepository;
import com.activecourses.upwork.repository.user.UserRepository;
import com.activecourses.upwork.service.authentication.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    private final ContractDocumentRepository documentRepository;
    private final ContractRepository contractRepository;
    private final UserRepository userRepository;
    private final AuthService authService;

    @Value("${app.upload.dir:uploads/documents}")
    private String uploadDir;

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(Paths.get(uploadDir));
        } catch (IOException e) {
            throw new RuntimeException("Could not create upload directory", e);
        }
    }

    @Override
    @Transactional
    public ContractDocumentDTO uploadDocument(int contractId, MultipartFile file, String description, String category) {
        Integer userId = authService.getCurrentUserId();
        if (userId == null) throw new IllegalStateException("Not authenticated");

        if (file.isEmpty()) throw new IllegalArgumentException("File is empty");

        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new IllegalArgumentException("Contract not found"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Verify participant
        boolean isParticipant = contract.getClient().getId().equals(userId)
                || contract.getLawyer().getId().equals(userId);
        if (!isParticipant) {
            throw new SecurityException("You can only upload documents to contracts you participate in");
        }

        // Generate unique storage path
        String originalName = file.getOriginalFilename();
        String extension = "";
        if (originalName != null && originalName.contains(".")) {
            extension = originalName.substring(originalName.lastIndexOf("."));
        }
        String storageName = UUID.randomUUID().toString() + extension;
        String storagePath = uploadDir + "/" + storageName;

        try {
            Files.copy(file.getInputStream(), Paths.get(storagePath));
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file", e);
        }

        ContractDocument document = ContractDocument.builder()
                .contract(contract)
                .uploadedBy(user)
                .fileName(originalName != null ? originalName : "unknown")
                .fileSize(file.getSize())
                .contentType(file.getContentType() != null ? file.getContentType() : "application/octet-stream")
                .storagePath(storagePath)
                .description(description)
                .category(category != null ? category : "other")
                .build();

        document = documentRepository.save(document);
        return mapToDTO(document);
    }

    @Override
    public List<ContractDocumentDTO> getDocuments(int contractId) {
        return documentRepository.findByContractContractIdOrderByCreatedAtDesc(contractId).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteDocument(int documentId) {
        Integer userId = authService.getCurrentUserId();
        if (userId == null) throw new IllegalStateException("Not authenticated");

        ContractDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));

        // Only uploader or admin can delete
        if (!document.getUploadedBy().getId().equals(userId)) {
            throw new SecurityException("You can only delete your own documents");
        }

        // Delete file from disk
        try {
            Files.deleteIfExists(Paths.get(document.getStoragePath()));
        } catch (IOException e) {
            // Log but don't fail
        }

        documentRepository.delete(document);
    }

    @Override
    public ContractDocumentDTO getDocumentById(int documentId) {
        return documentRepository.findById(documentId)
                .map(this::mapToDTO)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
    }

    @Override
    public DocumentDownloadInfo getDocumentDownloadInfo(int documentId) {
        ContractDocument doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        return new DocumentDownloadInfo(
                doc.getStoragePath(),
                doc.getContentType(),
                doc.getFileName()
        );
    }

    private ContractDocumentDTO mapToDTO(ContractDocument document) {
        return ContractDocumentDTO.builder()
                .documentId(document.getDocumentId())
                .contractId(document.getContract().getContractId())
                .uploadedById(document.getUploadedBy().getId())
                .uploadedByName(document.getUploadedBy().getFirstName() + " " + document.getUploadedBy().getLastName())
                .fileName(document.getFileName())
                .fileSize(document.getFileSize())
                .contentType(document.getContentType())
                .description(document.getDescription())
                .category(document.getCategory())
                .createdAt(document.getCreatedAt())
                .build();
    }
}
