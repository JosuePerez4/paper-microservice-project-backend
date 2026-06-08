package microservice.service.paper.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;
import microservice.service.paper.dto.ConferenceFileDownload;
import microservice.service.paper.dto.PaperCreateDto;
import microservice.service.paper.dto.PaperEvaluationDto;
import microservice.service.paper.dto.PaperResponseDto;
import microservice.service.paper.enums.PaperStatus;
import microservice.service.paper.service.PaperService;

@RestController
@RequestMapping("/papers")
public class PaperController {

    private final PaperService service;

    public PaperController(PaperService service) {
        this.service = service;
    }

    /** Endpoint temporal de debug: muestra claims JWT y authorities. Quitar antes de producción. */
    @GetMapping("/debug/auth")
    public Map<String, Object> debugAuth(Authentication authentication) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("authenticated", authentication != null && authentication.isAuthenticated());
        info.put("type", authentication != null ? authentication.getClass().getSimpleName() : "null");
        if (authentication != null) {
            info.put("authorities", authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority).toList());
        }
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            info.put("jwt_claims", jwt.getClaims());
            info.put("jwt_claim_role", jwt.getClaim("role"));
            info.put("jwt_claim_roles", jwt.getClaim("roles"));
            info.put("jwt_claimAsString_role", jwt.getClaimAsString("role"));
        }
        return info;
    }
    @PostMapping(value = "/conference/{conferenceId}/create", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PaperResponseDto> create(
            @PathVariable UUID conferenceId,
            @RequestPart("paper") @Valid PaperCreateDto body,
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "Authorization", required = false) String authorization) throws IOException {
        return ResponseEntity.ok(service.create(
                conferenceId, body, files, resolveUserId(jwt), resolveRole(jwt), authorization));
    }

    private static UUID resolveUserId(Jwt jwt) {
        String userIdClaim = jwt.getClaimAsString("userId");
        if (userIdClaim == null || userIdClaim.isBlank()) {
            userIdClaim = jwt.getSubject();
        }
        return UUID.fromString(userIdClaim);
    }

    @GetMapping("/conference/{conferenceId}/list")
    public List<PaperResponseDto> list(
            @PathVariable UUID conferenceId,
            @RequestParam(required = false) PaperStatus status) {
        return service.listByConference(conferenceId, status);
    }

    @GetMapping("/conference/{conferenceId}/mine")
    public List<PaperResponseDto> listMine(
            @PathVariable UUID conferenceId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return service.listMyPapersByConference(conferenceId, resolveUserId(jwt), authorization);
    }

    @GetMapping("/conference/{conferenceId}/evaluations-tray")
    public List<PaperResponseDto> evaluationTray(@PathVariable UUID conferenceId) {
        return service.listEvaluationTray(conferenceId);
    }

    @GetMapping("/public/conference/{conferenceId}/approved")
    public List<PaperResponseDto> listApprovedForVisitors(@PathVariable UUID conferenceId) {
        return service.listApprovedForVisitors(conferenceId);
    }

    @GetMapping("/conference/{conferenceId}/{paperId}")
    public PaperResponseDto getOne(
            @PathVariable UUID conferenceId,
            @PathVariable UUID paperId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return service.getById(
                conferenceId,
                paperId,
                resolveUserId(jwt),
                resolveRole(jwt),
                authorization);
    }

    @PatchMapping("/conference/{conferenceId}/{paperId}/evaluations")
    public PaperResponseDto evaluate(
            @PathVariable UUID conferenceId,
            @PathVariable UUID paperId,
            @Valid @RequestBody PaperEvaluationDto body,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return service.evaluate(conferenceId, paperId, body, authorization);
    }

    @PostMapping(value = "/conference/{conferenceId}/{paperId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PaperResponseDto> uploadDocuments(
            @PathVariable UUID conferenceId,
            @PathVariable UUID paperId,
            @RequestParam("files") List<MultipartFile> files) throws IOException {
        return ResponseEntity.ok(service.uploadDocuments(conferenceId, paperId, files));
    }

    @GetMapping("/conference/{conferenceId}/{paperId}/attachments/{attachmentId}")
    public ResponseEntity<byte[]> downloadDocument(
            @PathVariable UUID conferenceId,
            @PathVariable UUID paperId,
            @PathVariable UUID attachmentId) {
        ConferenceFileDownload download = service.downloadDocument(conferenceId, paperId, attachmentId);
        String mediaType = download.contentType() != null && !download.contentType().isBlank()
                ? download.contentType()
                : MediaType.APPLICATION_OCTET_STREAM_VALUE;
        String filename = download.originalFileName() != null && !download.originalFileName().isBlank()
                ? download.originalFileName()
                : "adjunto";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(mediaType));
        headers.setContentDisposition(
                ContentDisposition.attachment()
                        .filename(filename, StandardCharsets.UTF_8)
                        .build());

        return ResponseEntity.ok().headers(headers).body(download.content());
    }

    private static UUID resolveUserId(Jwt jwt) {
        if (jwt == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token requerido");
        }
        String userIdClaim = jwt.getClaimAsString("userId");
        if (userIdClaim == null || userIdClaim.isBlank()) {
            userIdClaim = jwt.getSubject();
        }
        return UUID.fromString(userIdClaim);
    }

    private static String resolveRole(Jwt jwt) {
        if (jwt == null) {
            return null;
        }
        String role = jwt.getClaimAsString("role");
        if (role != null && !role.isBlank()) {
            return role.trim();
        }
        Object rolesClaim = jwt.getClaim("roles");
        if (rolesClaim instanceof List<?> rolesList && !rolesList.isEmpty()) {
            Object first = rolesList.get(0);
            if (first instanceof String roleName && !roleName.isBlank()) {
                return roleName.startsWith("ROLE_") ? roleName.substring(5) : roleName;
            }
        }
        return null;
    }
}
