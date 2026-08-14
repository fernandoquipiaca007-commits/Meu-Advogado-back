package com.activecourses.upwork.service.conflict;

import com.activecourses.upwork.dto.ConflictCheckDto;
import com.activecourses.upwork.dto.ConflictCheckRequestDto;
import com.activecourses.upwork.model.ConflictCheck;
import com.activecourses.upwork.model.ConflictStatus;
import com.activecourses.upwork.model.Job;
import com.activecourses.upwork.model.User;
import com.activecourses.upwork.repository.conflict.ConflictCheckRepository;
import com.activecourses.upwork.repository.job.JobRepository;
import com.activecourses.upwork.repository.user.UserRepository;
import com.activecourses.upwork.service.authentication.AuthService;
import com.activecourses.upwork.service.security.AuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ConflictServiceImpl implements ConflictService {

    private final ConflictCheckRepository conflictCheckRepository;
    private final JobRepository jobRepository;
    private final UserRepository userRepository;
    private final AuthService authService;
    private final AuthorizationService authorizationService;

    @Override
    @Transactional
    public ConflictCheckDto checkConflict(Integer jobId, Integer lawyerId) {
        if (jobId == null) {
            throw new IllegalArgumentException("Job ID is required");
        }

        Integer currentUserId = authService.getCurrentUserId();
        if (currentUserId == null) {
            throw new AccessDeniedException("Authentication required");
        }

        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));

        Integer targetLawyerId = lawyerId != null ? lawyerId : currentUserId;

        // Security check: only client owner or the lawyer themselves can check
        boolean isOwner = job.getClient() != null && currentUserId.equals(job.getClient().getId());
        boolean isTargetLawyer = currentUserId.equals(targetLawyerId);

        if (!isOwner && !isTargetLawyer) {
            throw new AccessDeniedException("You are not authorized to check conflict for this job");
        }

        User lawyer = userRepository.findById(targetLawyerId)
                .orElseThrow(() -> new IllegalArgumentException("Lawyer not found: " + targetLawyerId));

        ConflictCheck check = conflictCheckRepository.findByJobJobIdAndLawyerId(jobId, targetLawyerId)
                .orElseGet(() -> {
                    ConflictCheck newCheck = ConflictCheck.builder()
                            .job(job)
                            .lawyer(lawyer)
                            .status(ConflictStatus.CLEAR)
                            .createdAt(LocalDateTime.now())
                            .resolvedAt(LocalDateTime.now())
                            .build();
                    return conflictCheckRepository.save(newCheck);
                });

        return mapToDto(check);
    }

    @Override
    @Transactional
    public ConflictCheckDto declareConflict(ConflictCheckRequestDto request) {
        if (request.getJobId() == null) {
            throw new IllegalArgumentException("Job ID is required");
        }

        Integer currentUserId = authService.getCurrentUserId();
        if (currentUserId == null) {
            throw new AccessDeniedException("Authentication required");
        }

        Job job = jobRepository.findById(request.getJobId())
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + request.getJobId()));

        Integer targetLawyerId = request.getLawyerId() != null ? request.getLawyerId() : currentUserId;

        // Only the lawyer can declare their conflict status
        if (!currentUserId.equals(targetLawyerId)) {
            throw new AccessDeniedException("Lawyers can only declare conflict for themselves");
        }

        User lawyer = userRepository.findById(targetLawyerId)
                .orElseThrow(() -> new IllegalArgumentException("Lawyer not found: " + targetLawyerId));

        ConflictStatus newStatus = request.getStatus() != null ? request.getStatus() : ConflictStatus.CLEAR;

        // Sanitize reason to prevent private data leakage
        String maskedReason = sanitizeReason(request.getReason(), newStatus);

        ConflictCheck check = conflictCheckRepository.findByJobJobIdAndLawyerId(request.getJobId(), targetLawyerId)
                .orElse(ConflictCheck.builder()
                        .job(job)
                        .lawyer(lawyer)
                        .createdAt(LocalDateTime.now())
                        .build());

        check.setStatus(newStatus);
        check.setReasonMasked(maskedReason);
        check.setResolvedAt(LocalDateTime.now());

        check = conflictCheckRepository.save(check);
        return mapToDto(check);
    }

    @Override
    public Optional<ConflictCheckDto> getConflictStatus(Integer jobId, Integer lawyerId) {
        if (jobId == null) {
            return Optional.empty();
        }

        Integer currentUserId = authService.getCurrentUserId();
        Integer targetLawyerId = lawyerId != null ? lawyerId : currentUserId;
        if (targetLawyerId == null) {
            return Optional.empty();
        }

        return conflictCheckRepository.findByJobJobIdAndLawyerId(jobId, targetLawyerId)
                .map(this::mapToDto);
    }

    private String sanitizeReason(String rawReason, ConflictStatus status) {
        if (status == ConflictStatus.BLOCKED) {
            return "Impedimento ético-profissional detectado nos termos do Código de Ética da OAB.";
        }
        if (status == ConflictStatus.CONSENT_REQUIRED) {
            return "Necessidade de consentimento prévio nos termos do Código de Ética da OAB.";
        }
        if (rawReason == null || rawReason.trim().isEmpty()) {
            return null;
        }
        // Limit length and keep generic
        String trimmed = rawReason.trim();
        return trimmed.length() > 255 ? trimmed.substring(0, 255) : trimmed;
    }

    private ConflictCheckDto mapToDto(ConflictCheck entity) {
        return ConflictCheckDto.builder()
                .id(entity.getId())
                .jobId(entity.getJob() != null ? entity.getJob().getJobId() : null)
                .lawyerId(entity.getLawyer() != null ? entity.getLawyer().getId() : null)
                .lawyerName(entity.getLawyer() != null ? entity.getLawyer().getFirstName() + " " + entity.getLawyer().getLastName() : null)
                .status(entity.getStatus())
                .reasonMasked(entity.getReasonMasked())
                .createdAt(entity.getCreatedAt())
                .resolvedAt(entity.getResolvedAt())
                .build();
    }
}
