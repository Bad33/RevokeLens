
package dev.revokelens;

import java.util.HashMap;
import java.util.Map;

// Deliberately vulnerable fixture. Never use in production.
class VulnerableHttpCache {

    private final HttpTargetClient client;
    private final Map<String, String> cache = new HashMap<>();

    VulnerableHttpCache(HttpTargetClient client) {
        this.client = client;
    }

    String read(String username, String documentId) throws Exception {

        // BUG: Cache hits skip authorization.
        if (cache.containsKey(documentId)) {
            return cache.get(documentId);
        }

        // A cache miss performs a real HTTP request.
        var response = client.read(username, "demo", documentId);

        if (response.status() != 200) {
            return null;
        }

        cache.put(documentId, response.body());
        return response.body();
    }
}
