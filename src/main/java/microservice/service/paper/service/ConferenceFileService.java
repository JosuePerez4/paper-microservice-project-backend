package microservice.service.paper.service;

import microservice.service.paper.model.ConferenceSupportFile;
import microservice.service.paper.repository.ConferenceSupportFileRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

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
