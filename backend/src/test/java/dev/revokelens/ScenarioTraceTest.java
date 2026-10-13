
package dev.revokelens;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;

import tools.jackson.databind.json.JsonMapper;

import static dev.revokelens.ScenarioRunner.Action.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql(
        scripts = "/fixtures.sql",
        config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED)
)
@Sql(
        scripts = "/fixtures.sql",
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD,
        config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED)
)
class ScenarioTraceTest {

    @LocalServerPort
    int port;

    @Autowired
    AuthorizationOracle oracle;

    @Autowired
    JsonMapper mapper;

    private ScenarioRunner runner() {
        var client = new HttpTargetClient(
                URI.create("http://127.0.0.1:" + port),
                mapper
        );

        return new ScenarioRunner(client, oracle);
    }

    @Test
    void detectsRevocationLeakOverHttp() throws Exception {
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

        var trace = runner().run(steps);

        assertEquals(4, trace.events().size());
        assertEquals("AUTHORIZED_DISCLOSURE",
                trace.events().get(0).verdict());

        assertEquals(204, trace.events().get(1).httpStatus());
        assertEquals("NOT_APPLICABLE",
                trace.events().get(1).verdict());

        assertEquals(404, trace.events().get(2).httpStatus());
        assertEquals("NO_DISCLOSURE",
                trace.events().get(2).verdict());

        assertEquals("UNAUTHORIZED_DISCLOSURE",
                trace.events().get(3).verdict());
        assertFalse(trace.events().get(3).activeGrant());

        Path path = trace.save(
                Path.of("target/traces/revocation-cache-leak.jsonl"),
                mapper
        );

        assertEquals(4, Files.readAllLines(path).size());
        System.out.println("Live HTTP trace: " + path.toAbsolutePath());
    }

    @Test
    void secureApiRemainsSafeAfterRevocation() throws Exception {
        var steps = List.of(
                new ScenarioRunner.Step(
                        API_READ, "alice", "alice", "atlas-plan"),
                new ScenarioRunner.Step(
                        REVOKE_GRANT, "admin-atlas", "alice", "atlas-plan"),
                new ScenarioRunner.Step(
                        API_READ, "alice", "alice", "atlas-plan")
        );

        var trace = runner().run(steps);

        assertEquals("AUTHORIZED_DISCLOSURE",
                trace.events().get(0).verdict());
        assertEquals(204, trace.events().get(1).httpStatus());
        assertEquals(404, trace.events().get(2).httpStatus());
        assertEquals("NO_DISCLOSURE",
                trace.events().get(2).verdict());
    }
}
