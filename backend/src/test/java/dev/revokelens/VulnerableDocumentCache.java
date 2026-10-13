
package dev.revokelens;

import java.util.HashMap;
import java.util.Map;

import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

// Intentionally vulnerable. For security tests only.
class VulnerableDocumentCache {

    private final MockMvc mvc;
    private final Map<String, String> cache = new HashMap<>();

    VulnerableDocumentCache(MockMvc mvc) {
        this.mvc = mvc;
    }

    String read(String username, String documentId) throws Exception {

        // BUG: A cache hit skips authorization entirely.
        String cached = cache.get(documentId);

        if (cached != null) {
            return cached;
        }

        // Cache misses go through the real secured API.
        var response = mvc.perform(
                get("/api/documents/{id}", documentId)
                        .with(httpBasic(username, "demo")))
                .andReturn()
                .getResponse();

        if (response.getStatus() != 200) {
            return null;
        }

        String content = response.getContentAsString();
        cache.put(documentId, content);

        return content;
    }
}
