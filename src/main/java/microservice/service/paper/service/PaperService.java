package microservice.service.paper.service;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import microservice.service.paper.dto.ConferenceFileDownload;
import microservice.service.paper.dto.PaperCreateDto;
import microservice.service.paper.dto.PaperEvaluationDto;
import microservice.service.paper.dto.PaperResponseDto;
import microservice.service.paper.enums.PaperStatus;
import microservice.service.paper.model.Paper;
import microservice.service.paper.repository.PaperRepository;

@Service
public class PaperService {

    private final PaperRepository repository;
    private final FileStorageService fileStorageService;
    private final FileOptimizer fileOptimizer;

    public PaperService(
            PaperRepository repository,
            FileStorageService fileStorageService,
            FileOptimizer fileOptimizer) {
        this.repository = repository;
        this.fileStorageService = fileStorageService;
        this.fileOptimizer = fileOptimizer;
    }

    public PaperResponseDto create(UUID conferenceId, PaperCreateDto dto) {
        Paper paper = new Paper();
        paper.setConferenceId(conferenceId);
        paper.setTitle(dto.title().trim());
        paper.setAbstractText(dto.abstractText().trim());
        paper.setTopic(dto.topic().trim());
        paper.setInstitutionalAffiliation(dto.institutionalAffiliation().trim());
        paper.setKeywords(dto.keywords().trim());
        paper.setAuthors(dto.authors().trim());
        paper.setStatus(PaperStatus.SUBMITTED);
        return new PaperResponseDto(repository.save(paper));
    }

    public List<PaperResponseDto> listByConference(UUID conferenceId, PaperStatus status) {
        List<Paper> papers = status == null
                ? repository.findByConferenceId(conferenceId)
                : repository.findByConferenceIdAndStatus(conferenceId, status);
        return papers.stream().map(PaperResponseDto::new).collect(Collectors.toList());
    }

    public List<PaperResponseDto> listEvaluationTray(UUID conferenceId) {
        return repository.findByConferenceIdAndStatus(conferenceId, PaperStatus.SUBMITTED).stream()
                .map(PaperResponseDto::new)
                .collect(Collectors.toList());
    }

    public PaperResponseDto getById(UUID conferenceId, UUID paperId) {
        Paper paper = requirePaperInConference(conferenceId, paperId);
        return new PaperResponseDto(paper);
    }

    public PaperResponseDto evaluate(UUID conferenceId, UUID paperId, PaperEvaluationDto dto) {
        Paper paper = requirePaperInConference(conferenceId, paperId);
        paper.setStatus(dto.status());
        paper.setEvaluationObservations(
                dto.observations() != null ? dto.observations().trim() : null);
        return new PaperResponseDto(repository.save(paper));
    }

    public PaperResponseDto uploadDocument(UUID conferenceId, UUID paperId, MultipartFile file) throws IOException {
        Paper paper = requirePaperInConference(conferenceId, paperId);
        if (paper.getDocumentObjectName() != null && !paper.getDocumentObjectName().isBlank()) {
            fileStorageService.deleteFile(paper.getDocumentObjectName());
        }
        FileOptimizer.OptimizedPayload optimized = fileOptimizer.optimize(file);
        String key = fileStorageService.uploadOptimized(
                optimized.data(),
                optimized.contentType(),
                file.getOriginalFilename());
        paper.setDocumentObjectName(key);
        paper.setDocumentOriginalFileName(file.getOriginalFilename());
        paper.setDocumentContentType(optimized.contentType());
        return new PaperResponseDto(repository.save(paper));
    }

    public ConferenceFileDownload downloadDocument(UUID conferenceId, UUID paperId) {
        Paper paper = requirePaperInConference(conferenceId, paperId);
        String key = paper.getDocumentObjectName();
        if (key == null || key.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "El artículo no tiene documento adjunto");
        }
        byte[] content = fileStorageService.downloadFile(key);
        String name = paper.getDocumentOriginalFileName() != null
                ? paper.getDocumentOriginalFileName()
                : "articulo";
        String ct = paper.getDocumentContentType() != null
                ? paper.getDocumentContentType()
                : "application/octet-stream";
        return new ConferenceFileDownload(content, ct, name);
    }

    private Paper requirePaperInConference(UUID conferenceId, UUID paperId) {
        return repository.findById(paperId)
                .filter(p -> conferenceId.equals(p.getConferenceId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Artículo no encontrado"));
    }
}
