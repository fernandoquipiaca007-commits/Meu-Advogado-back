package com.activecourses.upwork.service.conflict;

import com.activecourses.upwork.dto.ConflictCheckDto;
import com.activecourses.upwork.dto.ConflictCheckRequestDto;
import com.activecourses.upwork.model.ConflictStatus;

import java.util.Optional;

public interface ConflictService {
    ConflictCheckDto checkConflict(Integer jobId, Integer lawyerId);
    ConflictCheckDto declareConflict(ConflictCheckRequestDto request);
    Optional<ConflictCheckDto> getConflictStatus(Integer jobId, Integer lawyerId);
}
