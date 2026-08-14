package com.activecourses.upwork.repository.delivery;

import com.activecourses.upwork.model.Delivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeliveryRepository extends JpaRepository<Delivery, Long> {
    List<Delivery> findByContractContractIdAndMilestoneMilestoneId(Integer contractId, Integer milestoneId);
    List<Delivery> findByContractContractIdAndStatus(Integer contractId, String status);
    List<Delivery> findByContractContractIdOrderByVersionDesc(Integer contractId);
}
