package microservice.service.paper.config;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Value("${FRONTEND_URL}")
    private String frontendUrl;

    @Value("${JWT_PUBLIC_KEY}")
    private String jwtPublicKey;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/papers/conference/*/create").hasAnyRole("ADMIN", "AUTHOR")
                .requestMatchers(HttpMethod.PATCH, "/papers/conference/*/*/evaluations")
                    .hasAnyRole("ADMIN", "CHAIR", "ASISTANT", "ASSISTANT")
                .requestMatchers("/files/upload/*", "/files/delete/*").hasAnyRole("ADMIN", "CHAIR")
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withPublicKey(parsePublicKey(jwtPublicKey)).build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(this::extractRoleAuthorities);
        return converter;
    }

    private List<GrantedAuthority> extractRoleAuthorities(Jwt jwt) {
        log.info("[PAPER-SEC] JWT claims presentes: {}", jwt.getClaims().keySet());
        log.info("[PAPER-SEC] JWT claims completos: {}", jwt.getClaims());

        Object rolesClaim = jwt.getClaim("roles");
        if (rolesClaim instanceof List<?> rolesList) {
            List<GrantedAuthority> authorities = rolesList.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .map(String::trim)
                    .filter(role -> !role.isBlank())
                    .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());
            log.info("[PAPER-SEC] Authorities desde 'roles': {}", authorities);
            return authorities;
        }

        String roleClaim = jwt.getClaimAsString("role");
        log.info("[PAPER-SEC] Claim 'role' (string): '{}'", roleClaim);

        if (roleClaim != null && !roleClaim.isBlank()) {
            String normalized = roleClaim.startsWith("ROLE_") ? roleClaim : "ROLE_" + roleClaim;
            log.info("[PAPER-SEC] Authority final: {}", normalized);
            return List.of(new SimpleGrantedAuthority(normalized));
        }

        log.warn("[PAPER-SEC] NO se encontró ningún claim de rol en el JWT");
        return List.of();
    }

    private RSAPublicKey parsePublicKey(String keyValue) {
        try {
            String normalized = keyValue
                    .replace("\\n", "\n")
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");

            byte[] decoded = Base64.getDecoder().decode(normalized);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(decoded);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return (RSAPublicKey) keyFactory.generatePublic(keySpec);
        } catch (IllegalArgumentException | GeneralSecurityException ex) {
            throw new IllegalArgumentException(
                    "No fue posible parsear JWT_PUBLIC_KEY. Verifica formato PEM/base64.", ex);
        }
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = Arrays.stream(frontendUrl.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        config.setAllowedOriginPatterns(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
