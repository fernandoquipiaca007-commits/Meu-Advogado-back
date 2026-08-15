package com.activecourses.upwork.repository.payment;

import com.activecourses.upwork.model.PayoutAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PayoutAccountRepository extends JpaRepository<PayoutAccount, Long> {
    Optional<PayoutAccount> findByUserId(Integer userId);
    boolean existsByUserId(Integer userId);
}
