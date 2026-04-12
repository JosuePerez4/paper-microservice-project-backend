package microservice.service.paper.controller;

import microservice.service.paper.model.Paper;
import microservice.service.paper.service.PaperService;
import microservice.service.paper.dto.PaperCreateDto;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import microservice.service.paper.enums.PaperStatus;
import microservice.service.paper.dto.PaperEvaluationDto;
import java.util.UUID;

@RestController
@RequestMapping("/papers")
public class PaperController {

    private final PaperService service;

    public PaperController(PaperService service) {
        this.service = service;
    }

    @PostMapping
    public Paper create(@RequestBody PaperCreateDto paperCreateDto){
        return service.create(paperCreateDto);
    }

    @GetMapping("/evaluation-tray")
    public java.util.List<Paper> getEvaluationTray() {
        return service.getPapersForEvaluation();
    }

    @GetMapping
    public java.util.List<Paper> listPapers(@RequestParam(required = false) PaperStatus status) {
        return service.getPapersByStatus(status);
    }

    @PatchMapping("/{id}/evaluate")
    public Paper evaluatePaper(@PathVariable UUID id, @RequestBody PaperEvaluationDto evaluationDto) {
        return service.evaluatePaper(id, evaluationDto.getStatus());
    }
}
