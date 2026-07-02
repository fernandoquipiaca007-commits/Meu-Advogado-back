package com.activecourses.upwork.controller.job;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.activecourses.upwork.dto.JobDTO;
import com.activecourses.upwork.dto.ResponseDto;
import com.activecourses.upwork.mapper.JobMapper;
import com.activecourses.upwork.model.Job;
import com.activecourses.upwork.service.job.JobService;
import com.activecourses.upwork.service.authentication.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Casos Jurídicos", description = "Legal Case Management API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/jobs/")
public class JobController {
    private final JobService jobService;
    private final JobMapper jobMapper;
    private final AuthService authService;

    @Operation(summary = "Listar casos ativos", description = "Retrieve all active (non-archived) legal cases")
    @GetMapping
    public ResponseEntity<ResponseDto> getActiveJobs() {
        var jobs = jobService.getActiveJobs().stream()
                .map(jobMapper::mapTo)
                .toList();
        return buildResponse(HttpStatus.OK, true, jobs, null);
    }

    @Operation(summary = "Listar todos os casos", description = "Retrieve all legal cases (including archived)")
    @GetMapping("/all")
    public ResponseEntity<ResponseDto> getAllJobs() {
        var jobs = jobService.getAllJobs().stream()
                .map(jobMapper::mapTo)
                .toList();
        return buildResponse(HttpStatus.OK, true, jobs, null);
    }

    @Operation(summary = "Detalhe do caso", description = "Retrieve a specific legal case by its ID")
    @GetMapping("/{jobId}")
    public ResponseEntity<ResponseDto> getJobById(@PathVariable int jobId) {
        var job = jobService.getJobById(jobId);
        if (job.isPresent()) {
            return buildResponse(HttpStatus.OK, true, jobMapper.mapTo(job.get()), null);
        }
        return buildResponse(HttpStatus.NOT_FOUND, false, null, "Legal case not found.");
    }

    @Operation(summary = "Criar caso jurídico", description = "Creates a new legal case",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('ADMIN') or hasRole('CLIENT') or hasRole('LAWYER') or hasRole('FIRM') or hasRole('FREELANCER')")
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

    private ResponseEntity<ResponseDto> buildResponse(
            HttpStatus status, boolean success, Object data, Object error) {
        return ResponseEntity.status(status)
                .body(ResponseDto.builder().status(status).success(success).data(data).error(error).build());
    }
}
