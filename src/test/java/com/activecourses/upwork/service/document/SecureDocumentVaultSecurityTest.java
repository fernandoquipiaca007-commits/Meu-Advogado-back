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
import com.activecourses.upwork.service.document.SecureDocumentService.SecureDownloadInfo;
import com.activecourses.upwork.service.security.AuthorizationService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SecureDocumentVaultSecurityTest {

    @Mock
    private SecureDocumentRepository secureDocumentRepository;

    @Mock
    private DocumentAccessLogRepository documentAccessLogRepository;

    @Mock
    private ContractRepository contractRepository;

    @Mock
    private JobRepository jobRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthService authService;

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private HttpServletRequest httpRequest;

    @InjectMocks
    private SecureDocumentServiceImpl secureDocumentService;

    @TempDir
    Path tempDir;

    private User client;
    private User lawyer;
    private Contract contract;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(secureDocumentService, "uploadDir", tempDir.toString() + File.separator);

        client = User.builder().id(10).firstName("Maria").lastName("Silva").build();
        lawyer = User.builder().id(20).firstName("Dr. Carlos").lastName("Advogado").build();

        contract = Contract.builder()
                .contractId(100)
                .client(client)
                .lawyer(lawyer)
                .title("Mandato 100")
                .build();

        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(httpRequest.getHeader("User-Agent")).thenReturn("Test-Browser");
    }

    @Test
    @DisplayName("uploadSecureDocument deve calcular SHA-256 e registrar log de UPLOAD")
    void testUploadSecureDocument_Success() throws Exception {
        byte[] content = "Conteúdo Confidencial de Petição Inicial".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "peticao.pdf", "application/pdf", content
        );

        when(authService.getCurrentUserId()).thenReturn(10);
        when(userRepository.findById(10)).thenReturn(Optional.of(client));
        when(contractRepository.findById(100)).thenReturn(Optional.of(contract));
        doNothing().when(authorizationService).enforceContractParticipant(100, 10);

        when(secureDocumentRepository.save(any(SecureDocument.class))).thenAnswer(inv -> {
            SecureDocument doc = inv.getArgument(0);
            doc.setId(1L);
            return doc;
        });

        SecureDocumentDto dto = secureDocumentService.uploadSecureDocument(
                file, 100, null, DocumentClassification.CONFIDENTIAL, "Petição inicial", httpRequest
        );

        assertNotNull(dto);
        assertEquals("peticao.pdf", dto.getFileName());
        assertEquals(DocumentClassification.CONFIDENTIAL, dto.getClassification());
        assertNotNull(dto.getSha256Hash());
        assertEquals(64, dto.getSha256Hash().length());

        // Verify UPLOAD audit log recorded
        verify(documentAccessLogRepository, times(1)).save(argThat(log ->
                "UPLOAD".equals(log.getAction()) && log.getDocument() != null
        ));
    }

    @Test
    @DisplayName("downloadSecureDocument deve exigir autorização e registrar log de DOWNLOAD")
    void testDownloadSecureDocument_Success() throws IOException {
        Path tempFile = tempDir.resolve("test_download.pdf");
        Files.write(tempFile, "Dados do Processo".getBytes());

        SecureDocument doc = SecureDocument.builder()
                .id(1L)
                .contract(contract)
                .owner(client)
                .fileName("test_download.pdf")
                .fileSize(Files.size(tempFile))
                .contentType("application/pdf")
                .storagePath(tempFile.toAbsolutePath().toString())
                .sha256Hash("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
                .classification(DocumentClassification.CONFIDENTIAL)
                .virusScanStatus(VirusScanStatus.CLEAN)
                .isDeleted(false)
                .build();

        when(authService.getCurrentUserId()).thenReturn(20);
        when(userRepository.findById(20)).thenReturn(Optional.of(lawyer));
        when(secureDocumentRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(doc));
        doNothing().when(authorizationService).enforceDocumentAccess(doc, 20);

        SecureDownloadInfo info = secureDocumentService.downloadSecureDocument(1L, httpRequest);

        assertNotNull(info);
        assertEquals("test_download.pdf", info.fileName());
        assertEquals("application/pdf", info.contentType());
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", info.sha256Hash());

        // Verify DOWNLOAD log recorded
        verify(documentAccessLogRepository, times(1)).save(argThat(log ->
                "DOWNLOAD".equals(log.getAction())
        ));
    }

    @Test
    @DisplayName("downloadSecureDocument deve lançar AccessDeniedException para usuário não autorizado")
    void testDownloadSecureDocument_ThrowsAccessDeniedForUnauthorized() {
        SecureDocument doc = SecureDocument.builder()
                .id(1L)
                .contract(contract)
                .owner(client)
                .isDeleted(false)
                .virusScanStatus(VirusScanStatus.CLEAN)
                .build();

        when(authService.getCurrentUserId()).thenReturn(99); // Unauthorized user
        when(secureDocumentRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(doc));
        doThrow(new AccessDeniedException("Access denied: You do not have permission to access this document."))
                .when(authorizationService).enforceDocumentAccess(doc, 99);

        assertThrows(AccessDeniedException.class, () -> secureDocumentService.downloadSecureDocument(1L, httpRequest));
    }
}
