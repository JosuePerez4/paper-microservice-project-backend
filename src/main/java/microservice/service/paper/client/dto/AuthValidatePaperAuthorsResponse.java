package microservice.service.paper.client.dto;

import java.util.List;

import lombok.Data;

@Data
public class AuthValidatePaperAuthorsResponse {
    private List<AuthPaperAuthorResponse> authors;
}
