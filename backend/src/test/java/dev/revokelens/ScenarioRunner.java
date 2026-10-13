
package dev.revokelens;

import java.util.List;

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

    private final HttpTargetClient client;
    private final AuthorizationOracle oracle;
    private final VulnerableHttpCache cache;

    ScenarioRunner(HttpTargetClient client, AuthorizationOracle oracle) {
        this.client = client;
        this.oracle = oracle;
        this.cache = new VulnerableHttpCache(client);
    }

    ScenarioTrace run(List<Step> steps) throws Exception {
        var trace = new ScenarioTrace();

        for (Step step : steps) {

            if (step.action() != Action.REVOKE_GRANT
                    && !step.actor().equals(step.subject())) {
                throw new IllegalArgumentException(
                        "Read actor and subject must match");
            }

            switch (step.action()) {

                case API_READ -> {
                    var response = client.read(
                            step.actor(), "demo", step.documentId());

                    trace.record(
                            "API_READ",
                            step.actor(),
                            step.subject(),
                            step.documentId(),
                            response.status(),
                            oracle.inspect(
                                    step.actor(),
                                    step.documentId(),
                                    response.body())
                    );
                }

                case CACHE_READ -> {
                    String content = cache.read(
                            step.actor(), step.documentId());

                    trace.record(
                            "CACHE_READ",
                            step.actor(),
                            step.subject(),
                            step.documentId(),
                            null,
                            oracle.inspect(
                                    step.actor(),
                                    step.documentId(),
                                    content)
                    );
                }

                case REVOKE_GRANT -> {
                    var response = client.revoke(
                            step.actor(),
                            "demo",
                            step.documentId(),
                            step.subject());

                    trace.record(
                            "REVOKE_GRANT",
                            step.actor(),
                            step.subject(),
                            step.documentId(),
                            response.status(),
                            null
                    );
                }
            }
        }

        return trace;
    }
}
