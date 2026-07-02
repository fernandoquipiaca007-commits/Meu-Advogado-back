package com.activecourses.upwork.repository.contract;

import com.activecourses.upwork.model.ContractMilestone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContractMilestoneRepository extends JpaRepository<ContractMilestone, Integer> {
    List<ContractMilestone> findByContractContractId(int contractId);
}
