package org.crm.student.application_management_service.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "employment_details")
@Data
public class EmploymentDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String companyName;

    private String responsibilities;
}

