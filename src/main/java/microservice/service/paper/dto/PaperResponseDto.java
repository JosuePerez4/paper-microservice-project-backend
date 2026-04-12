package microservice.service.paper.dto;

import java.util.UUID;

import microservice.service.paper.enums.PaperStatus;
import microservice.service.paper.model.Paper;

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
    private boolean hasDocument;

    public PaperResponseDto() {
    }

    public PaperResponseDto(Paper p) {
        this.id = p.getId();
        this.conferenceId = p.getConferenceId();
        this.title = p.getTitle();
        this.abstractText = p.getAbstractText();
        this.topic = p.getTopic();
        this.institutionalAffiliation = p.getInstitutionalAffiliation();
        this.keywords = p.getKeywords();
        this.authors = p.getAuthors();
        this.status = p.getStatus();
        this.evaluationObservations = p.getEvaluationObservations();
        String key = p.getDocumentObjectName();
        this.hasDocument = key != null && !key.isBlank();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getConferenceId() {
        return conferenceId;
    }

    public void setConferenceId(UUID conferenceId) {
        this.conferenceId = conferenceId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAbstractText() {
        return abstractText;
    }

    public void setAbstractText(String abstractText) {
        this.abstractText = abstractText;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getInstitutionalAffiliation() {
        return institutionalAffiliation;
    }

    public void setInstitutionalAffiliation(String institutionalAffiliation) {
        this.institutionalAffiliation = institutionalAffiliation;
    }

    public String getKeywords() {
        return keywords;
    }

    public void setKeywords(String keywords) {
        this.keywords = keywords;
    }

    public String getAuthors() {
        return authors;
    }

    public void setAuthors(String authors) {
        this.authors = authors;
    }

    public PaperStatus getStatus() {
        return status;
    }

    public void setStatus(PaperStatus status) {
        this.status = status;
    }

    public String getEvaluationObservations() {
        return evaluationObservations;
    }

    public void setEvaluationObservations(String evaluationObservations) {
        this.evaluationObservations = evaluationObservations;
    }

    public boolean isHasDocument() {
        return hasDocument;
    }

    public void setHasDocument(boolean hasDocument) {
        this.hasDocument = hasDocument;
    }
}
