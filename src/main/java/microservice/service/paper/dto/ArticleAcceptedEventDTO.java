package microservice.service.paper.dto;



import java.util.List;

import java.util.UUID;



import lombok.AllArgsConstructor;

import lombok.Data;

import lombok.NoArgsConstructor;



@Data
@NoArgsConstructor
@AllArgsConstructor
public class ArticleAcceptedEventDTO {

    private UUID articleId;

    private UUID conferenceId;

    private List<UUID> authorIds;

    private UUID presenterId;

}