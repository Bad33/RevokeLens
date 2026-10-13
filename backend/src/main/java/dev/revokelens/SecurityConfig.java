
package dev.revokelens;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/api/documents/*/grants/*"
                        ).hasRole("ADMIN")
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .build();
    }

    @Bean
    UserDetailsService users() {
        return new InMemoryUserDetailsManager(
                User.withUsername("alice")
                        .password("{noop}demo")
                        .roles("READER")
                        .build(),

                User.withUsername("bob")
                        .password("{noop}demo")
                        .roles("READER")
                        .build(),

                User.withUsername("charlie")
                        .password("{noop}demo")
                        .roles("READER")
                        .build(),

                User.withUsername("admin-atlas")
                        .password("{noop}demo")
                        .roles("ADMIN")
                        .build(),

                User.withUsername("admin-beacon")
                        .password("{noop}demo")
                        .roles("ADMIN")
                        .build()
        );
    }
}
