package microservice.service.paper.client;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import microservice.service.paper.client.dto.AuthPaperAuthorResponse;
import microservice.service.paper.client.dto.AuthValidatePaperAuthorsRequest;
import microservice.service.paper.client.dto.AuthValidatePaperAuthorsResponse;
import microservice.service.paper.dto.PaperAuthorDto;

@Component
public class AuthClient {

    private final RestClient restClient;

    public AuthClient(
            RestClient.Builder restClientBuilder,
            @Value("${auth.service.base-url}") String authBaseUrl) {
        this.restClient = restClientBuilder.baseUrl(authBaseUrl).build();
    }

    public List<PaperAuthorDto> validatePaperAuthors(List<UUID> userIds, String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token requerido para validar autores");
        }

        try {
            AuthValidatePaperAuthorsResponse response = restClient.post()
                    .uri("/api/v1/users/paper-authors/validate")
                    .header("Authorization", authorizationHeader)
                    .body(new AuthValidatePaperAuthorsRequest(userIds))
                    .retrieve()
                    .body(AuthValidatePaperAuthorsResponse.class);

            if (response == null || response.getAuthors() == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY, "Respuesta inválida de auth-service al validar autores");
            }

            return response.getAuthors().stream().map(this::toDto).toList();
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode() == HttpStatus.BAD_REQUEST) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, extractMessage(ex), ex);
            }
            if (ex.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token inválido o expirado", ex);
            }
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "No se pudo validar autores con auth-service: " + ex.getMessage(),
                    ex);
        }
    }

    private PaperAuthorDto toDto(AuthPaperAuthorResponse author) {
        return PaperAuthorDto.builder()
                .id(author.getId())
                .firstName(author.getFirstName())
                .lastName(author.getLastName())
                .email(author.getEmail())
                .displayName(author.getDisplayName())
                .role(author.getRole())
                .build();
    }

    private static String extractMessage(RestClientResponseException ex) {
        String body = ex.getResponseBodyAsString();
        return body != null && !body.isBlank() ? body : ex.getMessage();
    }
}
