package org.crm.student.application_management_service.repository;

import org.crm.student.application_management_service.model.EmploymentDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EmploymentDetailsRepository extends JpaRepository<EmploymentDetails, Integer> {
}
