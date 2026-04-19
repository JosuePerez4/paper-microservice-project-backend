package microservice.service.paper.controller;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import microservice.service.paper.dto.ConferenceFileDownload;
import microservice.service.paper.dto.ConferenceFileDto;
import microservice.service.paper.model.ConferenceSupportFile;
import microservice.service.paper.service.ConferenceFileService;

@RestController
@RequestMapping("/files")
public class ConferenceFileController {

    private final ConferenceFileService fileService;

    public ConferenceFileController(ConferenceFileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping("/upload/{conferenceId}")
    public ResponseEntity<ConferenceFileDto> uploadFile(
            @PathVariable UUID conferenceId,
            @RequestParam("file") MultipartFile file) {
        try {
            ConferenceSupportFile savedFile = fileService.uploadFile(conferenceId, file);
            return ResponseEntity.ok(new ConferenceFileDto(savedFile));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/list/{conferenceId}")
    public ResponseEntity<List<ConferenceFileDto>> listFiles(@PathVariable UUID conferenceId) {
        List<ConferenceFileDto> files = fileService.getFilesByConference(conferenceId)
                .stream()
                .map(ConferenceFileDto::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(files);
    }

    @GetMapping("/{conferenceId}/download/{fileId}")
    public ResponseEntity<byte[]> downloadFile(
            @PathVariable UUID conferenceId,
            @PathVariable UUID fileId) {
        ConferenceFileDownload download = fileService.getFileForDownload(conferenceId, fileId);

        String mediaType = download.contentType() != null && !download.contentType().isBlank()
                ? download.contentType()
                : MediaType.APPLICATION_OCTET_STREAM_VALUE;

        String filename = download.originalFileName() != null && !download.originalFileName().isBlank()
                ? download.originalFileName()
                : "file";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(mediaType));
        headers.setContentDisposition(
                ContentDisposition.attachment()
                        .filename(filename, StandardCharsets.UTF_8)
                        .build());

        return ResponseEntity.ok()
                .headers(headers)
                .body(download.content());
    }

    @DeleteMapping("/delete/{fileId}")
    public ResponseEntity<Void> deleteFile(@PathVariable UUID fileId) {
        try {
            fileService.deleteFile(fileId);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }
}
