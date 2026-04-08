package microservice.service.paper.repository;

import microservice.service.paper.model.Paper;
import microservice.service.paper.enums.PaperStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaperRepository extends JpaRepository<Paper, Long> {
    List<Paper> findByStatus(PaperStatus status);
}
