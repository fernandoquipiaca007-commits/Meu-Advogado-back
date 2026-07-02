package com.activecourses.upwork.repository.firm;

import com.activecourses.upwork.model.LawFirm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LawFirmRepository extends JpaRepository<LawFirm, Integer> {
    Optional<LawFirm> findByCnpj(String cnpj);
}
