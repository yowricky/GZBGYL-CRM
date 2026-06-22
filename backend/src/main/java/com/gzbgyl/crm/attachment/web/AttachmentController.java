package com.gzbgyl.crm.attachment.web;

import com.gzbgyl.crm.attachment.application.AttachmentService;
import com.gzbgyl.crm.shared.security.CurrentUserService;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/attachments")
public class AttachmentController {
    private final AttachmentService service;
    private final CurrentUserService currentUser;

    public AttachmentController(AttachmentService service, CurrentUserService currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<AttachmentService.AttachmentDto> upload(
            @RequestParam String ownerType,
            @RequestParam UUID ownerId,
            @RequestParam MultipartFile file) {
        var result = service.upload(currentUser.required().id(), ownerType, ownerId, file);
        return ResponseEntity.status(201).body(result);
    }

    @GetMapping("/{id}")
    ResponseEntity<InputStreamResource> download(@PathVariable UUID id) throws IOException {
        var download = service.download(currentUser.required().id(), id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .contentLength(download.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(download.filename()))
                .body(new InputStreamResource(download.stream()));
    }

    @DeleteMapping("/{id}")
    AttachmentService.AttachmentDto delete(@PathVariable UUID id, @RequestParam long expectedVersion) {
        return service.delete(currentUser.required().id(), id, expectedVersion);
    }

    private static String contentDisposition(String filename) {
        String ascii = filename.replace("\\", "_").replace("\"", "_")
                .replace("\r", " ").replace("\n", " ");
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return ContentDisposition.attachment().filename(ascii).build() + "; filename*=UTF-8''" + encoded;
    }
}
