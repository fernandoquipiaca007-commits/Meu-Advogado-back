package com.activecourses.upwork.repository.delivery;

import com.activecourses.upwork.model.CancellationRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CancellationRequestRepository extends JpaRepository<CancellationRequest, Long> {
    List<CancellationRequest> findByContractContractId(Integer contractId);
    List<CancellationRequest> findByStatus(String status);
}
