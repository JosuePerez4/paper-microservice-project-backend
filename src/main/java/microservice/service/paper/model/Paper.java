package microservice.service.paper.model;

import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import microservice.service.paper.enums.PaperStatus;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Paper {
    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    private String title;
    private String abstractText;

    @Enumerated(EnumType.STRING)
    private PaperStatus status = PaperStatus.SUBMITTED;

    private UUID conferenceId;
}
