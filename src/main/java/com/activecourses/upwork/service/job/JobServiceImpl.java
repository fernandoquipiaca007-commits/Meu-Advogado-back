package com.activecourses.upwork.service.job;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.activecourses.upwork.dto.JobDTO;
import com.activecourses.upwork.mapper.JobMapper;
import com.activecourses.upwork.model.*;
import com.activecourses.upwork.repository.job.JobRepository;
import com.activecourses.upwork.repository.skill.SkillRepository;
import com.activecourses.upwork.repository.skill.SpecialtyRepository;
import com.activecourses.upwork.repository.user.UserRepository;
import com.activecourses.upwork.service.authentication.AuthService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class JobServiceImpl implements JobService {

    private final JobRepository jobRepository;
    private final UserRepository userRepository;
    private final AuthService authService;
    private final JobMapper jobMapper;
    private final SkillRepository skillRepository;
    private final SpecialtyRepository specialtyRepository;

    @Override
    public Job createJob(JobDTO jobDTO) {
        Integer clientId = authService.getCurrentUserId();
        if (clientId == null) {
            throw new IllegalStateException("User is not authenticated");
        }

        User client = userRepository.findById(clientId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + clientId));

        Job job = jobMapper.mapFrom(jobDTO);

        // Set skills
        if (jobDTO.getSkillIds() != null) {
            Set<Skill> skills = new HashSet<>();
            for (Integer skillId : jobDTO.getSkillIds()) {
                skills.add(skillRepository.findById(skillId)
                        .orElseThrow(() -> new IllegalArgumentException("Skill not found: " + skillId)));
            }
            job.setSkills(skills);
        }

        // Set specialty (legal category)
        if (jobDTO.getSpecialtyId() != null) {
            Specialty specialty = specialtyRepository.findById(jobDTO.getSpecialtyId())
                    .orElseThrow(() -> new IllegalArgumentException("Specialty not found: " + jobDTO.getSpecialtyId()));
            job.setSpecialty(specialty);
        }

        job.setClient(client);
        return jobRepository.save(job);
    }

    @Override
    public List<Job> getAllJobs() {
        return jobRepository.findAll();
    }

    @Override
    public Optional<Job> getJobById(int jobId) {
        return jobRepository.findById(jobId);
    }

    @Override
    @Transactional
    public Job updateJob(int jobId, JobDTO jobDTO) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));

        // Ownership check: only the job owner (or admin handled in controller) can update
        Integer currentUserId = authService.getCurrentUserId();
        if (currentUserId != null && !currentUserId.equals(job.getClient().getId())) {
            throw new SecurityException("You can only update your own legal cases");
        }

        job.setTitle(jobDTO.getTitle());
        job.setDescription(jobDTO.getDescription());
        job.setBudget(jobDTO.getBudget());
        job.setJobType(jobDTO.getJobType());
        job.setUrgency(jobDTO.getUrgency() != null ? jobDTO.getUrgency() : job.getUrgency());
        job.setConfidentiality(jobDTO.getConfidentiality() != null ? jobDTO.getConfidentiality() : job.getConfidentiality());
        job.setEstimatedValue(jobDTO.getEstimatedValue());
        job.setDeadline(jobDTO.getDeadline());

        if (jobDTO.getSpecialtyId() != null) {
            Specialty specialty = specialtyRepository.findById(jobDTO.getSpecialtyId())
                    .orElseThrow(() -> new IllegalArgumentException("Specialty not found: " + jobDTO.getSpecialtyId()));
            job.setSpecialty(specialty);
        }

        if (jobDTO.getSkillIds() != null) {
            Set<Skill> skills = new HashSet<>();
            for (Integer skillId : jobDTO.getSkillIds()) {
                skills.add(skillRepository.findById(skillId)
                        .orElseThrow(() -> new IllegalArgumentException("Skill not found: " + skillId)));
            }
            job.setSkills(skills);
        }

        return jobRepository.save(job);
    }

    @Override
    @Transactional
    public Job archiveJob(int jobId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));

        // Ownership check: only the job owner can archive
        Integer currentUserId = authService.getCurrentUserId();
        if (currentUserId != null && !currentUserId.equals(job.getClient().getId())) {
            throw new SecurityException("You can only archive your own legal cases");
        }

        job.setArchived(true);
        job.setArchivedAt(LocalDateTime.now());
        job.setStatus(JobStatus.Archived);
        return jobRepository.save(job);
    }

    @Override
    @Transactional
    public Job closeJob(int jobId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));

        // Ownership check: only the job owner can close
        Integer currentUserId = authService.getCurrentUserId();
        if (currentUserId != null && !currentUserId.equals(job.getClient().getId())) {
            throw new SecurityException("You can only close your own legal cases");
        }

        job.setStatus(JobStatus.Completed);
        job.setClosedAt(LocalDateTime.now());
        return jobRepository.save(job);
    }

    @Override
    public List<Job> getJobsByClient(int clientId) {
        return jobRepository.findByClientId(clientId);
    }

    @Override
    public List<Job> getActiveJobs() {
        return jobRepository.findByArchivedFalse();
    }
}
