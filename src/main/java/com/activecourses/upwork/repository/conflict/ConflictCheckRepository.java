package com.activecourses.upwork.repository.conflict;

import com.activecourses.upwork.model.ConflictCheck;
import com.activecourses.upwork.model.ConflictStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConflictCheckRepository extends JpaRepository<ConflictCheck, Long> {
    Optional<ConflictCheck> findByJobJobIdAndLawyerId(Integer jobId, Integer lawyerId);
    List<ConflictCheck> findByJobJobId(Integer jobId);
    List<ConflictCheck> findByLawyerId(Integer lawyerId);
    boolean existsByJobJobIdAndLawyerIdAndStatus(Integer jobId, Integer lawyerId, ConflictStatus status);
}
