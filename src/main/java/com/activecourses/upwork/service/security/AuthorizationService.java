package com.activecourses.upwork.service.security;

import com.activecourses.upwork.model.AdminAccessLog;
import jakarta.servlet.http.HttpServletRequest;

public interface AuthorizationService {

    void enforceVerifiedLawyer(Integer lawyerId);

    void enforceContractParticipant(Integer contractId, Integer userId);

    void enforceJobOwner(Integer jobId, Integer userId);

    void enforceProposalOwnerOrClient(Integer proposalId, Integer userId);

    void enforceCanViewJobDetail(Integer jobId, Integer userId);

    void enforceNegotiationParticipant(Integer proposalId, Integer userId);

    void enforceDocumentAccess(Long documentId, Integer userId);

    void enforceDocumentAccess(com.activecourses.upwork.model.SecureDocument document, Integer userId);

    AdminAccessLog logAdminAccess(Integer adminUserId,
                                  Integer targetUserId,
                                  String resourceType,
                                  String resourceId,
                                  String action,
                                  String justification,
                                  HttpServletRequest request);
}
