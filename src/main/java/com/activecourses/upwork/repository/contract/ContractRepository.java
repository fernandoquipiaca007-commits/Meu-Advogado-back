package com.activecourses.upwork.repository.contract;

import com.activecourses.upwork.model.Contract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContractRepository extends JpaRepository<Contract, Integer> {
    List<Contract> findByClientId(int clientId);
    List<Contract> findByLawyerId(int lawyerId);
    List<Contract> findByJobJobId(int jobId);
    List<Contract> findByStatus(com.activecourses.upwork.model.ContractStatus status);
}
