package org.crm.student.application_management_service.service;

import org.crm.student.application_management_service.model.Candidate;
import org.crm.student.application_management_service.model.Status;
import org.crm.student.application_management_service.repository.CandidateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CandidateServiceCandidateIdTest {

    @Mock
    private CandidateRepository candidateRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private CandidateService candidateService;

    @Test
    void updateCandidateUsesUnknownFieldCodeWhenFieldIsShorterThanThreeCharacters() {
        Candidate candidate = new Candidate();
        candidate.setField("CS");
        candidate.setStatus(Status.STUDENT);
        candidate.setEmail("candidate@example.com");
        candidate.setPhoneNumber("1234567890");

        String prefix = LocalDate.now().getYear() + "UNK";
        when(candidateRepository.countByCandidateIdStartingWith(prefix)).thenReturn(5);

        candidateService.updateCandidate(candidate);

        assertEquals(prefix + "0006", candidate.getCandidateId());
        verify(candidateRepository).countByCandidateIdStartingWith(prefix);
        verify(candidateRepository).save(candidate);
    }
}
