package com.activecourses.upwork.service.firm;

import com.activecourses.upwork.model.LawFirm;

import java.util.List;
import java.util.Optional;

public interface LawFirmService {
    LawFirm createFirm(LawFirm lawFirm);
    Optional<LawFirm> getFirmById(int id);
    List<LawFirm> getAllFirms();
    LawFirm updateFirm(int id, LawFirm lawFirm);
    boolean deleteFirm(int id);
    LawFirm addLawyerToFirm(int firmId, int lawyerId, boolean isPartner);
    boolean removeLawyerFromFirm(int firmId, int lawyerId);
    List<LawFirm> getFirmsByLawyer(int lawyerId);
}
