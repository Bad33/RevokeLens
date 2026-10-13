
package dev.revokelens;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static dev.revokelens.AuthorizationOracle.Verdict.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Sql("/fixtures.sql")
class AuthorizationOracleTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    AuthorizationOracle oracle;

    @Test
    void detectsStaleCacheDisclosureAfterRevocation() throws Exception {

        // 1. Alice reads the document while authorized.
        String originalResponse = mvc.perform(
                get("/api/documents/atlas-plan")
                        .with(httpBasic("alice", "demo")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        var before = oracle.inspect(
                "alice", "atlas-plan", originalResponse);

        assertEquals(AUTHORIZED_DISCLOSURE, before.verdict());

        // 2. Simulate a faulty cache that stores the response
        // without binding it to the current authorization state.
        Map<String, String> staleCache =
                Map.of("atlas-plan", originalResponse);

        // 3. Administrator revokes Alice's permission.
        mvc.perform(delete("/api/documents/atlas-plan/grants/alice")
                        .with(httpBasic("admin-atlas", "demo"))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        // 4. The secure API now refuses access.
        String secureResponse = mvc.perform(
                get("/api/documents/atlas-plan")
                        .with(httpBasic("alice", "demo")))
                .andExpect(status().isNotFound())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertEquals(NO_DISCLOSURE,
                oracle.inspect(
                        "alice", "atlas-plan", secureResponse
                ).verdict());

        // 5. The deliberately broken cache emits old content again.
        String leakedResponse = staleCache.get("atlas-plan");

        var finding = oracle.inspect(
                "alice", "atlas-plan", leakedResponse);

        // 6. The oracle must identify the violation.
        assertEquals(UNAUTHORIZED_DISCLOSURE, finding.verdict());
        assertTrue(finding.sameOrganization());
        assertFalse(finding.activeGrant());
        assertEquals("SYNTH-ATLAS-PLAN-001", finding.matchedEvidence());

        System.out.println("RevokeLens finding: " + finding);
    }

    @Test
    void detectsCrossOrganizationDisclosure() {

        // Simulate a faulty application emitting Atlas content to Bob.
        var finding = oracle.inspect(
                "bob",
                "atlas-plan",
                "SYNTH-ATLAS-PLAN-001"
        );

        assertEquals(UNAUTHORIZED_DISCLOSURE, finding.verdict());
        assertFalse(finding.sameOrganization());
        assertFalse(finding.activeGrant());
    }

    @Test
    void doesNotFlagHarmlessResponses() {

        var finding = oracle.inspect(
                "alice",
                "atlas-plan",
                "Document access unavailable."
        );

        assertEquals(NO_DISCLOSURE, finding.verdict());
        assertNull(finding.matchedEvidence());
    }
}
