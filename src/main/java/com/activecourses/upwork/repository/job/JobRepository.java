package com.activecourses.upwork.repository.job;

import com.activecourses.upwork.model.Job;
import com.activecourses.upwork.model.JobStatus;
import com.activecourses.upwork.model.JobVisibility;
import com.activecourses.upwork.model.ModerationStatus;
import com.activecourses.upwork.model.UrgencyLevel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface JobRepository extends JpaRepository<Job, Integer> {
    List<Job> findByClientId(int clientId);
    List<Job> findByArchivedFalse();
    List<Job> findBySpecialtyId(int specialtyId);
    List<Job> findByArchivedFalseAndStatus(JobStatus status);

    Page<Job> findByVisibilityInAndModerationStatusAndArchivedFalseAndStatus(
            Collection<JobVisibility> visibilities,
            ModerationStatus moderationStatus,
            JobStatus status,
            Pageable pageable);

    List<Job> findByVisibilityInAndModerationStatusAndArchivedFalseAndStatus(
            Collection<JobVisibility> visibilities,
            ModerationStatus moderationStatus,
            JobStatus status);

    @Query("SELECT j FROM Job j WHERE j.visibility IN (:visibilities) " +
           "AND j.moderationStatus = :moderationStatus " +
           "AND j.archived = false " +
           "AND j.status = :status " +
           "AND (:specialtyId IS NULL OR (j.specialty IS NOT NULL AND j.specialty.id = :specialtyId)) " +
           "AND (:urgency IS NULL OR j.urgency = :urgency)")
    Page<Job> findDiscoveryJobs(
            @Param("visibilities") Collection<JobVisibility> visibilities,
            @Param("moderationStatus") ModerationStatus moderationStatus,
            @Param("status") JobStatus status,
            @Param("specialtyId") Integer specialtyId,
            @Param("urgency") UrgencyLevel urgency,
            Pageable pageable);
}