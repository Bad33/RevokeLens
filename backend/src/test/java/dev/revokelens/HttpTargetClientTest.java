
package dev.revokelens;

import java.net.URI;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;

import tools.jackson.databind.json.JsonMapper;

import static dev.revokelens.AuthorizationOracle.Verdict.*;
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
class HttpTargetClientTest {

    @LocalServerPort
    int port;

    @Autowired
    JsonMapper mapper;

    @Autowired
    AuthorizationOracle oracle;

    @Test
    void revocationWorksOverRealHttp() throws Exception {
        var client = new HttpTargetClient(
                URI.create("http://127.0.0.1:" + port), mapper);

        // Read before revocation.
        var before = client.read("alice", "demo", "atlas-plan");

        assertEquals(200, before.status());
        assertEquals(AUTHORIZED_DISCLOSURE,
                oracle.inspect(
                        "alice", "atlas-plan", before.body()
                ).verdict());

        // Revoke through the real HTTP endpoint.
        var revoke = client.revoke(
                "admin-atlas", "demo", "atlas-plan", "alice");

        assertEquals(204, revoke.status());

        // Confirm the permission change took effect.
        var after = client.read("alice", "demo", "atlas-plan");

        assertEquals(404, after.status());
        assertEquals(NO_DISCLOSURE,
                oracle.inspect(
                        "alice", "atlas-plan", after.body()
                ).verdict());
    }

    @Test
    void crossOrganizationAccessIsDeniedOverHttp() throws Exception {
        var client = new HttpTargetClient(
                URI.create("http://127.0.0.1:" + port), mapper);

        var response = client.read("bob", "demo", "atlas-plan");

        assertEquals(404, response.status());
        assertEquals(NO_DISCLOSURE,
                oracle.inspect(
                        "bob", "atlas-plan", response.body()
                ).verdict());
    }
}
