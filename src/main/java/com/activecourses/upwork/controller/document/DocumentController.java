package com.activecourses.upwork.controller.document;

import com.activecourses.upwork.dto.ContractDocumentDTO;
import com.activecourses.upwork.dto.ResponseDto;
import com.activecourses.upwork.model.ContractDocument;
import com.activecourses.upwork.repository.document.ContractDocumentRepository;
import com.activecourses.upwork.service.document.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Tag(name = "Documentos", description = "Upload e gestão de documentos")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/documents/")
public class DocumentController {

    private final DocumentService documentService;
    private final ContractDocumentRepository documentRepository;

    @Operation(summary = "Upload documento", description = "Faz upload de um documento para um contrato",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('CLIENT') or hasRole('LAWYER')")
    @PostMapping("/upload/{contractId}")
    public ResponseEntity<ResponseDto> uploadDocument(
            @PathVariable int contractId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String description,
            @RequestParam(defaultValue = "other") String category) {
        ContractDocumentDTO doc = documentService.uploadDocument(contractId, file, description, category);
        return buildResponse(HttpStatus.CREATED, true, doc, null);
    }

    @Operation(summary = "Listar documentos", description = "Lista documentos de um contrato",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('CLIENT') or hasRole('LAWYER')")
    @GetMapping("/list/{contractId}")
    public ResponseEntity<ResponseDto> getDocuments(@PathVariable int contractId) {
        List<ContractDocumentDTO> docs = documentService.getDocuments(contractId);
        return buildResponse(HttpStatus.OK, true, docs, null);
    }

    @Operation(summary = "Download documento", description = "Download de um documento pelo ID",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('CLIENT') or hasRole('LAWYER')")
    @GetMapping("/download/{documentId}")
    public ResponseEntity<Resource> downloadDocument(@PathVariable int documentId) {
        ContractDocument doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));

        Path filePath = Paths.get(doc.getStoragePath());
        Resource resource = new FileSystemResource(filePath.toFile());

        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(doc.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + doc.getFileName() + "\"")
                .body(resource);
    }

    @Operation(summary = "Eliminar documento", description = "Apaga um documento",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('CLIENT') or hasRole('LAWYER')")
    @DeleteMapping("/{documentId}")
    public ResponseEntity<ResponseDto> deleteDocument(@PathVariable int documentId) {
        documentService.deleteDocument(documentId);
        return buildResponse(HttpStatus.OK, true, null, null);
    }

    private ResponseEntity<ResponseDto> buildResponse(HttpStatus status, boolean success, Object data, Object error) {
        return ResponseEntity.status(status)
                .body(ResponseDto.builder().status(status).success(success).data(data).error(error).build());
    }
}
