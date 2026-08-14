package com.activecourses.upwork.repository.payment;

import com.activecourses.upwork.model.ProviderEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProviderEventRepository extends JpaRepository<ProviderEvent, Long> {
    Optional<ProviderEvent> findByProviderEventId(String providerEventId);
    boolean existsByProviderEventId(String providerEventId);
}
