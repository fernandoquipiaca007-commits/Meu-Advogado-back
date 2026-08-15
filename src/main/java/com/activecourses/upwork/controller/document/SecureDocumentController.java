package com.activecourses.upwork.controller.document;

import com.activecourses.upwork.dto.DocumentAccessLogDto;
import com.activecourses.upwork.dto.ResponseDto;
import com.activecourses.upwork.dto.SecureDocumentDto;
import com.activecourses.upwork.model.DocumentClassification;
import com.activecourses.upwork.service.document.SecureDocumentService;
import com.activecourses.upwork.service.document.SecureDocumentService.SecureDownloadInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "Cofre de Documentos Seguros", description = "Armazenamento criptografado, validação de integridade por SHA-256 e auditoria forense")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/documents/secure")
public class SecureDocumentController {

    private final SecureDocumentService secureDocumentService;

    @Operation(summary = "Upload de documento seguro", description = "Realiza o upload com cálculo de hash SHA-256 e gravação de log de acesso",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResponseDto> uploadSecureDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "contractId", required = false) Integer contractId,
            @RequestParam(value = "jobId", required = false) Integer jobId,
            @RequestParam(value = "classification", defaultValue = "CONFIDENTIAL") DocumentClassification classification,
            @RequestParam(value = "description", required = false) String description,
            HttpServletRequest request) {

        SecureDocumentDto doc = secureDocumentService.uploadSecureDocument(
                file, contractId, jobId, classification, description, request
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ResponseDto.builder()
                        .status(HttpStatus.CREATED)
                        .success(true)
                        .data(doc)
                        .build());
    }

    @Operation(summary = "Download de documento seguro", description = "Download protegido com autorização estrita, retorno de cabeçalho X-Document-SHA256 e log de auditoria",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/{documentId}/download")
    public ResponseEntity<Resource> downloadSecureDocument(
            @PathVariable Long documentId,
            HttpServletRequest request) {

        SecureDownloadInfo info = secureDocumentService.downloadSecureDocument(documentId, request);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(info.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + info.fileName() + "\"")
                .header("X-Document-SHA256", info.sha256Hash())
                .contentLength(info.fileSize())
                .body(info.resource());
    }

    @Operation(summary = "Listar documentos do contrato", description = "Lista documentos seguros vinculados a um contrato",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/contract/{contractId}")
    public ResponseEntity<ResponseDto> getDocumentsByContract(@PathVariable Integer contractId) {
        List<SecureDocumentDto> docs = secureDocumentService.getDocumentsByContract(contractId);
        return ResponseEntity.ok(ResponseDto.builder()
                .status(HttpStatus.OK)
                .success(true)
                .data(docs)
                .build());
    }

    @Operation(summary = "Listar documentos da demanda", description = "Lista documentos seguros vinculados a uma demanda",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/job/{jobId}")
    public ResponseEntity<ResponseDto> getDocumentsByJob(@PathVariable Integer jobId) {
        List<SecureDocumentDto> docs = secureDocumentService.getDocumentsByJob(jobId);
        return ResponseEntity.ok(ResponseDto.builder()
                .status(HttpStatus.OK)
                .success(true)
                .data(docs)
                .build());
    }

    @Operation(summary = "Listar meus documentos seguros", description = "Lista todos os documentos seguros pertencentes ao usuÃ¡rio autenticado",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/my")
    public ResponseEntity<ResponseDto> getMyDocuments(HttpServletRequest request) {
        List<SecureDocumentDto> docs = secureDocumentService.getMyDocuments(request);
        return ResponseEntity.ok(ResponseDto.builder()
                .status(HttpStatus.OK)
                .success(true)
                .data(docs)
                .build());
    }

    @Operation(summary = "Obter metadados do documento", description = "Retorna metadados do documento seguro",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/{documentId}")
    public ResponseEntity<ResponseDto> getDocumentById(@PathVariable Long documentId) {
        return secureDocumentService.getDocumentById(documentId)
                .map(doc -> ResponseEntity.ok(ResponseDto.builder()
                        .status(HttpStatus.OK)
                        .success(true)
                        .data(doc)
                        .build()))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ResponseDto.builder()
                                .status(HttpStatus.NOT_FOUND)
                                .success(false)
                                .error("Documento seguro não encontrado")
                                .build()));
    }

    @Operation(summary = "Excluir documento seguro", description = "Exclui logicamente o documento seguro",
            security = @SecurityRequirement(name = "bearerAuth"))
    @DeleteMapping("/{documentId}")
    public ResponseEntity<ResponseDto> deleteSecureDocument(
            @PathVariable Long documentId,
            HttpServletRequest request) {
        secureDocumentService.deleteSecureDocument(documentId, request);
        return ResponseEntity.ok(ResponseDto.builder()
                .status(HttpStatus.OK)
                .success(true)
                .data(null)
                .build());
    }

    @Operation(summary = "Logs de acesso do documento", description = "Retorna a trilha forense imutável de acessos ao documento",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/{documentId}/logs")
    public ResponseEntity<ResponseDto> getAccessLogs(
            @PathVariable Long documentId,
            HttpServletRequest request) {
        List<DocumentAccessLogDto> logs = secureDocumentService.getAccessLogs(documentId, request);
        return ResponseEntity.ok(ResponseDto.builder()
                .status(HttpStatus.OK)
                .success(true)
                .data(logs)
                .build());
    }
}
