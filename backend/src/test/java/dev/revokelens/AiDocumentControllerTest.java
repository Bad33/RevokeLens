
package dev.revokelens;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.ai.chat.prompt.ChatOptions;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Sql("/fixtures.sql")
class AiDocumentControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @MockitoBean
    ChatModel model;

    @BeforeEach
    void setUp() {
        when(model.getOptions())
                .thenReturn(ChatOptions.builder().build());
    }

    private static final String REQUEST = """
            {
              "documentId": "atlas-plan",
              "question": "What is this document?"
            }
            """;

    private ChatResponse response(String text) {
        return new ChatResponse(
                List.of(new Generation(new AssistantMessage(text)))
        );
    }

    @Test
    void authorizedUserCanAskQuestion() throws Exception {
        when(model.call(any(Prompt.class)))
                .thenReturn(response("Synthetic Atlas plan."));

        mvc.perform(post("/api/ai/answer")
                        .with(httpBasic("alice", "demo"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer")
                        .value("Synthetic Atlas plan."));

        verify(model, times(1)).call(any(Prompt.class));
    }

    @Test
    void unauthorizedDocumentNeverReachesModel() throws Exception {
        mvc.perform(post("/api/ai/answer")
                        .with(httpBasic("bob", "demo"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST))
                .andExpect(status().isNotFound());

        verify(model, never()).call(any(Prompt.class));
    }

    @Test
    void revocationDuringInferenceBlocksResponse() throws Exception {
        when(model.call(any(Prompt.class))).thenAnswer(invocation -> {
            jdbc.sql("""
                    DELETE FROM document_grants
                    WHERE document_id = 'atlas-plan'
                      AND username = 'alice'
                    """).update();

            return response("SYNTH-ATLAS-PLAN-001");
        });

        mvc.perform(post("/api/ai/answer")
                        .with(httpBasic("alice", "demo"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST))
                .andExpect(status().isNotFound());

        verify(model, times(1)).call(any(Prompt.class));
    }
}
