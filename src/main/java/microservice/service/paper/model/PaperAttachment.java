package microservice.service.paper.model;

import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "paper_attachment")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaperAttachment {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "paper_id")
    private Paper paper;

    /** Clave del objeto en almacenamiento (p. ej. Backblaze). */
    private String objectName;

    private String originalFileName;

    private String contentType;

    /** Tamaño en bytes tras la optimización. */
    private Long fileSize;
}
