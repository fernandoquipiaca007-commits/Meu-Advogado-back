package com.activecourses.upwork.repository.delivery;

import com.activecourses.upwork.model.Dispute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DisputeRepository extends JpaRepository<Dispute, Long> {
    List<Dispute> findByContractContractId(Integer contractId);
    List<Dispute> findByStatus(String status);
    boolean existsByContractContractIdAndStatusIn(Integer contractId, List<String> statuses);
}
