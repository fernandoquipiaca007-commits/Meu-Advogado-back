package com.activecourses.upwork.service.firm;

import com.activecourses.upwork.model.LawFirm;
import com.activecourses.upwork.model.User;
import com.activecourses.upwork.repository.firm.LawFirmRepository;
import com.activecourses.upwork.repository.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LawFirmServiceImpl implements LawFirmService {

    private final LawFirmRepository lawFirmRepository;
    private final UserRepository userRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public LawFirm createFirm(LawFirm lawFirm) {
        return lawFirmRepository.save(lawFirm);
    }

    @Override
    public Optional<LawFirm> getFirmById(int id) {
        return lawFirmRepository.findById(id);
    }

    @Override
    public List<LawFirm> getAllFirms() {
        return lawFirmRepository.findAll();
    }

    @Override
    public LawFirm updateFirm(int id, LawFirm lawFirm) {
        LawFirm existing = lawFirmRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Law Firm not found"));
        existing.setName(lawFirm.getName());
        existing.setDescription(lawFirm.getDescription());
        existing.setPhone(lawFirm.getPhone());
        existing.setWebsite(lawFirm.getWebsite());
        existing.setLogoUrl(lawFirm.getLogoUrl());
        existing.setAddress(lawFirm.getAddress());
        existing.setCity(lawFirm.getCity());
        existing.setState(lawFirm.getState());
        existing.setCountry(lawFirm.getCountry());
        return lawFirmRepository.save(existing);
    }

    @Override
    public boolean deleteFirm(int id) {
        if (lawFirmRepository.existsById(id)) {
            lawFirmRepository.deleteById(id);
            return true;
        }
        return false;
    }

    @Override
    @Transactional
    public LawFirm addLawyerToFirm(int firmId, int lawyerId, boolean isPartner) {
        LawFirm firm = lawFirmRepository.findById(firmId)
                .orElseThrow(() -> new RuntimeException("Law Firm not found"));
        User lawyer = userRepository.findById(lawyerId)
                .orElseThrow(() -> new RuntimeException("Lawyer not found"));

        // Insert into lawyer_firms junction table
        entityManager.createNativeQuery(
                "INSERT INTO lawyer_firms (lawyer_id, firm_id, is_partner) VALUES (:lawyerId, :firmId, :isPartner) " +
                "ON CONFLICT (lawyer_id, firm_id) DO UPDATE SET is_partner = :isPartner")
                .setParameter("lawyerId", lawyerId)
                .setParameter("firmId", firmId)
                .setParameter("isPartner", isPartner)
                .executeUpdate();

        return firm;
    }

    @Override
    @Transactional
    public boolean removeLawyerFromFirm(int firmId, int lawyerId) {
        int affected = entityManager.createNativeQuery(
                "DELETE FROM lawyer_firms WHERE lawyer_id = :lawyerId AND firm_id = :firmId")
                .setParameter("lawyerId", lawyerId)
                .setParameter("firmId", firmId)
                .executeUpdate();
        return affected > 0;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<LawFirm> getFirmsByLawyer(int lawyerId) {
        List<Integer> firmIds = entityManager.createNativeQuery(
                "SELECT firm_id FROM lawyer_firms WHERE lawyer_id = :lawyerId")
                .setParameter("lawyerId", lawyerId)
                .getResultList();
        return lawFirmRepository.findAllById(firmIds);
    }
}
