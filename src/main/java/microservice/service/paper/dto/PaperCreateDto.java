package microservice.service.paper.dto;

import jakarta.validation.constraints.NotBlank;
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
    @NotBlank private String authors;
}
