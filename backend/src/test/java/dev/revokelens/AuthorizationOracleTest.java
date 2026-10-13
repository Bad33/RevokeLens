
package dev.revokelens;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

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
    void detectsCacheLeakAfterRevocation() throws Exception {
        var cache = new VulnerableDocumentCache(mvc);

        // Alice retrieves the document while authorized.
        String original = cache.read("alice", "atlas-plan");

        assertNotNull(original);
        assertEquals(AUTHORIZED_DISCLOSURE,
                oracle.inspect("alice", "atlas-plan", original).verdict());

        // Administrator revokes Alice's grant.
        mvc.perform(delete("/api/documents/atlas-plan/grants/alice")
                        .with(httpBasic("admin-atlas", "demo"))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        // The secure API correctly denies subsequent access.
        mvc.perform(get("/api/documents/atlas-plan")
                        .with(httpBasic("alice", "demo")))
                .andExpect(status().isNotFound());

        // The faulty cache still returns the stored response.
        String leaked = cache.read("alice", "atlas-plan");

        var finding = oracle.inspect("alice", "atlas-plan", leaked);

        assertEquals(UNAUTHORIZED_DISCLOSURE, finding.verdict());
        assertFalse(finding.activeGrant());
        assertEquals("SYNTH-ATLAS-PLAN-001", finding.matchedEvidence());

        System.out.println("Revocation finding: " + finding);
    }

    @Test
    void detectsCrossOrganizationCacheLeak() throws Exception {
        var cache = new VulnerableDocumentCache(mvc);

        // Alice populates the cache.
        assertNotNull(cache.read("alice", "atlas-plan"));

        // Bob receives Atlas's cached content.
        String leaked = cache.read("bob", "atlas-plan");

        var finding = oracle.inspect("bob", "atlas-plan", leaked);

        assertEquals(UNAUTHORIZED_DISCLOSURE, finding.verdict());
        assertFalse(finding.sameOrganization());

        System.out.println("Cross-tenant finding: " + finding);
    }

    @Test
    void cacheMissStillRespectsAuthorization() throws Exception {
        var cache = new VulnerableDocumentCache(mvc);

        // Bob cannot read Atlas's document on a cold cache.
        String response = cache.read("bob", "atlas-plan");

        assertNull(response);
        assertEquals(NO_DISCLOSURE,
                oracle.inspect("bob", "atlas-plan", response).verdict());
    }

    @Test
    void harmlessResponseDoesNotTriggerViolation() {
        var finding = oracle.inspect(
                "alice",
                "atlas-plan",
                "Document access unavailable."
        );

        assertEquals(NO_DISCLOSURE, finding.verdict());
        assertNull(finding.matchedEvidence());
    }
}
