
package dev.revokelens;

import java.util.List;

import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class ScenarioRunner {

    enum Action {
        API_READ,
        CACHE_READ,
        REVOKE_GRANT
    }

    record Step(
            Action action,
            String actor,
            String subject,
            String documentId
    ) {}

    private final MockMvc mvc;
    private final AuthorizationOracle oracle;
    private final VulnerableDocumentCache cache;

    ScenarioRunner(MockMvc mvc, AuthorizationOracle oracle) {
        this.mvc = mvc;
        this.oracle = oracle;
        this.cache = new VulnerableDocumentCache(mvc);
    }

    ScenarioTrace run(List<Step> steps) throws Exception {
        var trace = new ScenarioTrace();

        for (Step step : steps) {
            switch (step.action()) {

                case CACHE_READ -> {
                    String response = cache.read(
                            step.actor(), step.documentId());

                    trace.record(
                            "CACHE_READ",
                            step.actor(),
                            step.subject(),
                            step.documentId(),
                            null,
                            oracle.inspect(
                                    step.subject(),
                                    step.documentId(),
                                    response)
                    );
                }

                case API_READ -> {
                    var response = mvc.perform(
                            get("/api/documents/{id}", step.documentId())
                                    .with(httpBasic(step.actor(), "demo")))
                            .andReturn()
                            .getResponse();

                    trace.record(
                            "API_READ",
                            step.actor(),
                            step.subject(),
                            step.documentId(),
                            response.getStatus(),
                            oracle.inspect(
                                    step.subject(),
                                    step.documentId(),
                                    response.getContentAsString())
                    );
                }

                case REVOKE_GRANT -> {
                    var response = mvc.perform(
                            delete(
                                    "/api/documents/{id}/grants/{username}",
                                    step.documentId(),
                                    step.subject())
                                    .with(httpBasic(step.actor(), "demo"))
                                    .with(csrf()))
                            .andReturn()
                            .getResponse();

                    trace.record(
                            "REVOKE_GRANT",
                            step.actor(),
                            step.subject(),
                            step.documentId(),
                            response.getStatus(),
                            oracle.inspect(
                                    step.subject(),
                                    step.documentId(),
                                    null)
                    );
                }
            }
        }

        return trace;
    }
}
