package microservice.service.paper.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaperCreateDto {

    @NotBlank private String title;
    @NotBlank private String abstractText;
    @NotBlank private String topic;
    @NotBlank private String institutionalAffiliation;
    @NotBlank private String keywords;

    @NotEmpty(message = "Debe incluir al menos un autor registrado")
    private List<UUID> authorIds;

    @NotNull(message = "Debe especificar el ponente de este artículo")
    private UUID presenterId;
}
