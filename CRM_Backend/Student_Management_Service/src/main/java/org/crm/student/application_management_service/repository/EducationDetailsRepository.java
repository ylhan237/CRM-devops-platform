package org.crm.student.application_management_service.repository;

import org.crm.student.application_management_service.model.EducationDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EducationDetailsRepository extends JpaRepository<EducationDetails, Integer> {
}
