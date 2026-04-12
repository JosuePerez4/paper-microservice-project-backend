package microservice.service.paper.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import microservice.service.paper.dto.PaperCreateDto;
import microservice.service.paper.enums.PaperStatus;
import microservice.service.paper.model.Paper;
import microservice.service.paper.repository.PaperRepository;

@Service
public class PaperService {

    private final PaperRepository repository;

    public PaperService(PaperRepository repository) {
        this.repository = repository;
    }

    public Paper save(Paper paper){
        return repository.save(paper);
    }

    public Paper create(PaperCreateDto paperCreateDto){
        Paper paper = new Paper();
        paper.setTitle(paperCreateDto.getTitle());
        paper.setAbstractText(paperCreateDto.getAbstractText());
        paper.setConferenceId(paperCreateDto.getConferenceId());
        paper.setStatus(PaperStatus.SUBMITTED);
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

    public Paper evaluatePaper(UUID id, PaperStatus newStatus) {
        Paper paper = repository.findById(id).orElseThrow(() -> new RuntimeException("Paper not found"));
        paper.setStatus(newStatus);
        return repository.save(paper);
    }
}
