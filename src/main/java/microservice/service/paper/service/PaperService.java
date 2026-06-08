package microservice.service.paper.service;



import java.io.IOException;

import java.util.ArrayList;

import java.util.Collections;

import java.util.LinkedHashSet;

import java.util.List;

import java.util.UUID;

import java.util.stream.Collectors;



import org.slf4j.Logger;

import org.slf4j.LoggerFactory;

import org.springframework.amqp.rabbit.core.RabbitTemplate;

import org.springframework.http.HttpStatus;

import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import org.springframework.web.multipart.MultipartFile;

import org.springframework.web.server.ResponseStatusException;



import microservice.service.paper.client.AuthClient;

import microservice.service.paper.config.RabbitMQConfig;

import microservice.service.paper.dto.ArticleAcceptedEventDTO;

import microservice.service.paper.dto.ConferenceFileDownload;

import microservice.service.paper.dto.PaperAuthorDto;

import microservice.service.paper.dto.PaperCreateDto;

import microservice.service.paper.dto.PaperEvaluationDto;

import microservice.service.paper.dto.PaperEvaluatedEvent;

import microservice.service.paper.dto.PaperResponseDto;

import microservice.service.paper.enums.PaperStatus;

import microservice.service.paper.model.Paper;

import microservice.service.paper.model.PaperAttachment;

import microservice.service.paper.repository.PaperAttachmentRepository;

import microservice.service.paper.repository.PaperRepository;

import microservice.service.paper.service.FileOptimizer.OptimizedPayload;

import java.time.Instant;



@Service

public class PaperService {

    private static final Logger log = LoggerFactory.getLogger(PaperService.class);
    private static final String ROLE_GUEST_SPOKER = "GUEST_SPOKER";
    private static final String ROLE_AUTHOR = "AUTHOR";

    private final PaperRepository repository;

    private final PaperAttachmentRepository attachmentRepository;

    private final FileStorageService fileStorageService;

    private final FileOptimizer fileOptimizer;

    private final RabbitTemplate rabbitTemplate;

    private final AuthClient authClient;



    public PaperService(

            PaperRepository repository,

            PaperAttachmentRepository attachmentRepository,

            FileStorageService fileStorageService,

            FileOptimizer fileOptimizer,

            RabbitTemplate rabbitTemplate,

            AuthClient authClient) {

        this.repository = repository;

        this.attachmentRepository = attachmentRepository;

        this.fileStorageService = fileStorageService;

        this.fileOptimizer = fileOptimizer;

        this.rabbitTemplate = rabbitTemplate;

        this.authClient = authClient;

    }



    @Transactional

    public PaperResponseDto create(

            UUID conferenceId,

            PaperCreateDto dto,

            List<MultipartFile> files,

            UUID submitterId,

            String submitterRole,

            String authorizationHeader) throws IOException {

        List<UUID> authorIds = normalizeAuthorIds(dto.getAuthorIds());

        if (!authorIds.contains(submitterId)) {

            throw new ResponseStatusException(

                    HttpStatus.BAD_REQUEST,

                    "El usuario que envía el artículo debe estar incluido en la lista de autores");

        }

        if (dto.getPresenterId() == null) {

            throw new ResponseStatusException(

                    HttpStatus.BAD_REQUEST,

                    "Debe especificar un ponente para el artículo");

        }

        if (!authorIds.contains(dto.getPresenterId())) {

            throw new ResponseStatusException(

                    HttpStatus.BAD_REQUEST,

                    "El ponente debe estar incluido en la lista de autores");

        }



        List<PaperAuthorDto> validatedAuthors = authClient.validatePaperAuthors(authorIds, authorizationHeader);

        Paper paper = new Paper();

        paper.setConferenceId(conferenceId);

        paper.setSubmittedByUserId(submitterId);

        paper.setAuthorIds(new ArrayList<>(authorIds));

        paper.setPresenterId(dto.getPresenterId());

        paper.setTitle(dto.getTitle().trim());

        paper.setAbstractText(dto.getAbstractText().trim());

        paper.setTopic(dto.getTopic().trim());

        paper.setInstitutionalAffiliation(dto.getInstitutionalAffiliation().trim());

        paper.setKeywords(dto.getKeywords().trim());

        boolean guestSpeakerSubmission = ROLE_GUEST_SPOKER.equalsIgnoreCase(submitterRole);
        paper.setStatus(guestSpeakerSubmission ? PaperStatus.ACCEPTED : PaperStatus.SUBMITTED);

        paper.setAttachments(new ArrayList<>());



        for (MultipartFile file : normalizeFiles(files)) {

            PaperAttachment att = buildAttachment(paper, file);

            paper.getAttachments().add(att);

        }



        Paper saved = repository.save(paper);

        if (guestSpeakerSubmission) {
            publishArticleAcceptedEvent(saved);
            log.info("Artículo {} de ponente invitado auto-aceptado en conferencia {}", saved.getId(), conferenceId);
        }

        Paper loaded = repository.findByIdAndConferenceIdWithAttachments(saved.getId(), conferenceId)

                .orElse(saved);

        return PaperResponseDto.from(loaded, validatedAuthors);

    }


