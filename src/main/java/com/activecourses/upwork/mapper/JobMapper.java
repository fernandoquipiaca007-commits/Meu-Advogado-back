package com.activecourses.upwork.mapper;

import org.springframework.stereotype.Component;

import com.activecourses.upwork.dto.JobDTO;
import com.activecourses.upwork.dto.JobDetailDto;
import com.activecourses.upwork.dto.JobDiscoveryDto;
import com.activecourses.upwork.model.*;

import java.util.Set;
import java.util.stream.Collectors;

@Component
public class JobMapper implements Mapper<Job, JobDTO> {

    @Override
    public JobDTO mapTo(Job job) {
        if (job == null) {
            return null;
        }
        JobDTO jobDTO = new JobDTO();
        jobDTO.setJobId(job.getJobId());
        jobDTO.setTitle(job.getTitle());
        jobDTO.setDescription(job.getDescription());
        jobDTO.setBudget(job.getBudget());
        jobDTO.setJobType(job.getJobType());
        jobDTO.setUrgency(job.getUrgency());
        jobDTO.setConfidentiality(job.getConfidentiality());
        jobDTO.setVisibility(job.getVisibility());
        jobDTO.setSensitivity(job.getSensitivity());
        jobDTO.setModerationStatus(job.getModerationStatus());
        jobDTO.setModerationReason(job.getModerationReason());
        jobDTO.setStatus(job.getStatus());
        jobDTO.setEstimatedValue(job.getEstimatedValue());
        jobDTO.setDeadline(job.getDeadline());
        jobDTO.setCreatedAt(job.getCreatedAt());

        if (job.getSpecialty() != null) {
            jobDTO.setSpecialtyId(job.getSpecialty().getId());
        }
        if (job.getClient() != null) {
            jobDTO.setClientId(job.getClient().getId());
            jobDTO.setClientName(job.getClient().getFirstName() + " " + job.getClient().getLastName());
        }
        if (job.getSkills() != null) {
            jobDTO.setSkillNames(job.getSkills().stream().map(Skill::getName).collect(Collectors.toSet()));
        }
        return jobDTO;
    }

    @Override
    public Job mapFrom(JobDTO jobDTO) {
        if (jobDTO == null) {
            return null;
        }
        Job job = new Job();
        job.setTitle(jobDTO.getTitle());
        job.setDescription(jobDTO.getDescription());
        job.setBudget(jobDTO.getBudget());
        job.setJobType(jobDTO.getJobType() != null ? jobDTO.getJobType() : JobType.Fixed);
        job.setStatus(jobDTO.getStatus() != null ? jobDTO.getStatus() : JobStatus.Open);
        job.setUrgency(jobDTO.getUrgency() != null ? jobDTO.getUrgency() : UrgencyLevel.Medium);
        job.setConfidentiality(jobDTO.getConfidentiality() != null ? jobDTO.getConfidentiality() : ConfidentialityLevel.Public);
        job.setVisibility(jobDTO.getVisibility() != null ? jobDTO.getVisibility() : JobVisibility.PRIVATE);
        job.setSensitivity(jobDTO.getSensitivity() != null ? jobDTO.getSensitivity() : JobSensitivity.STANDARD);
        job.setModerationStatus(jobDTO.getModerationStatus() != null ? jobDTO.getModerationStatus() : ModerationStatus.PENDING_REVIEW);
        job.setModerationReason(jobDTO.getModerationReason());
        job.setEstimatedValue(jobDTO.getEstimatedValue());
        job.setDeadline(jobDTO.getDeadline());
        return job;
    }

    public JobDiscoveryDto toDiscoveryDto(Job job) {
        if (job == null) {
            return null;
        }

        String summary = null;
        if (job.getDescription() != null) {
            String desc = job.getDescription().trim();
            if (desc.length() > 200) {
                summary = desc.substring(0, 197) + "...";
            } else {
                summary = desc;
            }
        }

        String specialtyName = null;
        Integer specialtyId = null;
        if (job.getSpecialty() != null) {
            specialtyId = job.getSpecialty().getId();
            specialtyName = job.getSpecialty().getName();
        }

        String locationCity = null;
        String locationState = null;
        if (job.getClient() != null && job.getClient().getUserProfile() != null) {
            locationCity = job.getClient().getUserProfile().getLocation();
            locationState = job.getClient().getUserProfile().getOabState();
        }

        return JobDiscoveryDto.builder()
                .jobId(job.getJobId())
                .title(job.getTitle())
                .summary(summary)
                .description(summary)
                .specialtyId(specialtyId)
                .specialtyName(specialtyName)
                .specialty(specialtyName)
                .urgency(job.getUrgency())
                .budget(job.getBudget())
                .budgetMin(job.getBudget())
                .budgetMax(job.getEstimatedValue() != null ? job.getEstimatedValue() : job.getBudget())
                .jobType(job.getJobType())
                .budgetType(job.getJobType())
                .estimatedValue(job.getEstimatedValue())
                .locationCity(locationCity)
                .locationState(locationState)
                .createdAt(job.getCreatedAt())
                .visibility(job.getVisibility())
                .status(job.getStatus())
                .skillNames(job.getSkills() != null
                        ? job.getSkills().stream().map(Skill::getName).collect(Collectors.toSet())
                        : Set.of())
                .deadline(job.getDeadline())
                .build();
    }

    public JobDetailDto toDetailDto(Job job, boolean isOwner, boolean canPropose) {
        if (job == null) {
            return null;
        }

        String specialtyName = null;
        Integer specialtyId = null;
        if (job.getSpecialty() != null) {
            specialtyId = job.getSpecialty().getId();
            specialtyName = job.getSpecialty().getName();
        }

        Integer clientId = null;
        String clientName = null;
        if (job.getClient() != null) {
            clientId = job.getClient().getId();
            clientName = job.getClient().getFirstName() + " " + job.getClient().getLastName();
        }

        return JobDetailDto.builder()
                .jobId(job.getJobId())
                .title(job.getTitle())
                .description(job.getDescription())
                .budget(job.getBudget())
                .jobType(job.getJobType())
                .status(job.getStatus())
                .urgency(job.getUrgency())
                .confidentiality(job.getConfidentiality())
                .visibility(job.getVisibility())
                .sensitivity(job.getSensitivity())
                .moderationStatus(job.getModerationStatus())
                .moderationReason(job.getModerationReason())
                .estimatedValue(job.getEstimatedValue())
                .deadline(job.getDeadline())
                .specialtyId(specialtyId)
                .specialtyName(specialtyName)
                .clientId(clientId)
                .clientName(clientName)
                .skillNames(job.getSkills() != null
                        ? job.getSkills().stream().map(Skill::getName).collect(Collectors.toSet())
                        : Set.of())
                .createdAt(job.getCreatedAt())
                .isOwner(isOwner)
                .canPropose(canPropose)
                .build();
    }
}
