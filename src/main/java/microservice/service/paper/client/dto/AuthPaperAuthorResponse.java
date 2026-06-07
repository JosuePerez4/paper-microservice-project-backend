package microservice.service.paper.client.dto;

import java.util.UUID;

import lombok.Data;

@Data
public class AuthPaperAuthorResponse {
    private UUID id;
    private String firstName;
    private String lastName;
    private String email;
    private String displayName;
    private String role;
}
