package microservice.service.paper.dto;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import microservice.service.paper.enums.PaperStatus;
import microservice.service.paper.model.Paper;

@Data
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PaperResponseDto {

    private UUID id;
    private UUID conferenceId;
    private String title;
    private String abstractText;
    private String topic;
    private String institutionalAffiliation;
    private String keywords;
    private String authors;
    private PaperStatus status;
    private String evaluationObservations;
    /** Indica si hay al menos un adjunto (compatibilidad con clientes que solo comprueban este flag). */
    private boolean hasDocument;
    private List<PaperAttachmentDto> documents;

    public static PaperResponseDto from(Paper p) {
        PaperResponseDto dto = new PaperResponseDto();
        dto.setId(p.getId());
        dto.setConferenceId(p.getConferenceId());
        dto.setTitle(p.getTitle());
        dto.setAbstractText(p.getAbstractText());
        dto.setTopic(p.getTopic());
        dto.setInstitutionalAffiliation(p.getInstitutionalAffiliation());
        dto.setKeywords(p.getKeywords());
        dto.setAuthors(p.getAuthors());
        dto.setStatus(p.getStatus());
        dto.setEvaluationObservations(p.getEvaluationObservations());
        List<PaperAttachmentDto> docs = p.getAttachments() == null
                ? List.of()
                : p.getAttachments().stream().map(PaperAttachmentDto::new).collect(Collectors.toList());
        dto.setDocuments(docs);
        dto.setHasDocument(!docs.isEmpty());
        return dto;
    }
}
