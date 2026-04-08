package microservice.service.paper.dto;

import microservice.service.paper.enums.PaperStatus;

public class PaperEvaluationDto {
    private PaperStatus status;

    public PaperStatus getStatus() {
        return status;
    }

    public void setStatus(PaperStatus status) {
        this.status = status;
    }
}
