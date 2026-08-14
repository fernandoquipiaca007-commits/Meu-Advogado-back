package com.activecourses.upwork.service.contract;

import com.activecourses.upwork.dto.ContractDTO;
import com.activecourses.upwork.model.*;
import com.activecourses.upwork.repository.contract.ContractMilestoneRepository;
import com.activecourses.upwork.repository.contract.ContractRepository;
import com.activecourses.upwork.repository.job.ProposalRepository;
import com.activecourses.upwork.service.authentication.AuthService;
import com.activecourses.upwork.service.notification.NotificationService;
import com.activecourses.upwork.service.security.AuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ContractServiceVerificationTest {

    @Mock
    private ContractRepository contractRepository;

    @Mock
    private ContractMilestoneRepository milestoneRepository;

    @Mock
    private ProposalRepository proposalRepository;

    @Mock
    private AuthService authService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private AuthorizationService authorizationService;

    @InjectMocks
    private ContractServiceImpl contractService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testCreateContract_SuccessWhenLawyerVerified() {
        int proposalId = 20;
        User lawyer = User.builder().id(5).firstName("Ana").lastName("Silva").build();
        User client = User.builder().id(1).firstName("Carlos").lastName("Oliveira").build();
        Job job = Job.builder().jobId(10).title("Revisão de Contrato").client(client).budget(BigDecimal.valueOf(1000)).build();

        Proposal proposal = Proposal.builder()
                .proposalId(proposalId)
                .status(ProposalStatus.Accepted)
                .job(job)
                .lawyer(lawyer)
                .totalValue(BigDecimal.valueOf(1000))
                .build();

        when(proposalRepository.findById(proposalId)).thenReturn(Optional.of(proposal));
        doNothing().when(authorizationService).enforceVerifiedLawyer(lawyer.getId());
        when(contractRepository.save(any(Contract.class))).thenAnswer(invocation -> {
            Contract c = invocation.getArgument(0);
            c.setContractId(201);
            return c;
        });

        ContractDTO contractDTO = contractService.createContract(proposalId);

        assertNotNull(contractDTO);
        assertEquals(201, contractDTO.getContractId());
        verify(authorizationService, times(1)).enforceVerifiedLawyer(5);
        verify(contractRepository, times(1)).save(any(Contract.class));
    }

    @Test
    void testCreateContract_Throws403WhenLawyerNotVerified() {
        int proposalId = 21;
        User lawyer = User.builder().id(6).firstName("João").lastName("Santos").build();
        User client = User.builder().id(1).firstName("Carlos").lastName("Oliveira").build();
        Job job = Job.builder().jobId(10).title("Revisão de Contrato").client(client).budget(BigDecimal.valueOf(1000)).build();

        Proposal proposal = Proposal.builder()
                .proposalId(proposalId)
                .status(ProposalStatus.Accepted)
                .job(job)
                .lawyer(lawyer)
                .build();

        when(proposalRepository.findById(proposalId)).thenReturn(Optional.of(proposal));
        doThrow(new AccessDeniedException("Lawyer verification required. Current status: EXPIRED"))
                .when(authorizationService).enforceVerifiedLawyer(lawyer.getId());

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> contractService.createContract(proposalId));

        assertTrue(ex.getMessage().contains("Lawyer verification required"));
        verify(contractRepository, never()).save(any(Contract.class));
    }
}
