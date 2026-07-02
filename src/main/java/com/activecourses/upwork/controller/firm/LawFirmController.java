package com.activecourses.upwork.controller.firm;

import com.activecourses.upwork.dto.ResponseDto;
import com.activecourses.upwork.model.LawFirm;
import com.activecourses.upwork.service.authentication.AuthService;
import com.activecourses.upwork.service.firm.LawFirmService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Law Firm", description = "Law Firm Management API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/firms/")
public class LawFirmController {

    private final LawFirmService lawFirmService;
    private final AuthService authService;

    @Operation(summary = "Create a new law firm")
    @PostMapping
    public ResponseEntity<ResponseDto> createFirm(@RequestBody LawFirm lawFirm) {
        LawFirm created = lawFirmService.createFirm(lawFirm);
        return buildResponse(HttpStatus.CREATED, true, created, null);
    }

    @Operation(summary = "Get all law firms")
    @GetMapping
    public ResponseEntity<ResponseDto> getAllFirms() {
        return buildResponse(HttpStatus.OK, true, lawFirmService.getAllFirms(), null);
    }

    @Operation(summary = "Get law firm by ID")
    @GetMapping("/{id}")
    public ResponseEntity<ResponseDto> getFirmById(@PathVariable int id) {
        return lawFirmService.getFirmById(id)
                .map(firm -> buildResponse(HttpStatus.OK, true, firm, null))
                .orElse(buildResponse(HttpStatus.NOT_FOUND, false, null, "Law Firm not found"));
    }

    @Operation(summary = "Update law firm")
    @PutMapping("/{id}")
    public ResponseEntity<ResponseDto> updateFirm(@PathVariable int id, @RequestBody LawFirm lawFirm) {
        LawFirm updated = lawFirmService.updateFirm(id, lawFirm);
        return buildResponse(HttpStatus.OK, true, updated, null);
    }

    @Operation(summary = "Delete law firm")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ResponseDto> deleteFirm(@PathVariable int id) {
        boolean deleted = lawFirmService.deleteFirm(id);
        return deleted
                ? buildResponse(HttpStatus.OK, true, "Law Firm deleted", null)
                : buildResponse(HttpStatus.NOT_FOUND, false, null, "Law Firm not found");
    }

    @Operation(summary = "Add lawyer to firm")
    @PostMapping("/{firmId}/lawyers")
    public ResponseEntity<ResponseDto> addLawyerToFirm(
            @PathVariable int firmId, @RequestBody Map<String, Object> body) {
        int lawyerId = (int) body.get("lawyerId");
        boolean isPartner = body.containsKey("isPartner") && (boolean) body.get("isPartner");
        LawFirm firm = lawFirmService.addLawyerToFirm(firmId, lawyerId, isPartner);
        return buildResponse(HttpStatus.OK, true, firm, null);
    }

    @Operation(summary = "Remove lawyer from firm")
    @DeleteMapping("/{firmId}/lawyers/{lawyerId}")
    public ResponseEntity<ResponseDto> removeLawyerFromFirm(
            @PathVariable int firmId, @PathVariable int lawyerId) {
        boolean removed = lawFirmService.removeLawyerFromFirm(firmId, lawyerId);
        return removed
                ? buildResponse(HttpStatus.OK, true, "Lawyer removed from firm", null)
                : buildResponse(HttpStatus.NOT_FOUND, false, null, "Association not found");
    }

    @Operation(summary = "Get my law firms")
    @GetMapping("/my")
    public ResponseEntity<ResponseDto> getMyFirms() {
        Integer userId = authService.getCurrentUserId();
        if (userId == null) {
            return buildResponse(HttpStatus.UNAUTHORIZED, false, null, "Not authenticated");
        }
        return buildResponse(HttpStatus.OK, true, lawFirmService.getFirmsByLawyer(userId), null);
    }

    private ResponseEntity<ResponseDto> buildResponse(HttpStatus status, boolean success, Object data, Object error) {
        return ResponseEntity.status(status)
                .body(ResponseDto.builder().status(status).success(success).data(data).error(error).build());
    }
}
