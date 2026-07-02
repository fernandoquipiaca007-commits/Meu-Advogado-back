package com.activecourses.upwork.controller.skill;

import com.activecourses.upwork.dto.ResponseDto;
import com.activecourses.upwork.repository.skill.SpecialtyRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Specialty", description = "Legal Specialties API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/specialties/")
public class SpecialtyController {

    private final SpecialtyRepository specialtyRepository;

    @Operation(summary = "Get all legal specialties")
    @GetMapping
    public ResponseEntity<ResponseDto> getAllSpecialties() {
        var specialties = specialtyRepository.findAll();
        return ResponseEntity.ok()
                .body(ResponseDto.builder()
                        .status(HttpStatus.OK)
                        .success(true)
                        .data(specialties)
                        .build());
    }
}
