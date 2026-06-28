package com.alumindex.controller;

import com.alumindex.entity.Tenant;
import com.alumindex.pipeline.LlmNormalisationService;
import com.alumindex.pipeline.PipelineService;
import com.alumindex.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end (HTTP → controller → parser → header mapper → validation) test of the
 * upload file-type gate, against in-memory H2 with the LLM mocked (zero API cost,
 * no Supabase access). Proves a non-alumni file is rejected with a clear 400 message
 * and a valid alumni file is accepted (202) without consulting the classifier.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ImportFileValidationTest {

    @Autowired MockMvc mvc;
    @Autowired TenantRepository tenantRepo;

    @MockBean LlmNormalisationService llm;   // control classification, no real OpenAI calls
    @MockBean PipelineService pipeline;      // no-op so no rows are actually processed

    private UUID newTenantId() {
        return tenantRepo.save(Tenant.builder()
                .institutionName("Test University")
                .adminName("Admin")
                .adminEmail("admin-" + UUID.randomUUID() + "@test.edu")
                .build()).getId();
    }

    @Test
    @WithMockUser(roles = "SUPERADMIN")
    void accounting_file_is_rejected_with_clear_message() throws Exception {
        when(llm.classifyAlumniFile(anyList(), anyList()))
                .thenReturn(new LlmNormalisationService.FileClassification(
                        false, "accounting", "Columns are invoice amounts, not people."));

        var file = new MockMultipartFile("file", "accounting.csv", "text/csv",
                "name,amount,invoice_no\nAcme Corp,1200,INV-1\n".getBytes(StandardCharsets.UTF_8));

        mvc.perform(multipart("/api/superadmin/import").file(file)
                        .param("tenantId", newTenantId().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("alumni")))
                .andExpect(jsonPath("$.message").value(containsString("accounting")));

        verify(pipeline, never()).runAsync(any(), any(), anyList(), any());
    }

    @Test
    @WithMockUser(roles = "SUPERADMIN")
    void valid_alumni_file_is_accepted_without_classifier() throws Exception {
        // name + company + graduation_year → 2 alumni signals → classification skipped
        var file = new MockMultipartFile("file", "alumni.csv", "text/csv",
                "name,company,graduation_year\nBob Tan,Acme,2019\n".getBytes(StandardCharsets.UTF_8));

        mvc.perform(multipart("/api/superadmin/import").file(file)
                        .param("tenantId", newTenantId().toString()))
                .andExpect(status().isAccepted());

        verify(llm, never()).classifyAlumniFile(anyList(), anyList());
        verify(pipeline, times(1)).runAsync(any(), any(), anyList(), any());
    }
}
