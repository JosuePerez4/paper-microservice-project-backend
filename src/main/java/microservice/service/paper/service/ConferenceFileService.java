package microservice.service.paper.service;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import microservice.service.paper.dto.ConferenceFileDownload;
import microservice.service.paper.model.ConferenceSupportFile;
import microservice.service.paper.repository.ConferenceSupportFileRepository;

@Service
public class ConferenceFileService {

    private final ConferenceSupportFileRepository repository;
    private final FileStorageService fileStorageService;
    private final FileOptimizer fileOptimizer;

    public ConferenceFileService(
            ConferenceSupportFileRepository repository,
            FileStorageService fileStorageService,
            FileOptimizer fileOptimizer) {
        this.repository = repository;
        this.fileStorageService = fileStorageService;
        this.fileOptimizer = fileOptimizer;
    }

    public ConferenceSupportFile uploadFile(UUID conferenceId, MultipartFile file) throws IOException {
        FileOptimizer.OptimizedPayload optimized = fileOptimizer.optimize(file);
        String savedObjectName = fileStorageService.uploadOptimized(
                optimized.data(),
                optimized.contentType(),
                file.getOriginalFilename());

        ConferenceSupportFile supportFile = new ConferenceSupportFile();
        supportFile.setConferenceId(conferenceId);
        supportFile.setOriginalFileName(file.getOriginalFilename());
        supportFile.setMinioObjectName(savedObjectName);
        supportFile.setContentType(optimized.contentType());
        supportFile.setFileSize((long) optimized.size());

        return repository.save(supportFile);
    }

    public List<ConferenceSupportFile> getFilesByConference(UUID conferenceId) {
        return repository.findByConferenceId(conferenceId);
    }

    public ConferenceFileDownload getFileForDownload(UUID conferenceId, UUID fileId) {
        ConferenceSupportFile supportFile = repository.findById(fileId)
                .filter(f -> conferenceId.equals(f.getConferenceId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Archivo no encontrado"));

        String key = supportFile.getMinioObjectName();
        if (key == null || key.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Archivo no encontrado");
        }

        byte[] content = fileStorageService.downloadFile(key);
        return new ConferenceFileDownload(
                content,
                supportFile.getContentType(),
                supportFile.getOriginalFileName());
    }

    public void deleteFile(UUID fileId) {
        ConferenceSupportFile supportFile = repository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("File not found"));

        String objectKey = supportFile.getMinioObjectName();
        if (objectKey != null && !objectKey.isBlank()) {
            fileStorageService.deleteFile(objectKey);
        }

        repository.delete(supportFile);
    }
}
