package microservice.service.paper.dto;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaperAuthorDto {

    private UUID id;
    private String firstName;
    private String lastName;
    private String email;
    private String displayName;
    private String role;
}
