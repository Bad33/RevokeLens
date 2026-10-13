
package dev.revokelens;

import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
class AuthorizedDocuments {

    private final JdbcClient jdbc;

    AuthorizedDocuments(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<String> content(String username, String documentId) {
        return jdbc.sql("""
                SELECT d.content
                FROM documents d
                JOIN app_users u
                    ON u.organization_id = d.organization_id
                JOIN document_grants g
                    ON g.document_id = d.id
                    AND g.username = u.username
                WHERE d.id = :id
                    AND u.username = :username
                """)
                .param("id", documentId)
                .param("username", username)
                .query(String.class)
                .optional();
    }
}
