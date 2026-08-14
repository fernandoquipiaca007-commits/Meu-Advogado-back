package com.activecourses.upwork.repository.user;

import com.activecourses.upwork.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Integer> {
    Optional<User> findByEmail(String email);
    Optional<User> findByVerificationToken(String token);

    @Query("SELECT DISTINCT u FROM User u JOIN u.roles r LEFT JOIN FETCH u.userProfile WHERE r.name IN ('ROLE_LAWYER', 'ROLE_FREELANCER') AND u.accountEnabled = true")
    List<User> findAllLawyers();
}