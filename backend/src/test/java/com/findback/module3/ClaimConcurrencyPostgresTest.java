package com.findback.module3;

import com.findback.module3.domain.ClaimStatus;
import com.findback.module3.domain.CustodyStatus;
import com.findback.module3.domain.ItemCustodyState;
import com.findback.module3.repository.ClaimRepository;
import com.findback.module3.repository.CustodyEventRepository;
import com.findback.module3.repository.ItemCustodyStateRepository;
import com.jayway.jsonpath.JsonPath;
import edu.findback.report.Report;
import edu.findback.report.ReportRepository;
import edu.findback.report.ReportType;
import edu.findback.user.Role;
import edu.findback.user.User;
import edu.findback.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T10 concurrency checks against real PostgreSQL row locks ({@code SELECT ... FOR UPDATE}) and
 * optimistic versioning, which H2 only approximates.
 *
 * <p>Opt-in, and only against a dedicated test database (the tests delete every row):
 * <pre>
 * docker exec findback-postgres createdb -U findback findback_test
 * $env:FINDBACK_PG_TEST_URL = "jdbc:postgresql://localhost:5432/findback_test"
 * .\mvnw.cmd test "-Dtest=ClaimConcurrencyPostgresTest"
 * </pre>
 * Optional: FINDBACK_PG_TEST_USER / FINDBACK_PG_TEST_PASSWORD (default findback / findback_dev).
 */
