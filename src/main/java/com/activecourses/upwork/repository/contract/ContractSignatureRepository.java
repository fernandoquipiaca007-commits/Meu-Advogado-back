package com.activecourses.upwork.repository.contract;

import com.activecourses.upwork.model.ContractSignature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContractSignatureRepository extends JpaRepository<ContractSignature, Long> {
    List<ContractSignature> findByContractContractId(Integer contractId);
    List<ContractSignature> findByUserId(Integer userId);
    Optional<ContractSignature> findByHashReceipt(String hashReceipt);
}
