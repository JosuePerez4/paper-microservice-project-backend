package microservice.service.paper.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import microservice.service.paper.enums.PaperStatus;
import microservice.service.paper.model.Paper;

@Repository
public interface PaperRepository extends JpaRepository<Paper, UUID> {

    List<Paper> findByConferenceId(UUID conferenceId);

    List<Paper> findByConferenceIdAndStatus(UUID conferenceId, PaperStatus status);

    List<Paper> findByStatus(PaperStatus status);
}
