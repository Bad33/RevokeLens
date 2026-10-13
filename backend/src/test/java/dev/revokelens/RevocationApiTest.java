
package dev.revokelens;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Sql("/fixtures.sql")
class RevocationApiTest {

    private static final String GRANT_URL =
            "/api/documents/atlas-plan/grants/alice";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void ensureGrantExists() {
        jdbc.sql("""
                INSERT INTO document_grants (document_id, username)
                VALUES ('atlas-plan', 'alice')
                ON CONFLICT DO NOTHING
                """).update();
    }

    @Test
    void adminCanRevokeAccess() throws Exception {
        mvc.perform(get("/api/documents/atlas-plan")
                        .with(httpBasic("alice", "demo")))
                .andExpect(status().isOk());

        mvc.perform(delete(GRANT_URL)
                        .with(httpBasic("admin-atlas", "demo"))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/documents/atlas-plan")
                        .with(httpBasic("alice", "demo")))
                .andExpect(status().isNotFound());
    }

    @Test
    void readerCannotRevokeAccess() throws Exception {
        mvc.perform(delete(GRANT_URL)
                        .with(httpBasic("alice", "demo"))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void otherOrganizationAdminCannotRevoke() throws Exception {
        mvc.perform(delete(GRANT_URL)
                        .with(httpBasic("admin-beacon", "demo"))
                        .with(csrf()))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/documents/atlas-plan")
                        .with(httpBasic("alice", "demo")))
                .andExpect(status().isOk());
    }

    @Test
    void revocationDoesNotAffectOtherDocuments() throws Exception {
        mvc.perform(delete(GRANT_URL)
                        .with(httpBasic("admin-atlas", "demo"))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/documents/atlas-notes")
                        .with(httpBasic("alice", "demo")))
                .andExpect(status().isOk());
    }

    @Test
    void repeatedRevocationReturnsNotFound() throws Exception {
        mvc.perform(delete(GRANT_URL)
                        .with(httpBasic("admin-atlas", "demo"))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        mvc.perform(delete(GRANT_URL)
                        .with(httpBasic("admin-atlas", "demo"))
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteWithoutCsrfTokenIsRejected() throws Exception {
        mvc.perform(delete(GRANT_URL)
                        .with(httpBasic("admin-atlas", "demo")))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousUserCannotRevoke() throws Exception {
        mvc.perform(delete(GRANT_URL).with(csrf()))
                .andExpect(status().isUnauthorized());
    }
}
