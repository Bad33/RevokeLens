
package dev.revokelens;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
class AuthorizationOracle {

    private final JdbcClient jdbc;

    AuthorizationOracle(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Finding inspect(String username, String documentId, String emittedText) {

        // Read the document independently of the application's retrieval logic.
        var document = jdbc.sql("""
                SELECT organization_id, content
                FROM documents
                WHERE id = :id
                """)
                .param("id", documentId)
                .query((rs, row) -> new Document(
                        rs.getString("organization_id"),
                        rs.getString("content")
                ))
                .optional()
                .orElseThrow(() ->
                        new IllegalArgumentException("Unknown document: " + documentId));

        // Did the new response actually contain this synthetic document?
        boolean disclosed = emittedText != null
                && !document.content().isEmpty()
                && emittedText.contains(document.content());

        // Independently check organization membership.
        boolean sameOrganization = jdbc.sql("""
                SELECT 1
                FROM app_users
                WHERE username = :username
                  AND organization_id = :organization
                """)
                .param("username", username)
                .param("organization", document.organizationId())
                .query(Integer.class)
                .optional()
                .isPresent();

        // Independently check the current document grant.
        boolean activeGrant = jdbc.sql("""
                SELECT 1
                FROM document_grants
                WHERE username = :username
                  AND document_id = :documentId
                """)
                .param("username", username)
                .param("documentId", documentId)
                .query(Integer.class)
                .optional()
                .isPresent();

        Verdict verdict;

        if (!disclosed) {
            verdict = Verdict.NO_DISCLOSURE;
        } else if (sameOrganization && activeGrant) {
            verdict = Verdict.AUTHORIZED_DISCLOSURE;
        } else {
            verdict = Verdict.UNAUTHORIZED_DISCLOSURE;
        }

        return new Finding(
                verdict,
                username,
                documentId,
                sameOrganization,
                activeGrant,
                disclosed ? document.content() : null
        );
    }

    enum Verdict {
        NO_DISCLOSURE,
        AUTHORIZED_DISCLOSURE,
        UNAUTHORIZED_DISCLOSURE
    }

    record Finding(
            Verdict verdict,
            String username,
            String documentId,
            boolean sameOrganization,
            boolean activeGrant,
            String matchedEvidence
    ) {}

    private record Document(String organizationId, String content) {}
}
