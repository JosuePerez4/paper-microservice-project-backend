package microservice.service.paper.service;

import microservice.service.paper.model.ConferenceSupportFile;
import microservice.service.paper.repository.ConferenceSupportFileRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class ConferenceFileService {

    private final ConferenceSupportFileRepository repository;
    private final FileStorageService fileStorageService;

    public ConferenceFileService(ConferenceSupportFileRepository repository, FileStorageService fileStorageService) {
        this.repository = repository;
        this.fileStorageService = fileStorageService;
    }

    public ConferenceSupportFile uploadFile(Long conferenceId, MultipartFile file) {
        String savedObjectName = fileStorageService.uploadFile(file);

        ConferenceSupportFile supportFile = new ConferenceSupportFile();
        supportFile.setConferenceId(conferenceId);
        supportFile.setOriginalFileName(file.getOriginalFilename());
        supportFile.setMinioObjectName(savedObjectName); // We keep the property name MinioObjectName for DB or we can rename it later
        supportFile.setContentType(file.getContentType());
        supportFile.setFileSize(file.getSize());

        return repository.save(supportFile);
    }

    public List<ConferenceSupportFile> getFilesByConference(Long conferenceId) {
        return repository.findByConferenceId(conferenceId);
    }

    public void deleteFile(Long fileId) {
        ConferenceSupportFile supportFile = repository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("File not found"));

        fileStorageService.deleteFile(supportFile.getMinioObjectName());

        repository.delete(supportFile);
    }
}
