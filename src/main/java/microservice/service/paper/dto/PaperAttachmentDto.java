package microservice.service.paper.dto;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import microservice.service.paper.model.PaperAttachment;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaperAttachmentDto {

    private UUID id;
    private String originalFileName;
    private String contentType;
    private Long fileSize;

    public PaperAttachmentDto(PaperAttachment a) {
        this.id = a.getId();
        this.originalFileName = a.getOriginalFileName();
        this.contentType = a.getContentType();
        this.fileSize = a.getFileSize();
    }
}
