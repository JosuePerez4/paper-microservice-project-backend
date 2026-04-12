package microservice.service.paper.dto;

public record ConferenceFileDownload(
        byte[] content,
        String contentType,
        String originalFileName
) {}
