package microservice.service.paper.dto;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import microservice.service.paper.model.ConferenceSupportFile;

@Data
@NoArgsConstructor
@AllArgsConstructor
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
}
