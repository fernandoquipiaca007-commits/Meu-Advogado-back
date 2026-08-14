package com.activecourses.upwork.service.job;

import com.activecourses.upwork.dto.JobDTO;
import com.activecourses.upwork.dto.JobDiscoveryDto;
import com.activecourses.upwork.model.Job;
import com.activecourses.upwork.model.UrgencyLevel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface JobService {
    Job createJob(JobDTO jobDTO);
    List<Job> getAllJobs();
    Optional<Job> getJobById(int jobId);
    JobDTO getJobDetail(int jobId, Integer requestingUserId);
    Page<JobDiscoveryDto> getDiscoveryCases(Integer specialtyId, UrgencyLevel urgency, Pageable pageable);
    Job updateJob(int jobId, JobDTO jobDTO);
    Job archiveJob(int jobId);
    Job closeJob(int jobId);
    List<Job> getJobsByClient(int clientId);
    List<Job> getActiveJobs();
}
