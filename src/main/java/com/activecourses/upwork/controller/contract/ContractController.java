package com.activecourses.upwork.controller.contract;

import com.activecourses.upwork.dto.ContractDTO;
import com.activecourses.upwork.dto.ContractMilestoneDTO;
import com.activecourses.upwork.dto.ResponseDto;
import com.activecourses.upwork.service.authentication.AuthService;
import com.activecourses.upwork.service.contract.ContractService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Mandatos", description = "Gestão de Contratos/Mandatos Jurídicos")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/contracts/")
public class ContractController {

    private final ContractService contractService;
    private final AuthService authService;

    @Operation(summary = "Criar contrato", description = "Cria um contrato a partir de uma proposta aceite",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('CLIENT') or hasRole('ADMIN')")
    @PostMapping("/create/{proposalId}")
    public ResponseEntity<ResponseDto> createContract(@PathVariable int proposalId) {
        ContractDTO contract = contractService.createContract(proposalId);
        return buildResponse(HttpStatus.CREATED, true, contract, null);
    }

    @Operation(summary = "Meus contratos", description = "Lista contratos onde o utilizador é cliente ou advogado",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/my")
    public ResponseEntity<ResponseDto> getMyContracts() {
        var contracts = contractService.getMyContracts();
        return buildResponse(HttpStatus.OK, true, contracts, null);
    }

    @Operation(summary = "Detalhe do contrato")
    @GetMapping("/{contractId}")
    public ResponseEntity<ResponseDto> getContractById(@PathVariable int contractId) {
        return contractService.getContractById(contractId)
                .map(c -> buildResponse(HttpStatus.OK, true, c, null))
                .orElse(buildResponse(HttpStatus.NOT_FOUND, false, null, "Contract not found"));
    }

    @Operation(summary = "Completar contrato", description = "Marca contrato como concluído",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('CLIENT') or hasRole('LAWYER')")
    @PostMapping("/{contractId}/complete")
    public ResponseEntity<ResponseDto> completeContract(@PathVariable int contractId) {
        ContractDTO contract = contractService.completeContract(contractId);
        return buildResponse(HttpStatus.OK, true, contract, null);
    }

    @Operation(summary = "Encerrar contrato", description = "Encerra contrato antecipadamente",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('CLIENT')")
    @PostMapping("/{contractId}/terminate")
    public ResponseEntity<ResponseDto> terminateContract(@PathVariable int contractId) {
        ContractDTO contract = contractService.terminateContract(contractId);
        return buildResponse(HttpStatus.OK, true, contract, null);
    }

    @Operation(summary = "Cancelar contrato", description = "Cancela contrato",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{contractId}/cancel")
    public ResponseEntity<ResponseDto> cancelContract(@PathVariable int contractId) {
        ContractDTO contract = contractService.cancelContract(contractId);
        return buildResponse(HttpStatus.OK, true, contract, null);
    }

    @Operation(summary = "Completar milestone", description = "Marca uma etapa como concluída",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('CLIENT') or hasRole('LAWYER')")
    @PostMapping("/milestones/{milestoneId}/complete")
    public ResponseEntity<ResponseDto> completeMilestone(@PathVariable int milestoneId) {
        ContractMilestoneDTO milestone = contractService.completeMilestone(milestoneId);
        return buildResponse(HttpStatus.OK, true, milestone, null);
    }

    @Operation(summary = "Milestones do contrato")
    @GetMapping("/{contractId}/milestones")
    public ResponseEntity<ResponseDto> getMilestones(@PathVariable int contractId) {
        var milestones = contractService.getMilestones(contractId);
        return buildResponse(HttpStatus.OK, true, milestones, null);
    }

    private ResponseEntity<ResponseDto> buildResponse(HttpStatus status, boolean success, Object data, Object error) {
        return ResponseEntity.status(status)
                .body(ResponseDto.builder().status(status).success(success).data(data).error(error).build());
    }
}
