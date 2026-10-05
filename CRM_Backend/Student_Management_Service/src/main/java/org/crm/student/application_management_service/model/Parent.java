package org.crm.student.application_management_service.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "parent_details")
@Data
public class Parent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String fullName;

    private String email;

    private String phoneNumber;

    private String relationshipToCandidate;
}

