package microservice.service.paper.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import microservice.service.paper.model.ConferenceSupportFile;

@Repository
public interface ConferenceSupportFileRepository extends JpaRepository<ConferenceSupportFile, UUID> {
    List<ConferenceSupportFile> findByConferenceId(UUID conferenceId);
}
