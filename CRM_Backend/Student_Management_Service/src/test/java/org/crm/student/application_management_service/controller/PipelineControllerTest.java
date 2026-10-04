package org.crm.student.application_management_service.controller;

import org.crm.student.application_management_service.model.Candidate;
import org.crm.student.application_management_service.service.CandidateService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PipelineControllerTest {

    private final CandidateService candidateService = mock(CandidateService.class);
    private final PipelineController pipelineController = new PipelineController(candidateService);

    @Test
    void pipelineReturnsCandidatesAsJson() {
        List<Candidate> candidates = List.of(new Candidate());
        when(candidateService.getAllCandidates()).thenReturn(candidates);

        ResponseEntity<List<Candidate>> response = pipelineController.getPipelineCandidates();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(candidates, response.getBody());
        verify(candidateService).getAllCandidates();
    }
}
