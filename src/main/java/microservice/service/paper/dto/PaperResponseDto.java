package microservice.service.paper.dto;

import java.util.ArrayList;
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
    private UUID submittedByUserId;
    private List<UUID> authorIds;
    private List<PaperAuthorDto> authors;
    private String title;
    private String abstractText;
    private String topic;
    private String institutionalAffiliation;
    private String keywords;
    private PaperStatus status;
    private String evaluationObservations;
    private boolean hasDocument;
    private List<PaperAttachmentDto> documents;

    public static PaperResponseDto from(Paper p) {
        return from(p, null);
    }

    public static PaperResponseDto from(Paper p, List<PaperAuthorDto> enrichedAuthors) {
        PaperResponseDto dto = new PaperResponseDto();
        dto.setId(p.getId());
        dto.setConferenceId(p.getConferenceId());
        dto.setSubmittedByUserId(p.getSubmittedByUserId());
        dto.setAuthorIds(p.getAuthorIds() == null ? List.of() : new ArrayList<>(p.getAuthorIds()));
        dto.setAuthors(enrichedAuthors);
        dto.setTitle(p.getTitle());
        dto.setAbstractText(p.getAbstractText());
        dto.setTopic(p.getTopic());
        dto.setInstitutionalAffiliation(p.getInstitutionalAffiliation());
        dto.setKeywords(p.getKeywords());
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
