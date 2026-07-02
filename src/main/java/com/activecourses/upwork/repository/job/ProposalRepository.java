package com.activecourses.upwork.repository.job;

import com.activecourses.upwork.model.Proposal;
import com.activecourses.upwork.model.ProposalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProposalRepository extends JpaRepository<Proposal, Integer> {
    List<Proposal> findByJobJobId(int jobId);
    List<Proposal> findByLawyerId(int lawyerId);
    List<Proposal> findByJobJobIdAndStatus(int jobId, ProposalStatus status);
    Optional<Proposal> findByJobJobIdAndLawyerId(int jobId, int lawyerId);
    long countByJobJobId(int jobId);
}
