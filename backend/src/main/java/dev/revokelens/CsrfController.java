
package dev.revokelens;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class CsrfController {

    @GetMapping("/api/csrf")
    CsrfToken token(CsrfToken csrfToken) {
        return csrfToken;
    }
}
