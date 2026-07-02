package com.activecourses.upwork.service.proposal;

import com.activecourses.upwork.dto.ProposalDTO;

import java.util.List;
import java.util.Optional;

public interface ProposalService {
    ProposalDTO createProposal(ProposalDTO proposalDTO);
    List<ProposalDTO> getProposalsByJob(int jobId);
    List<ProposalDTO> getMyProposals(int lawyerId);
    List<ProposalDTO> getProposalsForMyCases(int clientId);
    Optional<ProposalDTO> getProposalById(int proposalId);
    ProposalDTO acceptProposal(int proposalId);
    ProposalDTO rejectProposal(int proposalId);
    ProposalDTO withdrawProposal(int proposalId);
    ProposalDTO updateProposal(int proposalId, ProposalDTO proposalDTO);
}
