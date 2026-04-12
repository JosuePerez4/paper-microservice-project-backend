package microservice.service.paper.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import microservice.service.paper.enums.PaperStatus;
import microservice.service.paper.model.Paper;

@Repository
public interface PaperRepository extends JpaRepository<Paper, UUID> {

    @Query("SELECT DISTINCT p FROM Paper p LEFT JOIN FETCH p.attachments WHERE p.conferenceId = :conferenceId")
    List<Paper> findByConferenceIdWithAttachments(@Param("conferenceId") UUID conferenceId);

    @Query("SELECT DISTINCT p FROM Paper p LEFT JOIN FETCH p.attachments WHERE p.conferenceId = :conferenceId AND p.status = :status")
    List<Paper> findByConferenceIdAndStatusWithAttachments(
            @Param("conferenceId") UUID conferenceId,
            @Param("status") PaperStatus status);

    @Query("SELECT p FROM Paper p LEFT JOIN FETCH p.attachments WHERE p.id = :id AND p.conferenceId = :conferenceId")
    Optional<Paper> findByIdAndConferenceIdWithAttachments(
            @Param("id") UUID id,
            @Param("conferenceId") UUID conferenceId);

    List<Paper> findByStatus(PaperStatus status);
}
