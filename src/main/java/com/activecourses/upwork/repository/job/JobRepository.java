package com.activecourses.upwork.repository.job;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.activecourses.upwork.model.Job;

import java.util.List;

@Repository
public interface JobRepository extends JpaRepository<Job, Integer> {
    List<Job> findByClientId(int clientId);
    List<Job> findByArchivedFalse();
    List<Job> findBySpecialtyId(int specialtyId);
    List<Job> findByArchivedFalseAndStatus(com.activecourses.upwork.model.JobStatus status);
}