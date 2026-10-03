package org.crm.student.application_management_service.service;

import org.crm.student.application_management_service.email.EmailNotificationRequest;
import org.crm.student.application_management_service.email.SmsNotificationRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class NotificationService {

    /**
     * Base URL of the Notification service. Defaults to localhost for a local
     * run; override with the compose service name or the Kubernetes Service name
     * so the call does not resolve back to this container.
     */
    @Value("${clients.notification-service.url:http://localhost:8085}")
    private String notificationServiceUrl;

    @Autowired
    private RestTemplate restTemplate;

    public void sendEmailNotification(String email, String subject, String candidateName) {
        EmailNotificationRequest emailRequest = new EmailNotificationRequest();
        emailRequest.setTo(email);
        emailRequest.setSubject(subject);

        String url = UriComponentsBuilder
                .fromHttpUrl(notificationServiceUrl + "/api/notifications/email")
                .queryParam("candidateName", candidateName)
                .build()
                .toUriString();

        restTemplate.postForObject(url, emailRequest, String.class);
    }

    public void sendSmsNotification(String phoneNumber, String message) {
        SmsNotificationRequest smsRequest = new SmsNotificationRequest();
        smsRequest.setPhoneNumber(phoneNumber);
        smsRequest.setMessage(message);

        restTemplate.postForObject(notificationServiceUrl + "/api/notifications/sms", smsRequest, String.class);
    }
}
