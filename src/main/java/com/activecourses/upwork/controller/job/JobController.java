package com.activecourses.upwork.controller.job;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.activecourses.upwork.dto.JobDTO;
import com.activecourses.upwork.dto.JobDiscoveryDto;
import com.activecourses.upwork.dto.PageResponseDto;
import com.activecourses.upwork.dto.ResponseDto;
import com.activecourses.upwork.mapper.JobMapper;
import com.activecourses.upwork.model.Job;
import com.activecourses.upwork.model.UrgencyLevel;
import com.activecourses.upwork.service.job.JobService;
import com.activecourses.upwork.service.security.AuthorizationService;
import com.activecourses.upwork.service.authentication.AuthService;
import com.activecourses.upwork.service.notification.NotificationService;
import com.activecourses.upwork.model.NotificationType;
import com.activecourses.upwork.model.User;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import java.util.Map;

@Tag(name = "Casos Jurídicos", description = "Legal Case Management API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/jobs")
public class JobController {
    private final JobService jobService;
    private final JobMapper jobMapper;
    private final AuthService authService;
    private final AuthorizationService authorizationService;
    private final NotificationService notificationService;

    @Operation(summary = "Catálogo de Descoberta Sanitizado", description = "Retrieve paginated sanitized discovery cases for verified lawyers")
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
        return buildResponse(HttpStatus.OK, true, PageResponseDto.from(discoveryPage), null);
    }

    @Operation(summary = "Listar casos ativos (Sanitizados)", description = "Retrieve active legal cases as sanitized discovery DTOs")
    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public ResponseEntity<ResponseDto> getActiveJobs() {
        var jobs = jobService.getActiveJobs().stream()
                .map(jobMapper::toDiscoveryDto)
                .toList();
        return buildResponse(HttpStatus.OK, true, jobs, null);
    }

    @Operation(summary = "Listar todos os casos (Depreciado/Sanitizado)", description = "Retrieve legal cases as sanitized discovery DTOs")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/all")
    public ResponseEntity<ResponseDto> getAllJobs() {
        var jobs = jobService.getAllJobs().stream()
                .map(jobMapper::toDiscoveryDto)
                .toList();
        return buildResponse(HttpStatus.OK, true, jobs, null);
    }

    @Operation(summary = "Detalhe do caso", description = "Retrieve full legal case details for authorized users")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{jobId}")
    public ResponseEntity<ResponseDto> getJobById(@PathVariable int jobId) {
        Integer currentUserId = authService.getCurrentUserId();
        JobDTO jobDTO = jobService.getJobDetail(jobId, currentUserId);
        return buildResponse(HttpStatus.OK, true, jobDTO, null);
    }

    @Operation(summary = "Criar caso jurídico", description = "Creates a new legal case",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('ADMIN') or hasRole('CLIENT') or hasRole('LAWYER') or hasRole('FIRM') or hasRole('FREELANCER') or hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_CLIENT') or hasAuthority('ROLE_LAWYER') or hasAuthority('ROLE_FIRM') or hasAuthority('ROLE_FREELANCER') or isAuthenticated()")
    @PostMapping("/post")
    public ResponseEntity<ResponseDto> createJob(@Valid @RequestBody JobDTO jobDTO) {
        Job createdJob = jobService.createJob(jobDTO);
        return buildResponse(HttpStatus.CREATED, true, jobMapper.mapTo(createdJob), null);
    }

    @Operation(summary = "Atualizar caso jurídico", description = "Update an existing legal case",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('CLIENT') or hasRole('LAWYER') or hasRole('FIRM')")
    @PutMapping("/{jobId}")
    public ResponseEntity<ResponseDto> updateJob(@PathVariable int jobId, @Valid @RequestBody JobDTO jobDTO) {
        Job updatedJob = jobService.updateJob(jobId, jobDTO);
        return buildResponse(HttpStatus.OK, true, jobMapper.mapTo(updatedJob), null);
    }

    @Operation(summary = "Arquivar caso", description = "Archive a legal case",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('CLIENT')")
    @PostMapping("/{jobId}/archive")
    public ResponseEntity<ResponseDto> archiveJob(@PathVariable int jobId) {
        Job archivedJob = jobService.archiveJob(jobId);
        return buildResponse(HttpStatus.OK, true, jobMapper.mapTo(archivedJob), null);
    }

    @Operation(summary = "Fechar caso", description = "Mark a legal case as completed",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('CLIENT')")
    @PostMapping("/{jobId}/close")
    public ResponseEntity<ResponseDto> closeJob(@PathVariable int jobId) {
        Job closedJob = jobService.closeJob(jobId);
        return buildResponse(HttpStatus.OK, true, jobMapper.mapTo(closedJob), null);
    }

    @Operation(summary = "Meus casos", description = "Get all cases for the current authenticated client")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/my")
    public ResponseEntity<ResponseDto> getMyJobs() {
        Integer userId = authService.getCurrentUserId();
        if (userId == null) {
            return buildResponse(HttpStatus.UNAUTHORIZED, false, null, "Not authenticated");
        }
        var jobs = jobService.getJobsByClient(userId).stream()
                .map(jobMapper::mapTo)
                .toList();
        return buildResponse(HttpStatus.OK, true, jobs, null);
    }

    @Operation(summary = "Convidar advogado para caso", description = "Client invites a specific lawyer to submit a proposal",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{jobId}/invite")
    public ResponseEntity<ResponseDto> inviteLawyerToJob(
            @PathVariable int jobId,
            @RequestBody Map<String, Object> body) {
        Integer currentUserId = authService.getCurrentUserId();
        if (currentUserId == null) {
            return buildResponse(HttpStatus.UNAUTHORIZED, false, null, "Not authenticated");
        }

        Object lawyerIdObj = body.get("lawyerId");
        String customMessage = body.get("message") != null ? body.get("message").toString() : "";

        if (lawyerIdObj == null) {
            return buildResponse(HttpStatus.BAD_REQUEST, false, null, "lawyerId is required");
        }

        int lawyerId = Integer.parseInt(lawyerIdObj.toString());

        Job job = jobService.getJobById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Caso jurídico não encontrado: " + jobId));
        User client = job.getClient();
        String clientName = client != null ? (client.getFirstName() + " " + client.getLastName()).trim() : "Cliente";

        notificationService.createNotification(
                lawyerId,
                NotificationType.PROPOSAL_RECEIVED,
                "Convite para Demanda Jurídica",
                clientName + " convidou você para enviar uma proposta para a demanda: " + job.getTitle() + (customMessage != null && !customMessage.isBlank() ? " | Nota: \"" + customMessage + "\"" : ""),
                "job",
                jobId
        );

        return buildResponse(HttpStatus.OK, true, Map.of("invited", true, "jobId", jobId, "lawyerId", lawyerId), null);
    }

    private ResponseEntity<ResponseDto> buildResponse(
            HttpStatus status, boolean success, Object data, Object error) {
        return ResponseEntity.status(status)
                .body(ResponseDto.builder().status(status).success(success).data(data).error(error).build());
    }
}
