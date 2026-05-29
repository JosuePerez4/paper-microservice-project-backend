package microservice.service.paper.client.dto;

import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthValidatePaperAuthorsRequest {
    private List<UUID> userIds;
}
