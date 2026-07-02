package com.activecourses.upwork.mapper;

import org.springframework.stereotype.Component;

import com.activecourses.upwork.dto.JobDTO;
import com.activecourses.upwork.model.*;

@Component
public class JobMapper implements Mapper<Job, JobDTO> {

    @Override
    public JobDTO mapTo(Job job) {
        JobDTO jobDTO = new JobDTO();
        jobDTO.setJobId(job.getJobId());
        jobDTO.setTitle(job.getTitle());
        jobDTO.setDescription(job.getDescription());
        jobDTO.setBudget(job.getBudget());
        jobDTO.setJobType(job.getJobType());
        jobDTO.setUrgency(job.getUrgency());
        jobDTO.setConfidentiality(job.getConfidentiality());
        jobDTO.setEstimatedValue(job.getEstimatedValue());
        jobDTO.setDeadline(job.getDeadline());
        if (job.getSpecialty() != null) {
            jobDTO.setSpecialtyId(job.getSpecialty().getId());
        }
        if (job.getClient() != null) {
            jobDTO.setClientName(job.getClient().getFirstName() + " " + job.getClient().getLastName());
        }
        if (job.getSkills() != null) {
            jobDTO.setSkillNames(job.getSkills().stream().map(Skill::getName).collect(java.util.stream.Collectors.toSet()));
        }
        return jobDTO;
    }

    @Override
    public Job mapFrom(JobDTO jobDTO) {
        Job job = new Job();
        job.setTitle(jobDTO.getTitle());
        job.setDescription(jobDTO.getDescription());
        job.setBudget(jobDTO.getBudget());
        job.setJobType(jobDTO.getJobType());
        job.setStatus(JobStatus.Open);
        job.setUrgency(jobDTO.getUrgency() != null ? jobDTO.getUrgency() : UrgencyLevel.Medium);
        job.setConfidentiality(jobDTO.getConfidentiality() != null ? jobDTO.getConfidentiality() : ConfidentialityLevel.Public);
        job.setEstimatedValue(jobDTO.getEstimatedValue());
        job.setDeadline(jobDTO.getDeadline());
        return job;
    }
}
