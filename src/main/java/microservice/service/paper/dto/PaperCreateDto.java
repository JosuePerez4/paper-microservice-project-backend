package microservice.service.paper.dto;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaperCreateDto {
    private String title;
    private String abstractText;
    private UUID conferenceId;
}
