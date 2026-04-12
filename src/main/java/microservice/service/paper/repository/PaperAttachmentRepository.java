package microservice.service.paper.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import microservice.service.paper.model.PaperAttachment;

@Repository
public interface PaperAttachmentRepository extends JpaRepository<PaperAttachment, UUID> {

    @Query("SELECT a FROM PaperAttachment a JOIN FETCH a.paper p WHERE a.id = :attId AND p.id = :paperId AND p.conferenceId = :conferenceId")
    Optional<PaperAttachment> findByIdAndPaperAndConference(
            @Param("attId") UUID attachmentId,
            @Param("paperId") UUID paperId,
            @Param("conferenceId") UUID conferenceId);
}
