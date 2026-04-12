package microservice.service.paper.controller;

import microservice.service.paper.dto.ConferenceFileDto;
import microservice.service.paper.model.ConferenceSupportFile;
import microservice.service.paper.service.ConferenceFileService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/conferences")
public class ConferenceFileController {

    private final ConferenceFileService fileService;

    public ConferenceFileController(ConferenceFileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping("/{conferenceId}/files")
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

    @GetMapping("/{conferenceId}/files")
    public ResponseEntity<List<ConferenceFileDto>> listFiles(@PathVariable UUID conferenceId) {
        List<ConferenceFileDto> files = fileService.getFilesByConference(conferenceId)
                .stream()
                .map(ConferenceFileDto::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(files);
    }

    @DeleteMapping("/files/{fileId}")
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
