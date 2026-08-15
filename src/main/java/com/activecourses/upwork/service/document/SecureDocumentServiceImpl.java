package com.activecourses.upwork.service.document;

import com.activecourses.upwork.dto.DocumentAccessLogDto;
import com.activecourses.upwork.dto.SecureDocumentDto;
import com.activecourses.upwork.model.*;
import com.activecourses.upwork.repository.contract.ContractRepository;
import com.activecourses.upwork.repository.document.DocumentAccessLogRepository;
import com.activecourses.upwork.repository.document.SecureDocumentRepository;
import com.activecourses.upwork.repository.job.JobRepository;
import com.activecourses.upwork.repository.user.UserRepository;
import com.activecourses.upwork.service.authentication.AuthService;
import com.activecourses.upwork.service.security.AuthorizationService;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SecureDocumentServiceImpl implements SecureDocumentService {

    private static final Logger logger = LoggerFactory.getLogger(SecureDocumentServiceImpl.class);

    private final SecureDocumentRepository secureDocumentRepository;
    private final DocumentAccessLogRepository documentAccessLogRepository;
    private final ContractRepository contractRepository;
    private final JobRepository jobRepository;
    private final UserRepository userRepository;
    private final AuthService authService;
    private final AuthorizationService authorizationService;

    @Value("${app.upload.secure-dir:uploads/secure_documents/}")
    private String uploadDir;

    @PostConstruct
    public void init() {
        try {
            Path path = Paths.get(uploadDir);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                logger.info("Secure document storage directory created at: {}", path.toAbsolutePath());
            }
        } catch (IOException e) {
            logger.error("Could not create secure document directory: {}", e.getMessage());
        }
    }

    @Override
    @Transactional
    public SecureDocumentDto uploadSecureDocument(
            MultipartFile file,
            Integer contractId,
            Integer jobId,
            DocumentClassification classification,
            String description,
            HttpServletRequest request) {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("O arquivo é obrigatório para upload.");
        }

        Integer currentUserId = authService.getCurrentUserId();
        if (currentUserId == null) {
            throw new AccessDeniedException("Não autenticado.");
        }

        User owner = userRepository.findById(currentUserId)
                .orElseThrow(() -> new AccessDeniedException("Usuário não encontrado: " + currentUserId));

        Contract contract = null;
        if (contractId != null) {
            contract = contractRepository.findById(contractId)
                    .orElseThrow(() -> new IllegalArgumentException("Contrato não encontrado: " + contractId));
            authorizationService.enforceContractParticipant(contractId, currentUserId);
        }

        Job job = null;
        if (jobId != null) {
            job = jobRepository.findById(jobId)
                    .orElseThrow(() -> new IllegalArgumentException("Demanda não encontrada: " + jobId));
            authorizationService.enforceCanViewJobDetail(jobId, currentUserId);
        } else if (contract != null && contract.getJob() != null) {
            job = contract.getJob();
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.trim().isEmpty()) {
            originalFilename = "secure_document_" + System.currentTimeMillis();
        }
        // Sanitize filename
        originalFilename = Paths.get(originalFilename).getFileName().toString();

        long fileSize = file.getSize();
        String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";

        // Calculate SHA-256 hash and save to disk
        String sha256Hex;
        String storageFileName = UUID.randomUUID() + "_" + originalFilename;
        Path targetPath = Paths.get(uploadDir).resolve(storageFileName);

        try (InputStream inputStream = file.getInputStream()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
            byte[] hashBytes = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            sha256Hex = hexString.toString();

            // Save file to disk
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new RuntimeException("Falha ao processar e salvar o documento seguro: " + e.getMessage(), e);
        }

        DocumentClassification docClassification = classification != null ? classification : DocumentClassification.CONFIDENTIAL;

        SecureDocument doc = SecureDocument.builder()
                .contract(contract)
                .job(job)
                .owner(owner)
                .fileName(originalFilename)
                .fileSize(fileSize)
                .contentType(contentType)
                .storagePath(targetPath.toAbsolutePath().toString())
                .sha256Hash(sha256Hex)
                .classification(docClassification)
                .virusScanStatus(VirusScanStatus.CLEAN)
                .version(1)
                .description(description)
                .isDeleted(false)
                .createdAt(LocalDateTime.now())
                .build();

        doc = secureDocumentRepository.save(doc);

        // Record UPLOAD log
        logAccess(doc, owner, "UPLOAD", request);

        return mapToDto(doc);
    }

    @Override
    @Transactional
    public SecureDownloadInfo downloadSecureDocument(Long documentId, HttpServletRequest request) {
        if (documentId == null) {
            throw new IllegalArgumentException("O identificador do documento é obrigatório.");
        }

        Integer currentUserId = authService.getCurrentUserId();
        if (currentUserId == null) {
            throw new AccessDeniedException("Não autenticado.");
        }

        SecureDocument doc = secureDocumentRepository.findByIdAndIsDeletedFalse(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Documento não encontrado ou excluído: " + documentId));

        // Enforce document access
        authorizationService.enforceDocumentAccess(doc, currentUserId);

        Path filePath = Paths.get(doc.getStoragePath());
        Resource resource = new FileSystemResource(filePath.toFile());

        if (!resource.exists()) {
            throw new IllegalStateException("O arquivo físico do documento não foi encontrado no servidor.");
        }

        User user = userRepository.findById(currentUserId).orElse(null);
        logAccess(doc, user, "DOWNLOAD", request);

        return new SecureDownloadInfo(
                doc.getFileName(),
                doc.getContentType(),
                doc.getSha256Hash(),
                doc.getFileSize(),
                resource
        );
    }

    @Override
    public List<SecureDocumentDto> getDocumentsByContract(Integer contractId) {
        if (contractId == null) return Collections.emptyList();

        Integer currentUserId = authService.getCurrentUserId();
        if (currentUserId == null) {
            throw new AccessDeniedException("Não autenticado.");
        }

        authorizationService.enforceContractParticipant(contractId, currentUserId);

        return secureDocumentRepository.findByContractContractIdAndIsDeletedFalse(contractId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Override
    public List<SecureDocumentDto> getDocumentsByJob(Integer jobId) {
        if (jobId == null) return Collections.emptyList();

        Integer currentUserId = authService.getCurrentUserId();
        if (currentUserId == null) {
            throw new AccessDeniedException("Não autenticado.");
        }

        authorizationService.enforceCanViewJobDetail(jobId, currentUserId);

        return secureDocumentRepository.findByJobJobIdAndIsDeletedFalse(jobId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<SecureDocumentDto> getMyDocuments(HttpServletRequest request) {
        Integer currentUserId = authService.getCurrentUserId();
        if (currentUserId == null) {
            throw new AccessDeniedException("NÃ£o autenticado.");
        }

        return secureDocumentRepository.findByOwnerIdAndIsDeletedFalse(currentUserId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<SecureDocumentDto> getDocumentById(Long documentId) {
        if (documentId == null) return Optional.empty();

        Integer currentUserId = authService.getCurrentUserId();
        if (currentUserId == null) {
            throw new AccessDeniedException("Não autenticado.");
        }

        return secureDocumentRepository.findByIdAndIsDeletedFalse(documentId)
                .map(doc -> {
                    authorizationService.enforceDocumentAccess(doc, currentUserId);
                    return mapToDto(doc);
                });
    }

    @Override
    @Transactional
    public void deleteSecureDocument(Long documentId, HttpServletRequest request) {
        if (documentId == null) {
            throw new IllegalArgumentException("O identificador do documento é obrigatório.");
        }

        Integer currentUserId = authService.getCurrentUserId();
        if (currentUserId == null) {
            throw new AccessDeniedException("Não autenticado.");
        }

        SecureDocument doc = secureDocumentRepository.findByIdAndIsDeletedFalse(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Documento não encontrado: " + documentId));

        boolean isOwner = doc.getOwner() != null && currentUserId.equals(doc.getOwner().getId());
        if (!isOwner) {
            throw new AccessDeniedException("Apenas o proprietário pode excluir este documento.");
        }

        doc.setIsDeleted(true);
        secureDocumentRepository.save(doc);

        User user = userRepository.findById(currentUserId).orElse(null);
        logAccess(doc, user, "DELETE", request);
    }

    @Override
    @Transactional
    public List<DocumentAccessLogDto> getAccessLogs(Long documentId, HttpServletRequest request) {
        if (documentId == null) return Collections.emptyList();

        Integer currentUserId = authService.getCurrentUserId();
        if (currentUserId == null) {
            throw new AccessDeniedException("Não autenticado.");
        }

        SecureDocument doc = secureDocumentRepository.findByIdAndIsDeletedFalse(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Documento não encontrado: " + documentId));

        authorizationService.enforceDocumentAccess(doc, currentUserId);

        User user = userRepository.findById(currentUserId).orElse(null);
        logAccess(doc, user, "VIEW_METADATA", request);

        return documentAccessLogRepository.findByDocumentIdOrderByTimestampDesc(documentId).stream()
                .map(this::mapLogToDto)
                .collect(Collectors.toList());
    }

    private void logAccess(SecureDocument doc, User user, String action, HttpServletRequest request) {
        try {
            String ipAddress = request != null ? request.getRemoteAddr() : "127.0.0.1";
            String userAgent = request != null ? request.getHeader("User-Agent") : "LegaWork-System";

            DocumentAccessLog log = DocumentAccessLog.builder()
                    .document(doc)
                    .user(user)
                    .action(action)
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .timestamp(LocalDateTime.now())
                    .build();

            documentAccessLogRepository.save(log);
        } catch (Exception e) {
            logger.warn("Failed to record document access log: {}", e.getMessage());
        }
    }

    private SecureDocumentDto mapToDto(SecureDocument doc) {
        return SecureDocumentDto.builder()
                .id(doc.getId())
                .contractId(doc.getContract() != null ? doc.getContract().getContractId() : null)
                .jobId(doc.getJob() != null ? doc.getJob().getJobId() : null)
                .ownerId(doc.getOwner() != null ? doc.getOwner().getId() : null)
                .ownerName(doc.getOwner() != null ? doc.getOwner().getFirstName() + " " + doc.getOwner().getLastName() : null)
                .fileName(doc.getFileName())
                .fileSize(doc.getFileSize())
                .contentType(doc.getContentType())
                .sha256Hash(doc.getSha256Hash())
                .classification(doc.getClassification())
                .virusScanStatus(doc.getVirusScanStatus())
                .version(doc.getVersion())
                .description(doc.getDescription())
                .createdAt(doc.getCreatedAt())
                .expiresAt(doc.getExpiresAt())
                .build();
    }

    private DocumentAccessLogDto mapLogToDto(DocumentAccessLog log) {
        return DocumentAccessLogDto.builder()
                .id(log.getId())
                .documentId(log.getDocument() != null ? log.getDocument().getId() : null)
                .userId(log.getUser() != null ? log.getUser().getId() : null)
                .userName(log.getUser() != null ? log.getUser().getFirstName() + " " + log.getUser().getLastName() : null)
                .action(log.getAction())
                .timestamp(log.getTimestamp())
                .ipAddress(log.getIpAddress())
                .userAgent(log.getUserAgent())
                .build();
    }
}
