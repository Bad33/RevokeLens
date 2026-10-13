
package dev.revokelens;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DocumentApiTest {

    @Autowired
    MockMvc mvc;

    @Test
    void anonymousUserIsRejected() throws Exception {
        mvc.perform(get("/api/documents/atlas-plan"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aliceCanReadAtlasDocument() throws Exception {
        mvc.perform(get("/api/documents/atlas-plan")
                        .with(httpBasic("alice", "demo")))
                .andExpect(status().isOk())
                .andExpect(content().string("SYNTH-ATLAS-PLAN-001"));
    }

    @Test
    void aliceCannotReadBeaconDocument() throws Exception {
        mvc.perform(get("/api/documents/beacon-plan")
                        .with(httpBasic("alice", "demo")))
                .andExpect(status().isNotFound());
    }

    @Test
    void bobCanReadBeaconDocument() throws Exception {
        mvc.perform(get("/api/documents/beacon-plan")
                        .with(httpBasic("bob", "demo")))
                .andExpect(status().isOk())
                .andExpect(content().string("SYNTH-BEACON-PLAN-001"));
    }
}
