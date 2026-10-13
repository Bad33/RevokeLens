
package dev.revokelens;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/documents")
class DocumentController {

    private final JdbcClient jdbc;

    DocumentController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/{id}")
    ResponseEntity<String> read(
            @PathVariable String id,
            Authentication user
    ) {
        var content = jdbc.sql("""
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
                .param("id", id)
                .param("username", user.getName())
                .query(String.class)
                .optional();

        return content
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
