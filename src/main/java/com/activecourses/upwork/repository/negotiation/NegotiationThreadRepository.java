package com.activecourses.upwork.repository.negotiation;

import com.activecourses.upwork.model.NegotiationThread;
import com.activecourses.upwork.model.Proposal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NegotiationThreadRepository extends JpaRepository<NegotiationThread, Long> {
    Optional<NegotiationThread> findByProposalProposalId(int proposalId);
    Optional<NegotiationThread> findByProposal(Proposal proposal);
    boolean existsByProposalProposalId(int proposalId);
}
