package microservice.service.paper.dto;

import jakarta.validation.constraints.NotBlank;

public record PaperCreateDto(
        @NotBlank String title,
        @NotBlank String abstractText,
        @NotBlank String topic,
        @NotBlank String institutionalAffiliation,
        @NotBlank String keywords,
        @NotBlank String authors
) {}
