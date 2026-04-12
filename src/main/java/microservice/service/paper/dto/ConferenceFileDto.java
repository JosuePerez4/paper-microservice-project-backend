package microservice.service.paper.dto;

import microservice.service.paper.model.ConferenceSupportFile;

import java.util.UUID;

public class ConferenceFileDto {
    private UUID id;
    private String originalFileName;
    private String contentType;
    private Long fileSize;

    public ConferenceFileDto(ConferenceSupportFile model) {
        this.id = model.getId();
        this.originalFileName = model.getOriginalFileName();
        this.contentType = model.getContentType();
        this.fileSize = model.getFileSize();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getOriginalFileName() { return originalFileName; }
    public void setOriginalFileName(String originalFileName) { this.originalFileName = originalFileName; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
}
