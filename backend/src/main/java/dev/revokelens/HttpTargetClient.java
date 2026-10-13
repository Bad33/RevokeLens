
package dev.revokelens;

import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import tools.jackson.databind.json.JsonMapper;

class HttpTargetClient {

    private final URI baseUrl;
    private final JsonMapper mapper;

    private final HttpClient http = HttpClient.newBuilder()
            .cookieHandler(new CookieManager())
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    HttpTargetClient(URI baseUrl, JsonMapper mapper) {
        this.baseUrl = baseUrl;
        this.mapper = mapper;
    }

    Response read(String username, String password, String documentId)
            throws Exception {

        return send(request(
                "/api/documents/" + documentId,
                username,
                password
        ).GET());
    }

    Response revoke(
            String admin,
            String password,
            String documentId,
            String username
    ) throws Exception {

        // Fetch the CSRF token and retain the session cookie.
        var csrfResponse = send(request("/api/csrf", admin, password).GET());

        if (csrfResponse.status() != 200) {
            throw new IllegalStateException(
                    "Unable to obtain CSRF token: " + csrfResponse.status());
        }

        var csrf = mapper.readTree(csrfResponse.body());

        return send(request(
                "/api/documents/" + documentId + "/grants/" + username,
                admin,
                password
        ).header(
                csrf.get("headerName").asText(),
                csrf.get("token").asText()
        ).DELETE());
    }

    private HttpRequest.Builder request(
            String path,
            String username,
            String password
    ) {
        String credentials = Base64.getEncoder().encodeToString(
                (username + ":" + password)
                        .getBytes(StandardCharsets.UTF_8)
        );

        return HttpRequest.newBuilder(baseUrl.resolve(path))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Basic " + credentials);
    }

    private Response send(HttpRequest.Builder request) throws Exception {
        var response = http.send(
                request.build(),
                HttpResponse.BodyHandlers.ofString()
        );

        return new Response(response.statusCode(), response.body());
    }

    record Response(int status, String body) {}
}
