package com.activecourses.upwork.repository.admin;

import com.activecourses.upwork.model.AdminAccessLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AdminAccessLogRepository extends JpaRepository<AdminAccessLog, Long> {
    List<AdminAccessLog> findByAdminUserId(Integer adminUserId);
    List<AdminAccessLog> findByTargetUserId(Integer targetUserId);
}
