package org.crm.student.task_management_service.Service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class CandidateClient {

    private final RestTemplate restTemplate;

    /**
     * Base URL of the student service. Defaults to localhost for a local run;
     * override with the compose service name or the Kubernetes Service name so
     * the call does not resolve back to this container.
     */
    @Value("${clients.student-service.url:http://localhost:8082}")
    private String studentServiceUrl;

    public CandidateClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public boolean validateCandidate(String candidateFullname) {
        String url = studentServiceUrl + "/api/candidates/" + candidateFullname + "/exists";
        try {
            return restTemplate.getForObject(url, Boolean.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to validate candidateId: " + candidateFullname, e);
        }
    }
}
