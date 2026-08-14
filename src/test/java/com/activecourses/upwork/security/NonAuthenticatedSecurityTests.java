package com.activecourses.upwork.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NonAuthenticatedSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @org.junit.jupiter.api.BeforeEach
    @org.junit.jupiter.api.AfterEach
    void setUp() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }


    @Test
    @DisplayName("GET /api/cases/discovery without token should return 401 Unauthorized")
    void testGetDiscoveryCasesWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/cases/discovery")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/jobs/all without token should return 401 Unauthorized")
    void testGetAllJobsWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/jobs/all")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/jobs without token should return 401 Unauthorized")
    void testGetActiveJobsWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/jobs/{id} without token should return 401 Unauthorized")
    void testGetJobByIdWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/jobs/1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/proposals/ without token should return 401 Unauthorized")
    void testCreateProposalWithoutTokenReturns401() throws Exception {
        mockMvc.perform(post("/api/proposals/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobId\": 1, \"proposedRate\": 1000}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/negotiations/{proposalId}/messages without token should return 401 Unauthorized")
    void testSendNegotiationMessageWithoutTokenReturns401() throws Exception {
        mockMvc.perform(post("/api/negotiations/1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"Olá\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/negotiations/{proposalId}/messages without token should return 401 Unauthorized")
    void testGetNegotiationMessagesWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/negotiations/1/messages")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }
}
