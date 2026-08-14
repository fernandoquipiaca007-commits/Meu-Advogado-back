package com.activecourses.upwork.repository.firm;

import com.activecourses.upwork.model.LawFirmMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LawFirmMemberRepository extends JpaRepository<LawFirmMember, Integer> {
    List<LawFirmMember> findByFirmId(Integer firmId);
    List<LawFirmMember> findByUserId(Integer userId);
    Optional<LawFirmMember> findByFirmIdAndUserId(Integer firmId, Integer userId);
    boolean existsByFirmIdAndUserId(Integer firmId, Integer userId);
}
