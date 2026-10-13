
package dev.revokelens;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

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

    @DeleteMapping("/{id}/grants/{username}")
    ResponseEntity<Void> revoke(
            @PathVariable String id,
            @PathVariable String username,
            Authentication admin
    ) {
        int deleted = jdbc.sql("""
                DELETE FROM document_grants g
                USING documents d, app_users a
                WHERE g.document_id = d.id
                    AND d.id = :id
                    AND g.username = :username
                    AND a.username = :admin
                    AND a.organization_id = d.organization_id
                """)
                .param("id", id)
                .param("username", username)
                .param("admin", admin.getName())
                .update();

        return deleted > 0
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
