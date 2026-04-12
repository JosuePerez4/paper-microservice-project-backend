package microservice.service.paper.dto;

import jakarta.validation.constraints.NotNull;
import microservice.service.paper.enums.PaperStatus;

public record PaperEvaluationDto(
        @NotNull PaperStatus status,
        String observations
) {}
