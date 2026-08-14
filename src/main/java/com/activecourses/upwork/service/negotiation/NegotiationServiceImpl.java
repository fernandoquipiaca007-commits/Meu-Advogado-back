package com.activecourses.upwork.service.negotiation;

import com.activecourses.upwork.dto.NegotiationMessageDTO;
import com.activecourses.upwork.model.*;
import com.activecourses.upwork.repository.job.ProposalRepository;
import com.activecourses.upwork.repository.negotiation.NegotiationMessageRepository;
import com.activecourses.upwork.repository.negotiation.NegotiationThreadRepository;
import com.activecourses.upwork.repository.user.UserRepository;
import com.activecourses.upwork.service.moderation.ContentModerationService;
import com.activecourses.upwork.service.notification.NotificationService;
import com.activecourses.upwork.service.security.AuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NegotiationServiceImpl implements NegotiationService {

    private final NegotiationThreadRepository negotiationThreadRepository;
    private final NegotiationMessageRepository negotiationMessageRepository;
    private final ProposalRepository proposalRepository;
    private final UserRepository userRepository;
    private final ContentModerationService contentModerationService;
    private final AuthorizationService authorizationService;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public NegotiationMessageDTO sendMessage(int proposalId, int senderId, String content) {
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("O conteúdo da mensagem não pode estar vazio.");
        }

        // 1. Enforce participant authorization (Lawyer or Client of proposal, or Admin)
        authorizationService.enforceNegotiationParticipant(proposalId, senderId);

        Proposal proposal = proposalRepository.findById(proposalId)
                .orElseThrow(() -> new IllegalArgumentException("Proposta não encontrada com ID: " + proposalId));

        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado com ID: " + senderId));

        // 2. Obtain or create negotiation thread
        NegotiationThread thread = getOrCreateThread(proposalId);

        // 3. Mask sensitive content (emails, phones, URLs, CNJ, CPF/CNPJ)
        String maskedContent = contentModerationService.maskSensitiveContent(content);
        boolean isModerated = !Objects.equals(maskedContent, content);

        NegotiationMessage message = NegotiationMessage.builder()
                .thread(thread)
                .sender(sender)
                .contentMasked(maskedContent)
                .originalContent(content)
                .sentAt(LocalDateTime.now())
                .isModerated(isModerated)
                .flaggedReason(isModerated ? "Contém dados de contato ou PII mascarados" : null)
                .build();

        message = negotiationMessageRepository.save(message);

        // 4. Notify counterparty
        Integer recipientId = null;
        if (proposal.getLawyer() != null && senderId == proposal.getLawyer().getId()) {
            if (proposal.getJob() != null && proposal.getJob().getClient() != null) {
                recipientId = proposal.getJob().getClient().getId();
            }
        } else if (proposal.getLawyer() != null) {
            recipientId = proposal.getLawyer().getId();
        }

        if (recipientId != null) {
            notificationService.createNotification(
                    recipientId,
                    NotificationType.NEW_MESSAGE,
                    "Nova mensagem de negociação",
                    sender.getFirstName() + " enviou uma mensagem sobre a proposta para: " + (proposal.getJob() != null ? proposal.getJob().getTitle() : "demanda"),
                    "proposal",
                    proposalId
            );
        }

        return mapToDTO(message, proposalId);
    }

    @Override
    public Page<NegotiationMessageDTO> getMessages(int proposalId, int userId, Pageable pageable) {
        // Enforce participant authorization
        authorizationService.enforceNegotiationParticipant(proposalId, userId);

        NegotiationThread thread = getOrCreateThread(proposalId);
        Page<NegotiationMessage> messages = negotiationMessageRepository.findByThreadIdOrderBySentAtAsc(thread.getId(), pageable);
        return messages.map(msg -> mapToDTO(msg, proposalId));
    }

    @Override
    public List<NegotiationMessageDTO> getMessages(int proposalId, int userId) {
        // Enforce participant authorization
        authorizationService.enforceNegotiationParticipant(proposalId, userId);

        NegotiationThread thread = getOrCreateThread(proposalId);
        List<NegotiationMessage> messages = negotiationMessageRepository.findByThreadIdOrderBySentAtAsc(thread.getId());
        return messages.stream().map(msg -> mapToDTO(msg, proposalId)).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public NegotiationThread getOrCreateThread(int proposalId) {
        return negotiationThreadRepository.findByProposalProposalId(proposalId)
                .orElseGet(() -> {
                    Proposal proposal = proposalRepository.findById(proposalId)
                            .orElseThrow(() -> new IllegalArgumentException("Proposta não encontrada com ID: " + proposalId));
                    NegotiationThread newThread = NegotiationThread.builder()
                            .proposal(proposal)
                            .createdAt(LocalDateTime.now())
                            .retentionDays(90)
                            .build();
                    NegotiationThread savedThread = negotiationThreadRepository.save(newThread);
                    proposal.setNegotiationThread(savedThread);
                    proposalRepository.save(proposal);
                    return savedThread;
                });
    }

    private NegotiationMessageDTO mapToDTO(NegotiationMessage message, int proposalId) {
        String senderName = null;
        String senderRole = null;
        if (message.getSender() != null) {
            senderName = message.getSender().getFirstName() + " " + message.getSender().getLastName();
            if (message.getSender().getRoles() != null && !message.getSender().getRoles().isEmpty()) {
                senderRole = message.getSender().getRoles().get(0).getName();
            }
        }

        return NegotiationMessageDTO.builder()
                .id(message.getId())
                .threadId(message.getThread() != null ? message.getThread().getId() : null)
                .proposalId(proposalId)
                .senderId(message.getSender() != null ? message.getSender().getId() : null)
                .senderName(senderName)
                .senderRole(senderRole)
                .contentMasked(message.getContentMasked())
                .originalContent(null) // Do not expose raw content externally
                .sentAt(message.getSentAt())
                .isModerated(message.isModerated())
                .flaggedReason(message.getFlaggedReason())
                .build();
    }
}
