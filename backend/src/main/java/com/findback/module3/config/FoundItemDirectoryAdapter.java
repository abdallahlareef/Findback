package com.findback.module3.config;

import com.findback.module3.ports.CrossModulePorts.FoundItemDirectoryPort;
import com.findback.module3.ports.CrossModulePorts.FoundItemDto;
import edu.findback.report.ReportRepository;
import edu.findback.report.ReportStatus;
import edu.findback.report.ReportType;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/** Lists found reports from the shared report store for the staff custody desk (FR-11). */
@Component
public class FoundItemDirectoryAdapter implements FoundItemDirectoryPort {
    private final ReportRepository reports;

    public FoundItemDirectoryAdapter(ReportRepository reports) {
        this.reports = reports;
    }

    @Override
    public List<FoundItemDto> listFoundItems() {
        return Arrays.stream(ReportStatus.values())
                .flatMap(status -> reports.findByTypeAndStatus(ReportType.FOUND, status).stream())
                .map(r -> new FoundItemDto(r.getId(), r.getAuthor().getId(), r.getCategory(), r.getColour(),
                        r.getCampusZone(), r.getEventDate(), r.getStatus().name(), r.getRiskLevel()))
                .toList();
    }
}
