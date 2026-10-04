package org.crm.student.application_management_service.controller;

import org.crm.student.application_management_service.model.Candidate;
import org.crm.student.application_management_service.service.CandidateService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class PipelineController {

    private final CandidateService candidateService;

    public PipelineController(CandidateService candidateService) {
        this.candidateService = candidateService;
    }

    @GetMapping("/pipeline")
    public ResponseEntity<List<Candidate>> getPipelineCandidates() {
        return ResponseEntity.ok(candidateService.getAllCandidates());
    }
}
