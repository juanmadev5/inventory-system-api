package com.jm.inventory.api.security;

import com.jm.inventory.infrastructure.platform.CorsProperties;
import jakarta.servlet.Filter;
import java.util.List;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * One filter chain for the whole application - this backend is its own identity provider
 * (see {@link JwtAccessTokenIssuer}/{@link RoleJwtAuthenticationConverter}), so there is no
 * multi-issuer resolution to do, unlike a federated setup. {@code POST /api/v1/auth/login}
 * is the only public business endpoint; every other one requires a valid access token plus,
 * per endpoint, a minimum {@link com.jm.inventory.domain.user.Role} via the
 * {@link RequireStaff}/{@link RequireManager}/{@link RequireAdmin} composed annotations.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            "/api/v1/auth/login", "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/actuator/health"
    };

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder,
            CorsConfigurationSource corsConfigurationSource, LoginRateLimitFilter loginRateLimitFilter,
            ApiRateLimitFilter apiRateLimitFilter, AuthenticatedUserMdcFilter authenticatedUserMdcFilter)
            throws Exception {
        http.cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                        .decoder(jwtDecoder)
                        .jwtAuthenticationConverter(new RoleJwtAuthenticationConverter())))
                .addFilterBefore(loginRateLimitFilter, BearerTokenAuthenticationFilter.class)
                .addFilterAfter(authenticatedUserMdcFilter, BearerTokenAuthenticationFilter.class)
                .addFilterAfter(apiRateLimitFilter, AuthenticatedUserMdcFilter.class);
        return http.build();
    }

    /**
     * Verifies this application's own HMAC-signed tokens - no OIDC discovery, no external
     * issuer, since this backend signs the very tokens it validates. Also checks the
     * {@code iss} claim matches {@code app.security.jwt.issuer}, on top of Nimbus's default
     * timestamp (exp/iat) validation.
     */
    @Bean
    JwtDecoder jwtDecoder(JwtSigningKey jwtSigningKey, JwtProperties jwtProperties) {
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey.get())
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        jwtDecoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(jwtProperties.issuer()));
        return jwtDecoder;
    }

    /**
     * The mechanism is always wired in; whether it actually permits anything depends
     * entirely on {@code app.cors.allowed-origins} (empty by default - see
     * {@link CorsProperties}). {@code allowCredentials(true)} is safe here specifically
     * because origins are always an explicit list, never the {@code "*"} wildcard Spring
     * Security forbids combining with credentials.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties corsProperties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setExposedHeaders(List.of("Location", "X-Correlation-Id"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * Every custom {@code @Component implements Filter} in this package is auto-registered
     * by Spring Boot as a global container filter, in addition to wherever Spring Security
     * itself places it via {@code addFilterBefore}/{@code addFilterAfter} above - without
     * disabling that automatic registration, each one would run twice per request.
     */
    @Bean
    FilterRegistrationBean<LoginRateLimitFilter> disableAutomaticLoginRateLimitFilterRegistration(
            LoginRateLimitFilter loginRateLimitFilter) {
        return disabled(loginRateLimitFilter);
    }

    @Bean
    FilterRegistrationBean<ApiRateLimitFilter> disableAutomaticApiRateLimitFilterRegistration(
            ApiRateLimitFilter apiRateLimitFilter) {
        return disabled(apiRateLimitFilter);
    }

    @Bean
    FilterRegistrationBean<AuthenticatedUserMdcFilter> disableAutomaticAuthenticatedUserMdcFilterRegistration(
            AuthenticatedUserMdcFilter authenticatedUserMdcFilter) {
        return disabled(authenticatedUserMdcFilter);
    }

    private <T extends Filter> FilterRegistrationBean<T> disabled(T filter) {
        FilterRegistrationBean<T> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
