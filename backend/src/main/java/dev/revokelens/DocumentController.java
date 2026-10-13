
package dev.revokelens;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/documents")
class DocumentController {

    private static final Map<String, String> USER_ORGS = Map.of(
            "alice", "atlas",
            "bob", "beacon"
    );

    private static final Map<String, Document> DOCUMENTS = Map.of(
            "atlas-plan", new Document("atlas", "SYNTH-ATLAS-PLAN-001"),
            "beacon-plan", new Document("beacon", "SYNTH-BEACON-PLAN-001")
    );

    @GetMapping("/{id}")
    ResponseEntity<String> read(
            @PathVariable String id,
            Authentication user
    ) {
        Document document = DOCUMENTS.get(id);

        if (document == null ||
                !document.organization().equals(USER_ORGS.get(user.getName()))) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(document.content());
    }

    private record Document(String organization, String content) {}
}
