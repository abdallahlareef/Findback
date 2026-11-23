package com.findback.module3.config;

import com.findback.module3.ports.CrossModulePorts.ReportLifecyclePort;
import edu.findback.report.ReportRepository;
import edu.findback.report.ReportStatus;
import org.springframework.stereotype.Component;

/** Closes shared reports after a completed handover so they leave search and candidate discovery. */
@Component
public class ReportLifecycleAdapter implements ReportLifecyclePort {
    private final ReportRepository reports;

    public ReportLifecycleAdapter(ReportRepository reports) {
        this.reports = reports;
    }

    @Override
    public void closeReport(Long reportId) {
        reports.findById(reportId).ifPresent(report -> {
            report.setStatus(ReportStatus.CLOSED);
            reports.save(report);
        });
    }
}
