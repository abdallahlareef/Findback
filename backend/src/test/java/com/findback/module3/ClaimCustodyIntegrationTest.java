package com.findback.module3;

import com.findback.module3.domain.ClaimStatus;
import com.findback.module3.domain.CustodyStatus;
import com.findback.module3.domain.ItemCustodyState;
import com.findback.module3.repository.ClaimRepository;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end checks of Member 3 rules through the real HTTP, security and persistence layers. */
@SpringBootTest(classes = edu.findback.BackendApplication.class)
@AutoConfigureMockMvc
class ClaimCustodyIntegrationTest {
    private static final String DATA = "{\"serialSuffix\":\"a82f\",\"privateMark\":\"PRIVATE-MARK-TEST\",\"accessoryDescription\":\"PRIVATE-ACCESSORY-TEST\"}";

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired ReportRepository reports;
    @Autowired ClaimRepository claims;
    @Autowired ItemCustodyStateRepository custody;
    @Autowired JdbcTemplate jdbc;

    User owner, rival, finder, staff, otherStaff;
    Long lost, rivalLost, found;

    @BeforeEach
    void setup() throws Exception {
        for (String table : new String[]{"m2_verification_attempts", "m2_supplement_requests", "m2_evidence_versions",
                "m2_evidence_pairs", "admin_review", "custody_event", "item_custody_state", "claim", "reports", "app_users"}) {
            jdbc.execute("DELETE FROM " + table);
        }
        owner = user("owner@claims.test", Role.USER);
        rival = user("rival@claims.test", Role.USER);
        finder = user("finder@claims.test", Role.USER);
        staff = user("staff@claims.test", Role.ADMIN);
        otherStaff = user("reviewer@claims.test", Role.ADMIN);
        lost = report(owner, ReportType.LOST);
        rivalLost = report(rival, ReportType.LOST);
        found = report(finder, ReportType.FOUND);
        baseline(lost, owner);
        baseline(rivalLost, rival);
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

    ResultActions submit(User claimant, Long lostReportId) throws Exception {
        return perform(json(post("/api/v1/claims"), "{\"lostReportId\":" + lostReportId + ",\"foundReportId\":" + found + "}"), claimant);
    }

    Long createClaim(User claimant, Long lostReportId) throws Exception {
        String body = submit(claimant, lostReportId).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.claimId")).longValue();
    }

    ResultActions intake(User actor) throws Exception {
        return perform(json(post("/api/v1/claims/staff/custody/" + found + "/intake"),
                "{\"storageReference\":\"SEC-LOCKER-B12\",\"conditionNotes\":\"Matte black case\"}"), actor);
    }

    ResultActions decide(User actor, Long claimId, String action) throws Exception {
        return perform(json(post("/api/v1/claims/staff/claims/" + claimId + "/decision"),
                "{\"action\":\"" + action + "\",\"reason\":\"Recorded test decision\",\"physicalCheckNotes\":\"\"}"), actor);
    }

    @Test
    void reservedItemCannotBeReIntakenOrReservedTwice_T10() throws Exception {
        Long first = createClaim(owner, lost);
        Long second = createClaim(rival, rivalLost);
        intake(staff).andExpect(status().isOk());
        decide(staff, first, "APPROVE").andExpect(status().isOk());

        intake(staff).andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(containsString("reserved")));
        decide(staff, second, "APPROVE").andExpect(status().isConflict());

        ItemCustodyState state = custody.findById(found).orElseThrow();
        assertEquals(CustodyStatus.RESERVED, state.getCustodyStatus());
        assertEquals(first, state.getReservedByClaimId());
        assertEquals(ClaimStatus.UNDER_REVIEW, claims.findById(second).orElseThrow().getStatus());
    }

