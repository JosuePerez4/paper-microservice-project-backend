package microservice.service.paper.controller;

import jakarta.validation.Valid;
import microservice.service.paper.dto.ConferenceFileDownload;
import microservice.service.paper.dto.PaperCreateDto;
import microservice.service.paper.dto.PaperEvaluationDto;
import microservice.service.paper.dto.PaperResponseDto;
import microservice.service.paper.enums.PaperStatus;
import microservice.service.paper.service.PaperService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/conferences/{conferenceId}/papers")
public class PaperController {

    private final PaperService service;

    public PaperController(PaperService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<PaperResponseDto> create(
            @PathVariable UUID conferenceId,
            @Valid @RequestBody PaperCreateDto body) {
        return ResponseEntity.ok(service.create(conferenceId, body));
    }

    @GetMapping
    public List<PaperResponseDto> list(
            @PathVariable UUID conferenceId,
            @RequestParam(required = false) PaperStatus status) {
        return service.listByConference(conferenceId, status);
    }

    @GetMapping("/evaluation-tray")
    public List<PaperResponseDto> evaluationTray(@PathVariable UUID conferenceId) {
        return service.listEvaluationTray(conferenceId);
    }

    @GetMapping("/{paperId}")
    public PaperResponseDto getOne(
            @PathVariable UUID conferenceId,
            @PathVariable UUID paperId) {
        return service.getById(conferenceId, paperId);
    }

    @PatchMapping("/{paperId}/evaluation")
    public PaperResponseDto evaluate(
            @PathVariable UUID conferenceId,
            @PathVariable UUID paperId,
            @Valid @RequestBody PaperEvaluationDto body) {
        return service.evaluate(conferenceId, paperId, body);
    }

    @PostMapping(value = "/{paperId}/document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PaperResponseDto> uploadDocument(
            @PathVariable UUID conferenceId,
            @PathVariable UUID paperId,
            @RequestParam("file") MultipartFile file) throws IOException {
        return ResponseEntity.ok(service.uploadDocument(conferenceId, paperId, file));
    }

    @GetMapping("/{paperId}/document")
    public ResponseEntity<byte[]> downloadDocument(
            @PathVariable UUID conferenceId,
            @PathVariable UUID paperId) {
        ConferenceFileDownload download = service.downloadDocument(conferenceId, paperId);
        String mediaType = download.contentType() != null && !download.contentType().isBlank()
                ? download.contentType()
                : MediaType.APPLICATION_OCTET_STREAM_VALUE;
        String filename = download.originalFileName() != null && !download.originalFileName().isBlank()
                ? download.originalFileName()
                : "articulo";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(mediaType));
        headers.setContentDisposition(
                ContentDisposition.attachment()
                        .filename(filename, StandardCharsets.UTF_8)
                        .build());

        return ResponseEntity.ok().headers(headers).body(download.content());
    }
}
