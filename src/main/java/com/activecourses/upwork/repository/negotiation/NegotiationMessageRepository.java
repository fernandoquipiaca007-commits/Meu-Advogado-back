package com.activecourses.upwork.repository.negotiation;

import com.activecourses.upwork.model.NegotiationMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NegotiationMessageRepository extends JpaRepository<NegotiationMessage, Long> {
    List<NegotiationMessage> findByThreadIdOrderBySentAtAsc(Long threadId);
    Page<NegotiationMessage> findByThreadIdOrderBySentAtAsc(Long threadId, Pageable pageable);
    List<NegotiationMessage> findByThreadProposalProposalIdOrderBySentAtAsc(int proposalId);
    Page<NegotiationMessage> findByThreadProposalProposalIdOrderBySentAtAsc(int proposalId, Pageable pageable);
}
