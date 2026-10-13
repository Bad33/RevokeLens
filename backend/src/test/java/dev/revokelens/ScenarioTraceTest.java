
package dev.revokelens;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;

import static dev.revokelens.AuthorizationOracle.Verdict.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Sql("/fixtures.sql")
class ScenarioTraceTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    AuthorizationOracle oracle;

    @Autowired
    JsonMapper mapper;

    @Test
    void recordsRevocationLeakScenario() throws Exception {

        var cache = new VulnerableDocumentCache(mvc);
        var trace = new ScenarioTrace();

        // Step 1: Alice reads while authorized.
        String initial = cache.read("alice", "atlas-plan");
        assertNotNull(initial);

        trace.record(
                "CACHE_READ", "alice", "alice",
                "atlas-plan", null,
                oracle.inspect("alice", "atlas-plan", initial)
        );

        // Step 2: Administrator revokes Alice's grant.
        var revokeResponse = mvc.perform(
                delete("/api/documents/atlas-plan/grants/alice")
                        .with(httpBasic("admin-atlas", "demo"))
                        .with(csrf()))
                .andExpect(status().isNoContent())
                .andReturn()
                .getResponse();

        trace.record(
                "REVOKE_GRANT", "admin-atlas", "alice",
                "atlas-plan", revokeResponse.getStatus(),
                oracle.inspect("alice", "atlas-plan", null)
        );

        // Step 3: The secured API correctly denies access.
        var secureResponse = mvc.perform(
                get("/api/documents/atlas-plan")
                        .with(httpBasic("alice", "demo")))
                .andExpect(status().isNotFound())
                .andReturn()
                .getResponse();

        trace.record(
                "API_READ", "alice", "alice",
                "atlas-plan", secureResponse.getStatus(),
                oracle.inspect(
                        "alice",
                        "atlas-plan",
                        secureResponse.getContentAsString()
                )
        );

        // Step 4: The vulnerable cache exposes old content.
        String leaked = cache.read("alice", "atlas-plan");
        var finding = oracle.inspect("alice", "atlas-plan", leaked);

        trace.record(
                "CACHE_READ", "alice", "alice",
                "atlas-plan", null, finding
        );

        // Verify the event sequence and detected violation.
        assertEquals(4, trace.events().size());

        assertEquals(
                AUTHORIZED_DISCLOSURE.name(),
                trace.events().get(0).verdict()
        );

        assertEquals(
                UNAUTHORIZED_DISCLOSURE.name(),
                trace.events().get(3).verdict()
        );

        assertFalse(trace.events().get(3).activeGrant());
        assertTrue(trace.events().get(3).sameOrganization());

        // Save machine-readable evidence.
        Path path = trace.save(
                Path.of("target/traces/revocation-cache-leak.jsonl"),
                mapper
        );

        var lines = Files.readAllLines(path);

        assertEquals(4, lines.size());
        assertTrue(lines.get(3).contains("UNAUTHORIZED_DISCLOSURE"));

        System.out.println("Trace saved: " + path.toAbsolutePath());
    }
}
