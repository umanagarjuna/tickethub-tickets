package com.tickethub.eventservice.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true) // To enable @PreAuthorize for method-level security
public class SecurityConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    @Bean
    public JwtDecoder jwtDecoder() throws MalformedURLException {
        // Get JWK Set URI from the issuer's .well-known endpoint
        String jwkSetUri = issuerUri + "/protocol/openid-connect/certs";

        // Create a custom Nimbus JWT processor that can handle duplicate claims
        JWKSource<SecurityContext> jwkSource = new RemoteJWKSet<>(new URL(jwkSetUri));
        ConfigurableJWTProcessor<SecurityContext> jwtProcessor = new DefaultJWTProcessor<>();
        jwtProcessor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, jwkSource));

        // Custom claim converter to handle duplicate fields
        NimbusJwtDecoder decoder = new NimbusJwtDecoder(jwtProcessor);
        decoder.setClaimSetConverter(source -> {
            // Create a new map with the contents of the original claim set
            Map<String, Object> claims = new HashMap<>();
            // Only copy the last occurrence of any duplicate key
            source.forEach(claims::put);
            return claims;
        });

        return decoder;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Disable CSRF as this is a stateless API (typical for microservices)
                .csrf(csrf -> csrf.disable())
                // Configure session management to be stateless
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Define authorization rules for HTTP requests
                .authorizeHttpRequests(authorizeRequests ->
                        authorizeRequests
                                .requestMatchers(HttpMethod.GET, "/events", "/events/**").permitAll() // Public read access for events
                                .requestMatchers("/actuator/**").permitAll() // Allow access to health, info, prometheus endpoints
                                .requestMatchers("/admin/**").hasAuthority("SCOPE_admin") // Example: requires 'admin' scope from JWT
                                // .requestMatchers("/admin/**").hasRole("ADMIN") // Alternative: if using roles like "ROLE_ADMIN"
                                .anyRequest().authenticated() // All other requests require authentication
                )
                // Configure OAuth2 resource server with JWT validation
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {
                    try {
                        jwt.decoder(jwtDecoder());
                    } catch (MalformedURLException e) {
                        throw new RuntimeException(e);
                    }
                })); // Connect our custom decoder

        return http.build();
    }
}