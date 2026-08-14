package com.activecourses.upwork.controller.job;

import com.activecourses.upwork.dto.JobDTO;
import com.activecourses.upwork.dto.JobDiscoveryDto;
import com.activecourses.upwork.dto.PageResponseDto;
import com.activecourses.upwork.dto.ResponseDto;
import com.activecourses.upwork.model.UrgencyLevel;
import com.activecourses.upwork.service.authentication.AuthService;
import com.activecourses.upwork.service.job.JobService;
import com.activecourses.upwork.service.security.AuthorizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Casos Jurídicos (Descoberta)", description = "Discovery and Case Management API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/cases")
public class CaseController {

    private final JobService jobService;
    private final AuthService authService;
    private final AuthorizationService authorizationService;

    @Operation(summary = "Catálogo de Descoberta Canônico", description = "Canonical paginated sanitized discovery endpoint for verified legal professionals")
    @PreAuthorize("hasRole('LAWYER') or hasRole('FREELANCER') or hasAuthority('ROLE_LAWYER') or hasAuthority('ROLE_FREELANCER')")
    @GetMapping("/discovery")
    public ResponseEntity<ResponseDto> getDiscoveryCases(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Integer specialtyId,
            @RequestParam(required = false) UrgencyLevel urgency) {

        Integer currentUserId = authService.getCurrentUserId();
        authorizationService.enforceVerifiedLawyer(currentUserId);

        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<JobDiscoveryDto> discoveryPage = jobService.getDiscoveryCases(specialtyId, urgency, pageable);
        return ResponseEntity.ok(
                ResponseDto.builder()
                        .status(HttpStatus.OK)
                        .success(true)
                        .data(PageResponseDto.from(discoveryPage))
                        .build()
        );
    }

    @Operation(summary = "Detalhe do Caso Canônico", description = "Retrieve legal case details for authorized users")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{caseId}")
    public ResponseEntity<ResponseDto> getCaseById(@PathVariable int caseId) {
        Integer currentUserId = authService.getCurrentUserId();
        JobDTO jobDTO = jobService.getJobDetail(caseId, currentUserId);
        return ResponseEntity.ok(
                ResponseDto.builder()
                        .status(HttpStatus.OK)
                        .success(true)
                        .data(jobDTO)
                        .build()
        );
    }
}
