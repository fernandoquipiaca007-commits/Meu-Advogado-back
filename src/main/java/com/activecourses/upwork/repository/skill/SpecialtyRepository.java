package com.activecourses.upwork.repository.skill;

import com.activecourses.upwork.model.Specialty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SpecialtyRepository extends JpaRepository<Specialty, Integer> {
    Optional<Specialty> findByName(String name);
}
