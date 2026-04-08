package microservice.service.paper.service;

import microservice.service.paper.model.Paper;
import microservice.service.paper.enums.PaperStatus;
import microservice.service.paper.repository.PaperRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PaperService {

    private final PaperRepository repository;

    public PaperService(PaperRepository repository) {
        this.repository = repository;
    }

    public Paper save(Paper paper){
        return repository.save(paper);
    }

    public List<Paper> getPapersForEvaluation() {
        return repository.findByStatus(PaperStatus.SUBMITTED);
    }

    public List<Paper> getPapersByStatus(PaperStatus status) {
        if (status == null) {
            return repository.findAll();
        }
        return repository.findByStatus(status);
    }

    public Paper evaluatePaper(Long id, PaperStatus newStatus) {
        Paper paper = repository.findById(id).orElseThrow(() -> new RuntimeException("Paper not found"));
        paper.setStatus(newStatus);
        return repository.save(paper);
    }
}
