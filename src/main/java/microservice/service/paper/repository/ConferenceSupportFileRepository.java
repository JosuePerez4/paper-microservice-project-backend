package microservice.service.paper.repository;

import microservice.service.paper.model.ConferenceSupportFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConferenceSupportFileRepository extends JpaRepository<ConferenceSupportFile, Long> {
    List<ConferenceSupportFile> findByConferenceId(Long conferenceId);
}
