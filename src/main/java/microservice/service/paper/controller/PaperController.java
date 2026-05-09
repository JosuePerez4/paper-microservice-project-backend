package microservice.service.paper.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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
            @RequestPart(value = "files", required = false) List<MultipartFile> files) throws IOException {
        return ResponseEntity.ok(service.create(conferenceId, body, files));
    }

    @GetMapping("/conference/{conferenceId}/list")
    public List<PaperResponseDto> list(
            @PathVariable UUID conferenceId,
            @RequestParam(required = false) PaperStatus status) {
        return service.listByConference(conferenceId, status);
    }

    @GetMapping("/conference/{conferenceId}/evaluations-tray")
    public List<PaperResponseDto> evaluationTray(@PathVariable UUID conferenceId) {
        return service.listEvaluationTray(conferenceId);
    }

    @GetMapping("/conference/{conferenceId}/{paperId}")
    public PaperResponseDto getOne(
            @PathVariable UUID conferenceId,
            @PathVariable UUID paperId) {
        return service.getById(conferenceId, paperId);
    }

    @PatchMapping("/conference/{conferenceId}/{paperId}/evaluations")
    public PaperResponseDto evaluate(
            @PathVariable UUID conferenceId,
            @PathVariable UUID paperId,
            @Valid @RequestBody PaperEvaluationDto body) {
        return service.evaluate(conferenceId, paperId, body);
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
}
