package com.activecourses.upwork.service.document;

import com.activecourses.upwork.model.Contract;
import com.activecourses.upwork.model.ContractDocument;
import com.activecourses.upwork.model.User;
import com.activecourses.upwork.repository.contract.ContractRepository;
import com.activecourses.upwork.repository.document.ContractDocumentRepository;
import com.activecourses.upwork.repository.user.UserRepository;
import com.activecourses.upwork.service.authentication.AuthService;
import com.activecourses.upwork.service.document.DocumentService.DocumentDownloadInfo;
import com.activecourses.upwork.service.security.AuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DocumentServiceAuthorizationTest {

    @Mock
    private ContractDocumentRepository documentRepository;

    @Mock
    private ContractRepository contractRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthService authService;

    @Mock
    private AuthorizationService authorizationService;

    @InjectMocks
    private DocumentServiceImpl documentService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testGetDocumentDownloadInfo_SuccessWhenParticipant() {
        int documentId = 100;
        int contractId = 50;
        int userId = 5;

        Contract contract = Contract.builder().contractId(contractId).build();
        ContractDocument doc = ContractDocument.builder()
                .documentId(documentId)
                .contract(contract)
                .fileName("contrato.pdf")
                .contentType("application/pdf")
                .storagePath("uploads/documents/uuid.pdf")
                .build();

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(doc));
        when(authService.getCurrentUserId()).thenReturn(userId);
        doNothing().when(authorizationService).enforceContractParticipant(contractId, userId);

        DocumentDownloadInfo info = documentService.getDocumentDownloadInfo(documentId);

        assertNotNull(info);
        assertEquals("contrato.pdf", info.fileName());
        assertEquals("application/pdf", info.contentType());
        verify(authorizationService, times(1)).enforceContractParticipant(contractId, userId);
    }

    @Test
    void testGetDocumentDownloadInfo_Throws403WhenNotParticipant() {
        int documentId = 100;
        int contractId = 50;
        int userId = 99;

        Contract contract = Contract.builder().contractId(contractId).build();
        ContractDocument doc = ContractDocument.builder()
                .documentId(documentId)
                .contract(contract)
                .fileName("contrato.pdf")
                .contentType("application/pdf")
                .storagePath("uploads/documents/uuid.pdf")
                .build();

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(doc));
        when(authService.getCurrentUserId()).thenReturn(userId);
        doThrow(new AccessDeniedException("Access denied to contract " + contractId))
                .when(authorizationService).enforceContractParticipant(contractId, userId);

        assertThrows(AccessDeniedException.class,
                () -> documentService.getDocumentDownloadInfo(documentId));
    }
}
