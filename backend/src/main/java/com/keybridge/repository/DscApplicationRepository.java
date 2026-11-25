package com.keybridge.repository;

import com.keybridge.entity.DscApplication;
import com.keybridge.enums.DscApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DscApplicationRepository extends JpaRepository<DscApplication, Long> {
    List<DscApplication> findByApplicantIdOrderByCreatedAtDesc(Long applicantId);
    List<DscApplication> findAllByOrderByCreatedAtDesc();
    List<DscApplication> findByStatus(DscApplicationStatus status);
    List<DscApplication> findByRenewalOfIdIn(List<Long> renewalOfIds);
}
