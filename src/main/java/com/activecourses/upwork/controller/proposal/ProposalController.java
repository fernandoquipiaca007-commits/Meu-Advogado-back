package com.activecourses.upwork.controller.proposal;

import com.activecourses.upwork.dto.ProposalDTO;
import com.activecourses.upwork.dto.ResponseDto;
import com.activecourses.upwork.service.authentication.AuthService;
import com.activecourses.upwork.service.proposal.ProposalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Propostas", description = "Sistema de Propostas Jurídicas")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/proposals/")
public class ProposalController {

    private final ProposalService proposalService;
    private final AuthService authService;

    @Operation(summary = "Enviar proposta", description = "Advogado envia proposta para um caso jurídico",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('LAWYER') or hasRole('FREELANCER')")
    @PostMapping
    public ResponseEntity<ResponseDto> createProposal(@RequestBody ProposalDTO proposalDTO) {
        ProposalDTO created = proposalService.createProposal(proposalDTO);
        return buildResponse(HttpStatus.CREATED, true, created, null);
    }

    @Operation(summary = "Propostas de um caso", description = "Lista todas as propostas de um caso específico")
    @GetMapping("/job/{jobId}")
    public ResponseEntity<ResponseDto> getProposalsByJob(@PathVariable int jobId) {
        var proposals = proposalService.getProposalsByJob(jobId);
        return buildResponse(HttpStatus.OK, true, proposals, null);
    }

    @Operation(summary = "Minhas propostas", description = "Lista as propostas enviadas pelo advogado logado",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/my")
    public ResponseEntity<ResponseDto> getMyProposals() {
        Integer userId = authService.getCurrentUserId();
        if (userId == null) return buildResponse(HttpStatus.UNAUTHORIZED, false, null, "Not authenticated");
        var proposals = proposalService.getMyProposals(userId);
        return buildResponse(HttpStatus.OK, true, proposals, null);
    }

    @Operation(summary = "Propostas dos meus casos", description = "Cliente vê propostas recebidas nos seus casos",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('CLIENT')")
    @GetMapping("/received")
    public ResponseEntity<ResponseDto> getReceivedProposals() {
        Integer userId = authService.getCurrentUserId();
        if (userId == null) return buildResponse(HttpStatus.UNAUTHORIZED, false, null, "Not authenticated");
        var proposals = proposalService.getProposalsForMyCases(userId);
        return buildResponse(HttpStatus.OK, true, proposals, null);
    }

    @Operation(summary = "Detalhe da proposta")
    @GetMapping("/{proposalId}")
    public ResponseEntity<ResponseDto> getProposalById(@PathVariable int proposalId) {
        return proposalService.getProposalById(proposalId)
                .map(p -> buildResponse(HttpStatus.OK, true, p, null))
                .orElse(buildResponse(HttpStatus.NOT_FOUND, false, null, "Proposal not found"));
    }

    @Operation(summary = "Aceitar proposta", description = "Cliente aceita uma proposta",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('CLIENT') or hasRole('ADMIN')")
    @PostMapping("/{proposalId}/accept")
    public ResponseEntity<ResponseDto> acceptProposal(@PathVariable int proposalId) {
        ProposalDTO accepted = proposalService.acceptProposal(proposalId);
        return buildResponse(HttpStatus.OK, true, accepted, null);
    }

    @Operation(summary = "Rejeitar proposta", description = "Cliente rejeita uma proposta",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('CLIENT') or hasRole('ADMIN')")
    @PostMapping("/{proposalId}/reject")
    public ResponseEntity<ResponseDto> rejectProposal(@PathVariable int proposalId) {
        ProposalDTO rejected = proposalService.rejectProposal(proposalId);
        return buildResponse(HttpStatus.OK, true, rejected, null);
    }

    @Operation(summary = "Retirar proposta", description = "Advogado retira a sua própria proposta",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('LAWYER') or hasRole('FREELANCER')")
    @PostMapping("/{proposalId}/withdraw")
    public ResponseEntity<ResponseDto> withdrawProposal(@PathVariable int proposalId) {
        ProposalDTO withdrawn = proposalService.withdrawProposal(proposalId);
        return buildResponse(HttpStatus.OK, true, withdrawn, null);
    }

    @Operation(summary = "Atualizar proposta", description = "Advogado atualiza sua proposta (apenas se pendente)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('LAWYER') or hasRole('FREELANCER')")
    @PutMapping("/{proposalId}")
    public ResponseEntity<ResponseDto> updateProposal(@PathVariable int proposalId, @RequestBody ProposalDTO proposalDTO) {
        ProposalDTO updated = proposalService.updateProposal(proposalId, proposalDTO);
        return buildResponse(HttpStatus.OK, true, updated, null);
    }

    private ResponseEntity<ResponseDto> buildResponse(HttpStatus status, boolean success, Object data, Object error) {
        return ResponseEntity.status(status)
                .body(ResponseDto.builder().status(status).success(success).data(data).error(error).build());
    }
}
