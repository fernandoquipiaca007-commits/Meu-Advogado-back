package com.activecourses.upwork.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendNegotiationMessageRequest {

    @NotBlank(message = "A mensagem não pode estar vazia")
    @Size(max = 4000, message = "A mensagem não pode exceder 4000 caracteres")
    private String content;

    private String message;

    public String getContent() {
        if (content != null && !content.trim().isEmpty()) {
            return content;
        }
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
        if (this.content == null) {
            this.content = message;
        }
    }
}
