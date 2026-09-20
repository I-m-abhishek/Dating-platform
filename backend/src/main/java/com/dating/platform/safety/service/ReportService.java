package com.dating.platform.safety.service;

import com.dating.platform.common.exception.BusinessException;
import com.dating.platform.common.exception.ErrorCode;
import com.dating.platform.safety.dto.CreateReportRequest;
import com.dating.platform.safety.entity.Report;
import com.dating.platform.safety.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * User reports.
 *
 * <p>Reports are never rejected as duplicates once the first is resolved - a repeat offence
 * deserves a new report. Only a second report against the same person while one is still
 * open is collapsed, so moderators see one thread per incident.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private static final int AUTO_REVIEW_THRESHOLD = 3;

    private final ReportRepository reportRepository;
    private final BlockService blockService;

    @Transactional
    public UUID report(UUID reporterId, CreateReportRequest request) {
        if (reporterId.equals(request.reportedUserId())) {
            throw new BusinessException(ErrorCode.SELF_INTERACTION, "You cannot report yourself");
        }

        boolean alreadyOpen = reportRepository.existsByReporterIdAndReportedUserIdAndStatus(
                reporterId, request.reportedUserId(), Report.ReportStatus.OPEN);

        Report report = alreadyOpen ? null : reportRepository.save(Report.builder()
                .reporterId(reporterId)
                .reportedUserId(request.reportedUserId())
                .reason(request.reason())
                .details(request.details())
                .contextType(request.contextType())
                .contextId(request.contextId())
                .status(Report.ReportStatus.OPEN)
                .build());

        if (request.alsoBlock()) {
            blockService.block(reporterId, request.reportedUserId(), request.reason().name());
        }

        long openReports = reportRepository.countByReportedUserIdAndStatus(
                request.reportedUserId(), Report.ReportStatus.OPEN);
        if (openReports >= AUTO_REVIEW_THRESHOLD) {
            // Escalation hook: a real deployment pages the trust and safety queue here.
            log.warn("User {} has {} open reports - escalate for review",
                    request.reportedUserId(), openReports);
        }

        return report == null ? null : report.getId();
    }
}
