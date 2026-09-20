package com.dating.platform.safety.dto;

import com.dating.platform.safety.entity.Report;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(name = "CreateReportRequest")
public record CreateReportRequest(

        @NotNull(message = "Who are you reporting?")
        UUID reportedUserId,

        @NotNull(message = "Pick a reason")
        Report.ReportReason reason,

        @Size(max = 1000, message = "Keep the details under 1000 characters")
        String details,

        /** Optional pointer to what triggered the report: MESSAGE, PHOTO, COMMENT. */
        @Size(max = 30) String contextType,

        UUID contextId,

        /** Reporting usually means you also never want to see them again. */
        boolean alsoBlock
) {
}
