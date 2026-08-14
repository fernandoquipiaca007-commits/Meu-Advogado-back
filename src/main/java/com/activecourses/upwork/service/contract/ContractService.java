package com.activecourses.upwork.service.contract;

import com.activecourses.upwork.dto.ContractDTO;
import com.activecourses.upwork.dto.ContractMilestoneDTO;

import java.util.List;
import java.util.Optional;

public interface ContractService {
    ContractDTO createContract(int proposalId);
    ContractDTO acceptAndContract(com.activecourses.upwork.dto.AcceptContractRequestDto request, jakarta.servlet.http.HttpServletRequest httpRequest);
    List<ContractDTO> getContractsByClient(int clientId);
    List<ContractDTO> getContractsByLawyer(int lawyerId);
    List<ContractDTO> getMyContracts();
    Optional<ContractDTO> getContractById(int contractId);
    com.activecourses.upwork.dto.ContractTimelineDto getContractTimeline(int contractId);
    ContractDTO completeContract(int contractId);
    ContractDTO terminateContract(int contractId);
    ContractDTO cancelContract(int contractId);
    ContractMilestoneDTO completeMilestone(int milestoneId);
    List<ContractMilestoneDTO> getMilestones(int contractId);
}
