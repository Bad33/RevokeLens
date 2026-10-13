
package dev.revokelens;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;

import static dev.revokelens.ScenarioRunner.Action.*;
import static org.junit.jupiter.api.Assertions.*;

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
    void runnerDetectsRevocationCacheLeak() throws Exception {
        var runner = new ScenarioRunner(mvc, oracle);

        var steps = List.of(
                new ScenarioRunner.Step(
                        CACHE_READ, "alice", "alice", "atlas-plan"),

                new ScenarioRunner.Step(
                        REVOKE_GRANT, "admin-atlas", "alice", "atlas-plan"),

                new ScenarioRunner.Step(
                        API_READ, "alice", "alice", "atlas-plan"),

                new ScenarioRunner.Step(
                        CACHE_READ, "alice", "alice", "atlas-plan")
        );

        var trace = runner.run(steps);

        assertEquals(4, trace.events().size());

        assertEquals("AUTHORIZED_DISCLOSURE",
                trace.events().get(0).verdict());

        assertEquals(Integer.valueOf(204),
                trace.events().get(1).httpStatus());

        assertEquals(Integer.valueOf(404),
                trace.events().get(2).httpStatus());

        assertEquals("UNAUTHORIZED_DISCLOSURE",
                trace.events().get(3).verdict());

        Path path = trace.save(
                Path.of("target/traces/revocation-cache-leak.jsonl"),
                mapper
        );

        assertEquals(4, Files.readAllLines(path).size());

        System.out.println("Scenario executed: " + path);
    }

    @Test
    void secureApiDoesNotLeakAfterRevocation() throws Exception {
        var runner = new ScenarioRunner(mvc, oracle);

        var steps = List.of(
                new ScenarioRunner.Step(
                        API_READ, "alice", "alice", "atlas-plan"),

                new ScenarioRunner.Step(
                        REVOKE_GRANT, "admin-atlas", "alice", "atlas-plan"),

                new ScenarioRunner.Step(
                        API_READ, "alice", "alice", "atlas-plan")
        );

        var trace = runner.run(steps);

        assertEquals(3, trace.events().size());

        assertEquals("AUTHORIZED_DISCLOSURE",
                trace.events().get(0).verdict());

        assertEquals(Integer.valueOf(204),
                trace.events().get(1).httpStatus());

        assertEquals(Integer.valueOf(404),
                trace.events().get(2).httpStatus());

        assertEquals("NO_DISCLOSURE",
                trace.events().get(2).verdict());
    }
}
