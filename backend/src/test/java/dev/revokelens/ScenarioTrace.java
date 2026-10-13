
package dev.revokelens;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import tools.jackson.databind.json.JsonMapper;

class ScenarioTrace {

    private final List<Event> events = new ArrayList<>();

    void record(
            String operation,
            String actor,
            String subject,
            String documentId,
            Integer httpStatus,
            AuthorizationOracle.Finding finding
    ) {
        events.add(new Event(
                events.size() + 1,
                operation,
                actor,
                subject,
                documentId,
                httpStatus,
                finding.verdict().name(),
                finding.sameOrganization(),
                finding.activeGrant(),
                finding.matchedEvidence()
        ));
    }

    List<Event> events() {
        return List.copyOf(events);
    }

    Path save(Path path, JsonMapper mapper) throws Exception {
        Files.createDirectories(path.getParent());

        List<String> lines = new ArrayList<>();

        for (Event event : events) {
            lines.add(mapper.writeValueAsString(event));
        }

        Files.write(path, lines, StandardCharsets.UTF_8);
        return path;
    }

    record Event(
            int sequence,
            String operation,
            String actor,
            String subject,
            String documentId,
            Integer httpStatus,
            String verdict,
            boolean sameOrganization,
            boolean activeGrant,
            String matchedEvidence
    ) {}
}
