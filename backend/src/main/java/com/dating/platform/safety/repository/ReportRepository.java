package com.dating.platform.safety.repository;

import com.dating.platform.safety.entity.Report;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ReportRepository extends JpaRepository<Report, UUID> {

    Page<Report> findAllByStatus(Report.ReportStatus status, Pageable pageable);

    long countByReportedUserIdAndStatus(UUID reportedUserId, Report.ReportStatus status);

    boolean existsByReporterIdAndReportedUserIdAndStatus(UUID reporterId, UUID reportedUserId,
                                                         Report.ReportStatus status);
}
