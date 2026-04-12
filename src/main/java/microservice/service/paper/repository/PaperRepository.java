package microservice.service.paper.repository;

import microservice.service.paper.model.Paper;
import microservice.service.paper.enums.PaperStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PaperRepository extends JpaRepository<Paper, UUID> {
    List<Paper> findByStatus(PaperStatus status);
}
