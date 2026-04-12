package microservice.service.paper.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import microservice.service.paper.enums.PaperStatus;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Paper {
    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    private UUID conferenceId;

    private String title;

    @Column(length = 8000)
    private String abstractText;

    private String topic;

    private String institutionalAffiliation;

    @Column(length = 2000)
    private String keywords;

    @Column(length = 4000)
    private String authors;

    @Enumerated(EnumType.STRING)
    private PaperStatus status = PaperStatus.SUBMITTED;

    @Column(length = 8000)
    private String evaluationObservations;

    @OneToMany(mappedBy = "paper", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PaperAttachment> attachments = new ArrayList<>();
}
