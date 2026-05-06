package microservice.service.paper.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import microservice.service.paper.dto.ConferenceFileDownload;
import microservice.service.paper.dto.PaperCreateDto;
import microservice.service.paper.dto.PaperEvaluationDto;
import microservice.service.paper.dto.PaperResponseDto;
import microservice.service.paper.enums.PaperStatus;
import microservice.service.paper.model.Paper;
import microservice.service.paper.model.PaperAttachment;
import microservice.service.paper.repository.PaperAttachmentRepository;
import microservice.service.paper.repository.PaperRepository;
import microservice.service.paper.service.FileOptimizer.OptimizedPayload;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import microservice.service.paper.config.RabbitMQConfig;
import microservice.service.paper.dto.PaperEvaluatedEvent;
import java.time.Instant;
import java.util.Arrays;

@Service
public class PaperService {

    private final PaperRepository repository;
    private final PaperAttachmentRepository attachmentRepository;
    private final FileStorageService fileStorageService;
    private final FileOptimizer fileOptimizer;
    private final RabbitTemplate rabbitTemplate;

    public PaperService(
            PaperRepository repository,
            PaperAttachmentRepository attachmentRepository,
            FileStorageService fileStorageService,
            FileOptimizer fileOptimizer,
            RabbitTemplate rabbitTemplate) {
        this.repository = repository;
        this.attachmentRepository = attachmentRepository;
        this.fileStorageService = fileStorageService;
        this.fileOptimizer = fileOptimizer;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Transactional
    public PaperResponseDto create(UUID conferenceId, PaperCreateDto dto, List<MultipartFile> files) throws IOException {
        Paper paper = new Paper();
        paper.setConferenceId(conferenceId);
        paper.setTitle(dto.getTitle().trim());
        paper.setAbstractText(dto.getAbstractText().trim());
        paper.setTopic(dto.getTopic().trim());
        paper.setInstitutionalAffiliation(dto.getInstitutionalAffiliation().trim());
        paper.setKeywords(dto.getKeywords().trim());
        paper.setAuthors(dto.getAuthors().trim());
        paper.setStatus(PaperStatus.SUBMITTED);
        paper.setAttachments(new ArrayList<>());

        for (MultipartFile file : normalizeFiles(files)) {
            PaperAttachment att = buildAttachment(paper, file);
            paper.getAttachments().add(att);
        }

        Paper saved = repository.save(paper);
        return PaperResponseDto.from(
                repository.findByIdAndConferenceIdWithAttachments(saved.getId(), conferenceId).orElse(saved));
    }

    @Transactional(readOnly = true)
    public List<PaperResponseDto> listByConference(UUID conferenceId, PaperStatus status) {
        List<Paper> papers = status == null
                ? repository.findByConferenceIdWithAttachments(conferenceId)
                : repository.findByConferenceIdAndStatusWithAttachments(conferenceId, status);
        return papers.stream().map(PaperResponseDto::from).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PaperResponseDto> listEvaluationTray(UUID conferenceId) {
        return repository.findByConferenceIdAndStatusWithAttachments(conferenceId, PaperStatus.SUBMITTED).stream()
                .map(PaperResponseDto::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PaperResponseDto getById(UUID conferenceId, UUID paperId) {
        Paper paper = requirePaperWithAttachments(conferenceId, paperId);
        return PaperResponseDto.from(paper);
    }

    @Transactional
    public PaperResponseDto evaluate(UUID conferenceId, UUID paperId, PaperEvaluationDto dto) {
        Paper paper = requirePaperWithAttachments(conferenceId, paperId);
        paper.setStatus(dto.status());
        paper.setEvaluationObservations(
                dto.observations() != null ? dto.observations().trim() : null);
        repository.save(paper);

        // Map authors from string to Author objects
        List<PaperEvaluatedEvent.Author> authors = paper.getAuthors() != null 
                ? Arrays.stream(paper.getAuthors().split(","))
                        .map(name -> new PaperEvaluatedEvent.Author(name.trim(), "author@example.com")) // Email placeholder
                        .toList()
                : Collections.emptyList();

        // Create the event according to the contract (Envelope + Data)
        PaperEvaluatedEvent event = new PaperEvaluatedEvent(
                "paper.evaluated",
                "1.0",
                UUID.randomUUID(),
                Instant.now(),
                "paper-service",
                new PaperEvaluatedEvent.Data(
                        paper.getId(),
                        paper.getConferenceId(),
                        paper.getTitle(),
                        paper.getTopic(),
                        paper.getStatus().name(),
                        paper.getEvaluationObservations(),
                        new PaperEvaluatedEvent.EvaluatedBy(UUID.randomUUID(), "CHAIR"), // Evaluator placeholders
                        authors
                )
        );

        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_KEY_EVALUATED, event);

        return PaperResponseDto.from(
                repository.findByIdAndConferenceIdWithAttachments(paperId, conferenceId).orElse(paper));
    }

    @Transactional
    public PaperResponseDto uploadDocuments(UUID conferenceId, UUID paperId, List<MultipartFile> files)
            throws IOException {
        Paper paper = repository.findById(paperId)
                .filter(p -> conferenceId.equals(p.getConferenceId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Artículo no encontrado"));

        if (paper.getAttachments() == null) {
            paper.setAttachments(new ArrayList<>());
        }
        for (MultipartFile file : normalizeFiles(files)) {
            paper.getAttachments().add(buildAttachment(paper, file));
        }
        repository.save(paper);
        return PaperResponseDto.from(
                repository.findByIdAndConferenceIdWithAttachments(paperId, conferenceId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Artículo no encontrado")));
    }

    @Transactional(readOnly = true)
    public ConferenceFileDownload downloadDocument(UUID conferenceId, UUID paperId, UUID attachmentId) {
        PaperAttachment att = attachmentRepository
                .findByIdAndPaperAndConference(attachmentId, paperId, conferenceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Adjunto no encontrado"));
        String key = att.getObjectName();
        byte[] content = fileStorageService.downloadFile(key);
        String name = att.getOriginalFileName() != null ? att.getOriginalFileName() : "adjunto";
        String ct = att.getContentType() != null ? att.getContentType() : "application/octet-stream";
        return new ConferenceFileDownload(content, ct, name);
    }

    private Paper requirePaperWithAttachments(UUID conferenceId, UUID paperId) {
        return repository.findByIdAndConferenceIdWithAttachments(paperId, conferenceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Artículo no encontrado"));
    }

    private static List<MultipartFile> normalizeFiles(List<MultipartFile> files) {
        if (files == null) {
            return Collections.emptyList();
        }
        return files.stream().filter(f -> f != null && !f.isEmpty()).toList();
    }

    private PaperAttachment buildAttachment(Paper paper, MultipartFile file) throws IOException {
        OptimizedPayload optimized = fileOptimizer.optimize(file);
        String key = fileStorageService.uploadOptimized(
                optimized.data(),
                optimized.contentType(),
                file.getOriginalFilename());
        PaperAttachment att = new PaperAttachment();
        att.setPaper(paper);
        att.setObjectName(key);
        att.setOriginalFileName(file.getOriginalFilename());
        att.setContentType(optimized.contentType());
        att.setFileSize((long) optimized.size());
        return att;
    }
}
