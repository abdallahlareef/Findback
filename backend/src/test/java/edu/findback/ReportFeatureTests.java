package edu.findback;

import edu.findback.discovery.DiscoveryService;
import edu.findback.report.*;
import edu.findback.user.User;
import edu.findback.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReportFeatureTests {
    private ReportRepository reports;
    private UserRepository users;
    private ReportService reportService;

    @BeforeEach
    void setup() {
        reports = mock(ReportRepository.class);
        users = mock(UserRepository.class);
        reportService = new ReportService(reports, users);
    }

    @Test
    void authenticatedUserCanCreateLostAndFoundReports() {
        User user = user("owner@example.test");
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(reports.save(any(Report.class))).thenAnswer(call -> call.getArgument(0));
        for (ReportType type : ReportType.values()) {
            CreateReportRequest request = new CreateReportRequest(type, "Electronics", "JBL", "Buds",
                    "Black", "Library", LocalDate.of(2026, 11, 11), null, null, "small case");
            ReportResponse created = reportService.create(user.getEmail(), request);
            assertEquals(type, created.type());
            assertEquals(user.getId(), created.authorId());
        }
        verify(reports, times(2)).save(any(Report.class));
    }

    @Test
    void discoveryRequiresOwnedLostReportAndRanksFoundCandidates() {
        User owner = user("owner@example.test");
        Report lost = report(1L, owner, ReportType.LOST, "Electronics", "JBL", "Buds", "Black", "Library");
        Report close = report(2L, user("finder@example.test"), ReportType.FOUND, "Electronics", "JBL", "Buds", "Black", "Library");
        Report weak = report(3L, user("other@example.test"), ReportType.FOUND, "Books", null, null, "Blue", "Cafeteria");
        when(reports.findById(1L)).thenReturn(Optional.of(lost));
        when(reports.findByTypeAndStatus(ReportType.FOUND, ReportStatus.ACTIVE)).thenReturn(List.of(weak, close));
        DiscoveryService discovery = new DiscoveryService(reports);
        var results = discovery.findCandidates(1L, owner.getEmail());
        assertEquals(List.of(2L, 3L), results.stream().map(r -> r.reportId()).toList());
        assertTrue(results.get(0).score() > results.get(1).score());
        assertFalse(results.get(0).toString().contains("finder@example.test"));

        assertThrows(AccessDeniedException.class, () -> discovery.findCandidates(1L, "intruder@example.test"));
        when(reports.findById(3L)).thenReturn(Optional.of(weak));
        assertThrows(IllegalArgumentException.class, () -> discovery.findCandidates(3L, owner.getEmail()));
    }

    @Test
    void searchMapsToSafeSummaryAndRequestsStableDatabaseSort() {
        Report row = report(8L, user("private@example.test"), ReportType.FOUND,
                "Electronics", "JBL", "Buds", "Black", "Library");
        when(reports.findAll(any(org.springframework.data.jpa.domain.Specification.class), (Sort) any(Sort.class)))
                .thenReturn(List.of(row));
        var results = reportService.search(ReportType.FOUND, " electronics ", " library ",
                LocalDate.of(2026, 11, 11), ReportStatus.ACTIVE);
        assertEquals(1, results.size());
        String jsonShape = results.get(0).toString();
        assertFalse(jsonShape.contains("author"));
        assertFalse(jsonShape.contains("private@example.test"));
        ArgumentCaptor<Sort> sortCaptor = ArgumentCaptor.forClass(Sort.class);
        verify(reports).findAll(any(org.springframework.data.jpa.domain.Specification.class), (Sort) sortCaptor.capture());
        assertTrue(sortCaptor.getValue().getOrderFor("createdAt").isDescending());
        assertTrue(sortCaptor.getValue().getOrderFor("id").isDescending());
    }

    private User user(String email) {
        User user = new User();
        user.setId(email.startsWith("owner") ? 11L : 12L);
        user.setEmail(email);
        user.setName("Test User");
        return user;
    }

    private Report report(Long id, User author, ReportType type, String category,
            String brand, String model, String colour, String zone) {
        Report report = new Report() {
            @Override public Long getId() { return id; }
        };
        report.setAuthor(author);
        report.setType(type);
        report.setCategory(category);
        report.setBrand(brand);
        report.setModel(model);
        report.setColour(colour);
        report.setCampusZone(zone);
        report.setEventDate(LocalDate.of(2026, 11, 11));
        report.setStatus(ReportStatus.ACTIVE);
        report.setSafeDescription("portable item");
        return report;
    }
}
