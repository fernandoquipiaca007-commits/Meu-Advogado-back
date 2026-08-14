package com.activecourses.upwork.repository.security;

import com.activecourses.upwork.model.SecurityAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SecurityAlertRepository extends JpaRepository<SecurityAlert, Long> {
    List<SecurityAlert> findByResolvedFalseOrderByCreatedAtDesc();
    List<SecurityAlert> findBySeverityAndResolvedFalse(String severity);
    long countBySeverityAndResolvedFalse(String severity);
}