    @Test
    void heldApprovalBlocksItemUntilDisputeIsResolved() throws Exception {
        Long id = createClaim(owner, lost);
        intake(staff).andExpect(status().isOk());
        decide(staff, id, "APPROVE").andExpect(status().isOk());
        decide(staff, id, "HOLD").andExpect(status().isOk());
        assertTrue(custody.findById(found).orElseThrow().isDisputeBlocked());

        decide(staff, id, "RECONSIDER").andExpect(status().isOk());
        decide(staff, id, "APPROVE").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("dispute")));

        String resolution = "{\"resolution\":\"Ownership re-confirmed with student ID at the desk\"}";
        String path = "/api/v1/claims/staff/custody/" + found + "/dispute/resolve";
        perform(json(post(path), resolution), owner).andExpect(status().isForbidden());
        perform(json(post(path), "{\"resolution\":\"\"}"), staff).andExpect(status().isBadRequest());
        perform(json(post(path), resolution), staff).andExpect(status().isOk())
                .andExpect(jsonPath("$.disputeBlocked").value(false));
        perform(json(post(path), resolution), staff).andExpect(status().isConflict());

        decide(staff, id, "APPROVE").andExpect(status().isOk());
        assertEquals(id, custody.findById(found).orElseThrow().getReservedByClaimId());
    }

    ResultActions handover(User actor, Long claimId, String notes) throws Exception {
        return perform(json(post("/api/v1/claims/staff/claims/" + claimId + "/handover"),
                "{\"handoverCode\":\"ONE-TIME-123\",\"identityCheckNotes\":\"" + notes + "\"}"), actor);
    }

    @Test
    void handoverCompletesTheLifecycleAndClosesEverythingElse() throws Exception {
        Long first = createClaim(owner, lost);
        Long second = createClaim(rival, rivalLost);
        intake(staff).andExpect(status().isOk());
        decide(staff, first, "APPROVE").andExpect(status().isOk());

        handover(owner, first, "Student ID checked").andExpect(status().isForbidden());
        handover(staff, first, "").andExpect(status().isBadRequest());
        handover(staff, second, "Student ID checked").andExpect(status().isConflict());

        handover(staff, first, "Student ID 2012 checked against account").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        assertEquals(CustodyStatus.RETURNED, custody.findById(found).orElseThrow().getCustodyStatus());
        assertEquals(ClaimStatus.REJECTED, claims.findById(second).orElseThrow().getStatus());
        assertEquals(edu.findback.report.ReportStatus.CLOSED, reports.findById(found).orElseThrow().getStatus());
        assertEquals(edu.findback.report.ReportStatus.CLOSED, reports.findById(lost).orElseThrow().getStatus());
        assertEquals(edu.findback.report.ReportStatus.ACTIVE, reports.findById(rivalLost).orElseThrow().getStatus());

        handover(staff, first, "Again").andExpect(status().isConflict());
        intake(staff).andExpect(status().isConflict());
        perform(get("/api/v1/claims/mine"), owner)
                .andExpect(jsonPath("$[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$[0].withdrawable").value(false));
        perform(get("/api/v1/claims/staff/cases"), otherStaff).andExpect(jsonPath("$.length()").value(0));
        perform(get("/api/reports/" + rivalLost + "/candidates"), rival).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void blockedApprovalExplainsWhy() throws Exception {
        Long id = createClaim(owner, lost);
        decide(staff, id, "APPROVE").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("custody")));
    }

    @Test
    void withdrawalIsLimitedToOwnPendingClaims_FR18() throws Exception {
        Long id = createClaim(owner, lost);
        perform(delete("/api/v1/claims/" + id), rival).andExpect(status().isForbidden());
        perform(delete("/api/v1/claims/" + id), owner).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"))
                .andExpect(jsonPath("$.withdrawable").value(false));
        perform(delete("/api/v1/claims/" + id), owner).andExpect(status().isConflict());

        decide(staff, id, "HOLD").andExpect(status().isConflict());
        assertEquals(ClaimStatus.WITHDRAWN, claims.findById(id).orElseThrow().getStatus());
    }

    @Test
    void approvedClaimCannotBeWithdrawn_FR18() throws Exception {
        Long id = createClaim(owner, lost);
        intake(staff).andExpect(status().isOk());
        decide(staff, id, "APPROVE").andExpect(status().isOk());

        perform(delete("/api/v1/claims/" + id), owner).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("awaiting a staff decision")));
        assertEquals(ClaimStatus.APPROVED, claims.findById(id).orElseThrow().getStatus());
    }

    @Test
    void claimantSeesOnlyOwnClaimsWithoutInternalFields() throws Exception {
        Long mine = createClaim(owner, lost);
        createClaim(rival, rivalLost);

        String body = perform(get("/api/v1/claims/mine"), owner).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].claimId").value(mine))
                .andExpect(jsonPath("$[0].withdrawable").value(true))
                .andReturn().getResponse().getContentAsString();
        assertFalse(body.contains("riskTier"));
        assertFalse(body.contains("flagReason"));
        assertFalse(body.contains("strength"));
    }

    @Test
    void staffQueueIsLiveSafeAndHidesCasesTheReviewerIsInvolvedIn_T09() throws Exception {
        Long ownerClaim = createClaim(owner, lost);
        Long staffLost = report(staff, ReportType.LOST);
        baseline(staffLost, staff);
        createClaim(staff, staffLost);

        perform(get("/api/v1/claims/staff/cases"), owner).andExpect(status().isForbidden());

        String body = perform(get("/api/v1/claims/staff/cases"), otherStaff).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].claimId").value(ownerClaim))
                .andExpect(jsonPath("$[0].claimantName").value("owner@claims.test"))
                .andExpect(jsonPath("$[0].custodyStatus").value("REPORTED"))
                .andExpect(jsonPath("$[0].doveSummary.strengthLabel").value("WEAK"))
                .andExpect(jsonPath("$[0].competingActiveClaimsCount").value(1))
                .andReturn().getResponse().getContentAsString();
        assertFalse(body.contains("PRIVATE-"));
        assertFalse(body.contains("A82F"));

        perform(get("/api/v1/claims/staff/cases"), staff).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void involvedStaffCannotEscalateDecideOrIntake_T09() throws Exception {
        Long ownerClaim = createClaim(owner, lost);
        Long staffLost = report(staff, ReportType.LOST);
        baseline(staffLost, staff);
        createClaim(staff, staffLost);
        String escalation = "{\"riskTier\":\"HIGH\",\"reason\":\"Competing claim on the same item\"}";

        perform(json(patch("/api/v1/claims/staff/claims/" + ownerClaim + "/risk"), escalation), staff).andExpect(status().isForbidden());
        decide(staff, ownerClaim, "REJECT").andExpect(status().isForbidden());
        intake(staff).andExpect(status().isForbidden());

        perform(json(patch("/api/v1/claims/staff/claims/" + ownerClaim + "/risk"), escalation), otherStaff)
                .andExpect(status().isOk()).andExpect(jsonPath("$.riskTier").value("HIGH"));
    }

    @Test
    void doubleSubmittedClaimCreatesOnlyOneActiveClaim_FR06_T10() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            Callable<Integer> task = () -> {
                start.await();
                return submit(owner, lost).andReturn().getResponse().getStatus();
            };
            var first = executor.submit(task);
            var second = executor.submit(task);
            start.countDown();
            List<Integer> statuses = new ArrayList<>(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)));
            Collections.sort(statuses);
            assertEquals(List.of(200, 409), statuses);
            assertEquals(1, claims.findByClaimantIdAndFoundReportId(owner.getId(), found).size());
        } finally {
            executor.shutdownNow();
        }
    }
}