    @Transactional(readOnly = true)

    public List<PaperResponseDto> listByConference(UUID conferenceId, PaperStatus status) {

        List<Paper> papers = status == null

                ? repository.findByConferenceIdWithAttachments(conferenceId)

                : repository.findByConferenceIdAndStatusWithAttachments(conferenceId, status);

        return papers.stream().map(PaperResponseDto::from).collect(Collectors.toList());

    }



    @Transactional(readOnly = true)

    public List<PaperResponseDto> listMyPapersByConference(
            UUID conferenceId,
            UUID userId,
            String authorizationHeader) {

        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no autenticado");
        }

        return repository.findByConferenceIdAndUserInvolvedWithAttachments(conferenceId, userId).stream()
                .map(paper -> PaperResponseDto.from(paper, resolveAuthorsForPaper(paper, authorizationHeader)))
                .collect(Collectors.toList());
    }



    @Transactional(readOnly = true)

    public List<PaperResponseDto> listEvaluationTray(UUID conferenceId) {

        return repository.findByConferenceIdAndStatusWithAttachments(conferenceId, PaperStatus.SUBMITTED).stream()

                .map(PaperResponseDto::from)

                .collect(Collectors.toList());

    }



    @Transactional(readOnly = true)

    public List<PaperResponseDto> listApprovedForVisitors(UUID conferenceId) {

        return repository

                .findByConferenceIdAndStatusWithAttachments(conferenceId, PaperStatus.ACCEPTED)

                .stream()

                .map(PaperResponseDto::from)

                .collect(Collectors.toList());

    }



    @Transactional(readOnly = true)

    public PaperResponseDto getById(
            UUID conferenceId,
            UUID paperId,
            UUID requesterId,
            String requesterRole,
            String authorizationHeader) {

        Paper paper = requirePaperWithAttachments(conferenceId, paperId);
        ensureCanAccessPaper(paper, requesterId, requesterRole);

        return PaperResponseDto.from(paper, resolveAuthorsForPaper(paper, authorizationHeader));
    }



    @Transactional

    public PaperResponseDto evaluate(

            UUID conferenceId,

            UUID paperId,

            PaperEvaluationDto dto,

            String authorizationHeader) {

        Paper paper = requirePaperWithAttachments(conferenceId, paperId);

        paper.setStatus(dto.status());

        paper.setEvaluationObservations(

                dto.observations() != null ? dto.observations().trim() : null);

        repository.save(paper);



        List<PaperEvaluatedEvent.Author> authors = buildEventAuthors(paper, authorizationHeader);



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

                        new PaperEvaluatedEvent.EvaluatedBy(UUID.randomUUID(), "CHAIR"),

                        authors));



        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_KEY_EVALUATED, event);



        if (paper.getStatus() == PaperStatus.ACCEPTED) {

            publishArticleAcceptedEvent(paper);

        }



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



    private List<PaperEvaluatedEvent.Author> buildEventAuthors(Paper paper, String authorizationHeader) {

        if (paper.getAuthorIds() == null || paper.getAuthorIds().isEmpty()) {

            return Collections.emptyList();

        }



        try {

            return authClient.validatePaperAuthors(paper.getAuthorIds(), authorizationHeader).stream()

                    .map(author -> new PaperEvaluatedEvent.Author(

                            author.getDisplayName() != null ? author.getDisplayName() : author.getEmail(),

                            author.getEmail()))

                    .toList();

        } catch (ResponseStatusException ex) {

            log.warn("No se pudieron resolver autores del paper {} para el evento: {}", paper.getId(), ex.getMessage());

            return Collections.emptyList();

        }

    }



    private static List<UUID> normalizeAuthorIds(List<UUID> authorIds) {

        if (authorIds == null || authorIds.isEmpty()) {

            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe incluir al menos un autor");

        }

        return new ArrayList<>(new LinkedHashSet<>(authorIds));

    }



    private Paper requirePaperWithAttachments(UUID conferenceId, UUID paperId) {

        return repository.findByIdAndConferenceIdWithAttachments(paperId, conferenceId)

                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Artículo no encontrado"));

    }



    private List<PaperAuthorDto> resolveAuthorsForPaper(Paper paper, String authorizationHeader) {

        if (paper.getAuthorIds() == null || paper.getAuthorIds().isEmpty()) {

            return Collections.emptyList();

        }



        try {

            return authClient.validatePaperAuthors(paper.getAuthorIds(), authorizationHeader);

        } catch (ResponseStatusException ex) {

            log.warn("No se pudieron resolver autores del paper {}: {}", paper.getId(), ex.getMessage());

            return Collections.emptyList();

        }

    }



    private void ensureCanAccessPaper(Paper paper, UUID requesterId, String requesterRole) {

        if (!isAuthorRestrictedRole(requesterRole)) {

            return;

        }

        if (requesterId == null || !isUserInvolvedInPaper(paper, requesterId)) {

            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes permiso para consultar este artículo");

        }

    }



    private static boolean isAuthorRestrictedRole(String role) {

        if (role == null || role.isBlank()) {

            return true;

        }

        String normalized = role.startsWith("ROLE_") ? role.substring(5) : role;

        return ROLE_AUTHOR.equalsIgnoreCase(normalized) || ROLE_GUEST_SPOKER.equalsIgnoreCase(normalized);

    }



    private static boolean isUserInvolvedInPaper(Paper paper, UUID userId) {

        if (userId == null) {

            return false;

        }

        if (userId.equals(paper.getSubmittedByUserId())) {

            return true;

        }

        return paper.getAuthorIds() != null && paper.getAuthorIds().contains(userId);

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



    private void publishArticleAcceptedEvent(Paper paper) {

        if (paper.getAuthorIds() == null || paper.getAuthorIds().isEmpty()) {

            log.warn("Artículo {} aceptado sin autores; no se publica article.accepted", paper.getId());

            return;

        }



        ArticleAcceptedEventDTO acceptedEvent = new ArticleAcceptedEventDTO(

                paper.getId(),

                paper.getConferenceId(),

                new ArrayList<>(paper.getAuthorIds()),

                paper.getPresenterId());



        rabbitTemplate.convertAndSend(

                RabbitMQConfig.ARTICLE_EXCHANGE,

                RabbitMQConfig.ROUTING_KEY_ARTICLE_ACCEPTED,

                acceptedEvent);



        log.info("Evento article.accepted publicado para artículo {} con autores {}",

                paper.getId(), paper.getAuthorIds());

    }

}

