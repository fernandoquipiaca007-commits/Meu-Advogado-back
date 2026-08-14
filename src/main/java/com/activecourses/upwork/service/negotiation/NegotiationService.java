package com.activecourses.upwork.service.negotiation;

import com.activecourses.upwork.dto.NegotiationMessageDTO;
import com.activecourses.upwork.model.NegotiationThread;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface NegotiationService {

    NegotiationMessageDTO sendMessage(int proposalId, int senderId, String content);

    Page<NegotiationMessageDTO> getMessages(int proposalId, int userId, Pageable pageable);

    List<NegotiationMessageDTO> getMessages(int proposalId, int userId);

    NegotiationThread getOrCreateThread(int proposalId);
}