@SpringBootTest(classes = edu.findback.BackendApplication.class)
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "FINDBACK_PG_TEST_URL", matches = ".+")
class ClaimConcurrencyPostgresTest {
    private static final String DATA = "{\"serialSuffix\":\"a82f\",\"privateMark\":\"PRIVATE-MARK-TEST\",\"accessoryDescription\":\"PRIVATE-ACCESSORY-TEST\"}";
    private static final int THREADS = 6;

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> requireTestDatabase(System.getenv("FINDBACK_PG_TEST_URL")));
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.datasource.username", () -> envOr("FINDBACK_PG_TEST_USER", "findback"));
        registry.add("spring.datasource.password", () -> envOr("FINDBACK_PG_TEST_PASSWORD", "findback_dev"));
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> String.valueOf(THREADS + 4));
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    /** Refuses to run against anything that is not clearly a throwaway test database. */
    static String requireTestDatabase(String url) {
        String database = url.replaceFirst("^jdbc:postgresql://[^/]+/", "").replaceFirst("[?;].*$", "");
        if (!url.startsWith("jdbc:postgresql://") || !database.toLowerCase().contains("test")) {
            throw new IllegalStateException("FINDBACK_PG_TEST_URL must point to a dedicated PostgreSQL test database "
                    + "whose name contains 'test' (got '" + database + "'). These tests delete all rows.");
        }
        return url;
    }

    static String envOr(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired ReportRepository reports;
    @Autowired ClaimRepository claims;
    @Autowired ItemCustodyStateRepository custody;
    @Autowired CustodyEventRepository custodyEvents;
    @Autowired JdbcTemplate jdbc;

    User finder, staff, otherStaff;
    Long found;

    @BeforeEach
    void setup() throws Exception {
        for (String table : new String[]{"m2_verification_attempts", "m2_supplement_requests", "m2_evidence_versions",
                "m2_evidence_pairs", "admin_review", "custody_event", "item_custody_state", "claim", "reports", "app_users"}) {
            jdbc.execute("DELETE FROM " + table);
        }
        finder = user("finder@pg.test", Role.USER);
        staff = user("staff@pg.test", Role.ADMIN);
        otherStaff = user("staff2@pg.test", Role.ADMIN);
        found = report(finder, ReportType.FOUND);
        baseline(found, finder);
    }

    User user(String email, Role role) {
        User u = new User();
        u.setName(email);
        u.setEmail(email);
        u.setRole(role);
        u.setPasswordHash("{noop}testpassword");
        return users.save(u);
    }

    Long report(User author, ReportType type) {
        Report r = new Report();
        r.setAuthor(author);
        r.setType(type);
        r.setCategory("EARBUDS");
        r.setColour("Black");
        r.setCampusZone("Library");
        r.setEventDate(LocalDate.now());
        return reports.save(r).getId();
    }

    ResultActions perform(MockHttpServletRequestBuilder request, User actor) throws Exception {
        return mvc.perform(request
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                        .user(actor.getEmail()).roles(actor.getRole().name()))
                .with(csrf()));
    }

    static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType("application/json").content(body);
    }

    void baseline(Long reportId, User author) throws Exception {
        perform(json(post("/api/evidence/reports/" + reportId + "/baseline"), DATA), author).andExpect(status().isOk());
    }

    /** A claimant with their own lost report and saved baseline, ready to claim {@link #found}. */
    record Claimant(User user, Long lostReportId) {}

    Claimant claimant(String email) throws Exception {
        User user = user(email, Role.USER);
        Long lost = report(user, ReportType.LOST);
        baseline(lost, user);
        return new Claimant(user, lost);
    }

    MockHttpServletRequestBuilder claimRequest(Claimant c) {
        return json(post("/api/v1/claims"), "{\"lostReportId\":" + c.lostReportId() + ",\"foundReportId\":" + found + "}");
    }

    Long createClaim(Claimant c) throws Exception {
        String body = perform(claimRequest(c), c.user()).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.claimId")).longValue();
    }

    MockHttpServletRequestBuilder intakeRequest() {
        return json(post("/api/v1/claims/staff/custody/" + found + "/intake"),
                "{\"storageReference\":\"SEC-LOCKER-B12\",\"conditionNotes\":\"Matte black case\"}");
    }

    MockHttpServletRequestBuilder approveRequest(Long claimId) {
        return json(post("/api/v1/claims/staff/claims/" + claimId + "/decision"),
                "{\"action\":\"APPROVE\",\"reason\":\"Physical check complete\",\"physicalCheckNotes\":\"\"}");
    }

    /** Starts all tasks at the same instant and returns their HTTP statuses, sorted. */
    static List<Integer> race(List<Callable<Integer>> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (Callable<Integer> task : tasks) {
                futures.add(executor.submit(() -> { start.await(); return task.call(); }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) statuses.add(future.get(30, TimeUnit.SECONDS));
            Collections.sort(statuses);
            return statuses;
        } finally {
            executor.shutdownNow();
        }
    }

    Callable<Integer> call(MockHttpServletRequestBuilder request, User actor) {
        return () -> perform(request, actor).andReturn().getResponse().getStatus();
    }

    static List<Integer> expected(int ok, int conflicts) {
        List<Integer> statuses = new ArrayList<>(Collections.nCopies(ok, 200));
        statuses.addAll(Collections.nCopies(conflicts, 409));
        return statuses;
    }

    @Test
    void parallelDuplicateSubmissionsCreateExactlyOneClaim_FR06() throws Exception {
        Claimant owner = claimant("owner@pg.test");
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) tasks.add(call(claimRequest(owner), owner.user()));

        assertEquals(expected(1, THREADS - 1), race(tasks));
        assertEquals(1, claims.findByClaimantIdAndFoundReportId(owner.user().getId(), found).size());
    }

    @Test
    void parallelApprovalsReserveTheItemForExactlyOneClaim() throws Exception {
        List<Long> claimIds = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) claimIds.add(createClaim(claimant("claimant" + i + "@pg.test")));
        perform(intakeRequest(), staff).andExpect(status().isOk());

        List<Callable<Integer>> tasks = new ArrayList<>();
        for (Long id : claimIds) tasks.add(call(approveRequest(id), staff));

        assertEquals(expected(1, THREADS - 1), race(tasks));
        List<Long> approved = claimIds.stream()
                .filter(id -> claims.findById(id).orElseThrow().getStatus() == ClaimStatus.APPROVED).toList();
        assertEquals(1, approved.size());
        ItemCustodyState state = custody.findById(found).orElseThrow();
        assertEquals(CustodyStatus.RESERVED, state.getCustodyStatus());
        assertEquals(approved.get(0), state.getReservedByClaimId());
    }

    @Test
    void withdrawalRacingApprovalLeavesAConsistentState_FR18() throws Exception {
        for (int round = 0; round < 5; round++) {
            setup();
            Claimant owner = claimant("owner" + round + "@pg.test");
            Long id = createClaim(owner);
            perform(intakeRequest(), staff).andExpect(status().isOk());

            List<Integer> statuses = race(List.of(
                    call(approveRequest(id), staff),
                    call(delete("/api/v1/claims/" + id), owner.user())));

            assertEquals(expected(1, 1), statuses, "exactly one of approve/withdraw may win");
            ClaimStatus finalStatus = claims.findById(id).orElseThrow().getStatus();
            ItemCustodyState state = custody.findById(found).orElseThrow();
            if (finalStatus == ClaimStatus.APPROVED) {
                assertEquals(CustodyStatus.RESERVED, state.getCustodyStatus());
                assertEquals(id, state.getReservedByClaimId());
            } else {
                assertEquals(ClaimStatus.WITHDRAWN, finalStatus);
                assertEquals(CustodyStatus.IN_CUSTODY, state.getCustodyStatus());
                assertNull(state.getReservedByClaimId());
            }
        }
    }

    @Test
    void parallelFirstIntakesCreateOneCustodyRecord_FR11() throws Exception {
        List<Integer> statuses = race(List.of(call(intakeRequest(), staff), call(intakeRequest(), otherStaff)));

        assertEquals(expected(2, 0), statuses, "intakes must be serialized, not fail on a duplicate key");
        assertEquals(1, custody.count());
        assertEquals(CustodyStatus.IN_CUSTODY, custody.findById(found).orElseThrow().getCustodyStatus());
        long received = custodyEvents.findAll().stream().filter(e -> e.getEventType().equals("RECEIVED_INTO_CUSTODY")).count();
        assertEquals(1, received);
        assertEquals(2, custodyEvents.count());
    }
}
