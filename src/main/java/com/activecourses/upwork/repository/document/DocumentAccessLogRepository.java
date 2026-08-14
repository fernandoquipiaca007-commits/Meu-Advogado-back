package com.activecourses.upwork.repository.document;

import com.activecourses.upwork.model.DocumentAccessLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentAccessLogRepository extends JpaRepository<DocumentAccessLog, Long> {
    List<DocumentAccessLog> findByDocumentIdOrderByTimestampDesc(Long documentId);
    List<DocumentAccessLog> findByUserIdOrderByTimestampDesc(Integer userId);
}
