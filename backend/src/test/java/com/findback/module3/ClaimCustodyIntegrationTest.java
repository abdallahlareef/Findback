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
import static org.junit.jupiter.api.Assertions.assertNull;
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

    /** LOW risk by default, so one automatic serial match satisfies FR-10 in tests about other rules. */
    Long report(User author, ReportType type) {
        return report(author, type, "Umbrella");
    }

    Long report(User author, ReportType type, String category) {
        Report r = new Report();
        r.setAuthor(author);
        r.setType(type);
        r.setCategory(category);
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

    /**
     * Staff accounts cannot claim (admin-only accounts), so a staff member with their own claim on an
     * item is someone who claimed as a student and was made staff later. T09 must still exclude them.
     */
    Long claimAsStudentThenPromote(User person, Long lostReportId) throws Exception {
        person.setRole(Role.USER);
        users.save(person);
        try {
            return createClaim(person, lostReportId);
        } finally {
            person.setRole(Role.ADMIN);
            users.save(person);
        }
    }

    ResultActions intake(User actor) throws Exception {
        return perform(json(post("/api/v1/claims/staff/custody/" + found + "/intake"),
                "{\"storageReference\":\"SEC-LOCKER-B12\",\"conditionNotes\":\"Matte black case\"}"), actor);
    }

    ResultActions decide(User actor, Long claimId, String action) throws Exception {
        return decide(actor, claimId, action, "");
    }

    ResultActions decide(User actor, Long claimId, String action, String physicalNotes) throws Exception {
        return perform(json(post("/api/v1/claims/staff/claims/" + claimId + "/decision"),
                "{\"action\":\"" + action + "\",\"reason\":\"Recorded test decision\",\"physicalCheckNotes\":\"" + physicalNotes + "\"}"), actor);
    }

    void setCategory(Long reportId, String category) {
        Report r = reports.findById(reportId).orElseThrow();
        r.setCategory(category);
        reports.save(r);
    }

    /** Records a staff manual assessment through Member 2's evidence API, as staff do after inspecting the item. */
    void assess(Long claimId, String serial, String mark, String accessory) throws Exception {
        String caseJson = perform(get("/api/evidence/staff/claims/" + claimId), staff).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String body = "{\"claimantVersionId\":" + JsonPath.read(caseJson, "$.summary.claimantVersionId")
                + ",\"finderVersionId\":" + JsonPath.read(caseJson, "$.summary.finderVersionId")
                + ",\"serialOutcome\":\"" + serial + "\",\"markOutcome\":\"" + mark + "\",\"accessoryOutcome\":\"" + accessory
                + "\",\"serialClueGroup\":\"serial\",\"markClueGroup\":\"mark\",\"accessoryClueGroup\":\"accessory\""
                + ",\"criticalConflict\":false,\"reason\":\"Physical inspection at the desk\"}";
        perform(json(post("/api/evidence/staff/claims/" + claimId + "/assessments"), body), staff).andExpect(status().isOk());
    }

    @Test
    void mediumRiskNeedsTwoMatchingCharacteristics_FR10() throws Exception {
        setCategory(lost, "Earbuds");
        setCategory(found, "Earbuds");
        Long id = createClaim(owner, lost);
        intake(staff).andExpect(status().isOk());

        decide(otherStaff, id, "APPROVE").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("MEDIUM risk needs at least 2")));
        perform(get("/api/v1/claims/staff/cases"), otherStaff)
                .andExpect(jsonPath("$[0].approvalBlockers[0]").value(containsString("MEDIUM risk needs at least 2")));

        assess(id, "MATCH", "MATCH", "UNKNOWN");
        perform(get("/api/v1/claims/staff/cases"), otherStaff).andExpect(jsonPath("$[0].approvalBlockers.length()").value(0));
        decide(otherStaff, id, "APPROVE").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void highRiskNeedsIdentifierInspectionNotesAndTwoDifferentApprovers_FR10() throws Exception {
        setCategory(lost, "Laptop");
        setCategory(found, "Laptop");
        Long id = createClaim(owner, lost);
        intake(staff).andExpect(status().isOk());

        assess(id, "UNKNOWN", "MATCH", "MATCH");
        decide(staff, id, "APPROVE", "Checked the case").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("serial/identifier")));

        assess(id, "MATCH", "MATCH", "UNKNOWN");
        decide(staff, id, "APPROVE").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("physical inspection notes")));

        // First of two approvals: nothing is reserved and the claimant still just sees "under review".
        decide(staff, id, "APPROVE", "Serial on the base plate checked").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.firstApproverId").value(staff.getId()));
        assertEquals(CustodyStatus.IN_CUSTODY, custody.findById(found).orElseThrow().getCustodyStatus());
        perform(get("/api/v1/claims/mine"), owner).andExpect(jsonPath("$[0].status").value("UNDER_REVIEW"));

        decide(staff, id, "APPROVE", "Approving again").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("different staff member")));
        perform(get("/api/v1/claims/staff/cases"), otherStaff)
                .andExpect(jsonPath("$[0].firstApproverName").value("staff@claims.test"))
                .andExpect(jsonPath("$[0].approvalBlockers.length()").value(0));

        decide(otherStaff, id, "APPROVE").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        assertEquals(id, custody.findById(found).orElseThrow().getReservedByClaimId());
        perform(get("/api/v1/claims/staff/claims/" + id + "/history"), otherStaff)
                .andExpect(jsonPath("$.events[?(@.type == 'APPROVE_FIRST')]").exists())
                .andExpect(jsonPath("$.events[?(@.type == 'APPROVE')].actorName").value("reviewer@claims.test"));
    }

    @Test
    void holdingAHighRiskClaimDiscardsItsFirstApproval_FR10() throws Exception {
        setCategory(lost, "Laptop");
        setCategory(found, "Laptop");
        Long id = createClaim(owner, lost);
        intake(staff).andExpect(status().isOk());
        assess(id, "MATCH", "MATCH", "UNKNOWN");
        decide(staff, id, "APPROVE", "Serial checked").andExpect(status().isOk());

        decide(otherStaff, id, "HOLD").andExpect(status().isOk());
        decide(otherStaff, id, "RECONSIDER").andExpect(status().isOk());

        // The earlier first approval no longer counts: this is a new first approval, not the second one.
        decide(otherStaff, id, "APPROVE", "Serial checked again").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.firstApproverId").value(otherStaff.getId()));
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
        perform(json(post(path), "{\"resolution\":\"\"}"), otherStaff).andExpect(status().isBadRequest());

        // Two-person rule: "staff" put the approved claim on hold, so someone else must resolve the dispute.
        perform(get("/api/v1/claims/staff/cases"), staff)
                .andExpect(jsonPath("$[0].disputeRaisedByName").value("staff@claims.test"))
                .andExpect(jsonPath("$[0].canResolveDispute").value(false));
        perform(get("/api/v1/claims/staff/cases"), otherStaff).andExpect(jsonPath("$[0].canResolveDispute").value(true));
        perform(json(post(path), resolution), staff).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("different staff member")));

        perform(json(post(path), resolution), otherStaff).andExpect(status().isOk())
                .andExpect(jsonPath("$.disputeBlocked").value(false));
        perform(json(post(path), resolution), otherStaff).andExpect(status().isConflict());
        perform(get("/api/v1/claims/staff/custody/" + found + "/history"), otherStaff)
                .andExpect(jsonPath("$.events[?(@.type == 'DISPUTE_RAISED')]").exists())
                .andExpect(jsonPath("$.events[?(@.type == 'DISPUTE_RESOLVED')].actorName").value("reviewer@claims.test"));

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
    void reportRiskLevelIsValidatedAndRaisesTheClaimTier_FR10() throws Exception {
        String reportJson = "{\"type\":\"FOUND\",\"category\":\"Earbuds\",\"colour\":\"Black\",\"campusZone\":\"Library\","
                + "\"eventDate\":\"" + LocalDate.now() + "\",\"riskLevel\":\"%s\"}";
        perform(json(post("/api/reports"), reportJson.formatted("EXTREME")), finder).andExpect(status().isBadRequest());
        perform(json(post("/api/reports"), reportJson.formatted("high")), finder).andExpect(status().isCreated())
                .andExpect(jsonPath("$.riskLevel").value("HIGH"));

        // The finder marks the item HIGH; the owner's LOW choice cannot lower the claim's tier.
        Report foundReport = reports.findById(found).orElseThrow();
        foundReport.setRiskLevel("HIGH");
        reports.save(foundReport);
        Report lostReport = reports.findById(lost).orElseThrow();
        lostReport.setRiskLevel("LOW");
        reports.save(lostReport);

        Long id = createClaim(owner, lost);
        assertEquals(com.findback.module3.domain.RiskTier.HIGH, claims.findById(id).orElseThrow().getRiskTier());
        perform(json(patch("/api/v1/claims/staff/claims/" + id + "/risk"), "{\"riskTier\":\"LOW\",\"reason\":\"Cheap item\"}"), staff)
                .andExpect(status().isConflict());
    }

    @Test
    void rejectedClaimantRequestsReconsiderationInsteadOfANewClaim() throws Exception {
        Long id = createClaim(owner, lost);
        decide(staff, id, "REJECT").andExpect(status().isOk());

        submit(owner, lost).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("Request reconsideration")));
        perform(get("/api/v1/claims/mine"), owner).andExpect(jsonPath("$[0].canRequestReconsideration").value(true));

        String request = "{\"reason\":\"I found the box with serial A82F printed on it\"}";
        String path = "/api/v1/claims/" + id + "/reconsideration";
        perform(json(post(path), request), rival).andExpect(status().isForbidden());
        perform(json(post(path), "{\"reason\":\" \"}"), owner).andExpect(status().isBadRequest());
        perform(json(post(path), request), owner).andExpect(status().isOk())
                .andExpect(jsonPath("$.reconsiderationStatus").value("PENDING"))
                .andExpect(jsonPath("$.canRequestReconsideration").value(false));
        perform(json(post(path), request), owner).andExpect(status().isConflict());

        // Staff see the request at the top of their queue, with the claimant's message.
        perform(get("/api/v1/claims/staff/cases"), otherStaff)
                .andExpect(jsonPath("$[0].claimId").value(id))
                .andExpect(jsonPath("$[0].reconsiderationPending").value(true))
                .andExpect(jsonPath("$[0].reconsiderationReason").value(containsString("A82F")));

        decide(staff, id, "RECONSIDER").andExpect(status().isOk());
        perform(get("/api/v1/claims/mine"), owner)
                .andExpect(jsonPath("$[0].status").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$[0].reconsiderationStatus").value("ACCEPTED"));
    }

    @Test
    void staffCanDeclineAReconsiderationRequest() throws Exception {
        Long id = createClaim(owner, lost);
        decide(staff, id, "REJECT").andExpect(status().isOk());
        decide(staff, id, "DECLINE_RECONSIDERATION").andExpect(status().isConflict());
        perform(json(post("/api/v1/claims/" + id + "/reconsideration"), "{\"reason\":\"New receipt\"}"), owner).andExpect(status().isOk());

        decide(staff, id, "DECLINE_RECONSIDERATION").andExpect(status().isOk());
        perform(get("/api/v1/claims/mine"), owner)
                .andExpect(jsonPath("$[0].status").value("REJECTED"))
                .andExpect(jsonPath("$[0].reconsiderationStatus").value("DECLINED"))
                .andExpect(jsonPath("$[0].canRequestReconsideration").value(false));
    }

    @Test
    void caseHistoryShowsWhoDidWhatAndWhy_FR12() throws Exception {
        Long id = createClaim(owner, lost);
        intake(staff).andExpect(status().isOk());
        decide(staff, id, "APPROVE").andExpect(status().isOk());
        perform(json(patch("/api/v1/claims/staff/claims/" + id + "/risk"), "{\"riskTier\":\"MEDIUM\",\"reason\":\"Expensive model\"}"), staff)
                .andExpect(status().isOk());

        String path = "/api/v1/claims/staff/claims/" + id + "/history";
        perform(get(path), owner).andExpect(status().isForbidden());
        perform(get(path), finder).andExpect(status().isForbidden());

        String body = perform(get(path), otherStaff).andExpect(status().isOk())
                .andExpect(jsonPath("$.claimantName").value("owner@claims.test"))
                .andExpect(jsonPath("$.events[0].type").value("CLAIM_SUBMITTED"))
                .andExpect(jsonPath("$.events[1].type").value("RECEIVED_INTO_CUSTODY"))
                .andExpect(jsonPath("$.events[2].type").value("APPROVE"))
                .andExpect(jsonPath("$.events[2].actorName").value("staff@claims.test"))
                .andExpect(jsonPath("$.events[2].details").value("Recorded test decision"))
                .andExpect(jsonPath("$.events[3].type").value("RISK_RAISED"))
                .andExpect(jsonPath("$.events[3].details").value(containsString("LOW -> MEDIUM: Expensive model")))
                .andReturn().getResponse().getContentAsString();
        assertFalse(body.contains("PRIVATE-"));

        perform(get("/api/v1/claims/staff/history"), otherStaff).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].claimId").value(id));
        // A staff member who has their own claim on the item sees neither the list entry nor the timeline (T09).
        Long staffLost = report(staff, ReportType.LOST);
        baseline(staffLost, staff);
        claimAsStudentThenPromote(staff, staffLost);
        perform(get(path), staff).andExpect(status().isForbidden());
        perform(get("/api/v1/claims/staff/history"), staff).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void custodyDeskReceivesItemsBeforeAnyoneClaimsThem_FR11() throws Exception {
        perform(get("/api/v1/claims/staff/custody"), owner).andExpect(status().isForbidden());

        // Handed in at the desk, nobody has claimed it yet: it is waiting for intake.
        perform(get("/api/v1/claims/staff/custody"), staff).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].foundReportId").value(found))
                .andExpect(jsonPath("$[0].custodyStatus").value("REPORTED"))
                .andExpect(jsonPath("$[0].finderName").value("finder@claims.test"))
                .andExpect(jsonPath("$[0].totalClaimsCount").value(0));

        intake(staff).andExpect(status().isOk());
        perform(get("/api/v1/claims/staff/custody"), otherStaff)
                .andExpect(jsonPath("$[0].custodyStatus").value("IN_CUSTODY"))
                .andExpect(jsonPath("$[0].storageReference").value("SEC-LOCKER-B12"))
                .andExpect(jsonPath("$[0].receivedByName").value("staff@claims.test"));

        // Moving it to another locker is logged as a re-confirmation, not a second intake.
        perform(json(post("/api/v1/claims/staff/custody/" + found + "/intake"),
                "{\"storageReference\":\"SEC-LOCKER-C03\",\"conditionNotes\":\"Moved to the large lockers\"}"), otherStaff)
                .andExpect(status().isOk());

        // A later claim starts with custody already confirmed.
        Long id = createClaim(owner, lost);
        perform(get("/api/v1/claims/staff/cases"), otherStaff)
                .andExpect(jsonPath("$[0].custodyStatus").value("IN_CUSTODY"))
                .andExpect(jsonPath("$[0].storageReference").value("SEC-LOCKER-C03"));

        String historyPath = "/api/v1/claims/staff/custody/" + found + "/history";
        perform(get(historyPath), otherStaff).andExpect(status().isOk())
                .andExpect(jsonPath("$.events[0].type").value("RECEIVED_INTO_CUSTODY"))
                .andExpect(jsonPath("$.events[1].type").value("STORAGE_RECONFIRMED"))
                .andExpect(jsonPath("$.events[1].details").value("Moved to the large lockers"))
                .andExpect(jsonPath("$.claims[0].claimId").value(id));
        perform(get("/api/v1/claims/staff/custody/" + lost + "/history"), otherStaff).andExpect(status().isBadRequest());

        // Staff who have their own claim on the item no longer see it on the desk (T09).
        Long staffLost = report(staff, ReportType.LOST);
        baseline(staffLost, staff);
        claimAsStudentThenPromote(staff, staffLost);
        perform(get("/api/v1/claims/staff/custody"), staff).andExpect(jsonPath("$.length()").value(0));
        perform(get(historyPath), staff).andExpect(status().isForbidden());
    }

    @Test
    void raisingTheTierOfAnApprovedClaimSendsItBackForReview_FR10() throws Exception {
        Long id = createClaim(owner, lost);
        intake(staff).andExpect(status().isOk());
        decide(staff, id, "APPROVE").andExpect(status().isOk());

        perform(json(patch("/api/v1/claims/staff/claims/" + id + "/risk"),
                "{\"riskTier\":\"HIGH\",\"reason\":\"It is a laptop, not an umbrella\"}"), otherStaff)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.riskTier").value("HIGH"));

        ItemCustodyState state = custody.findById(found).orElseThrow();
        assertEquals(CustodyStatus.IN_CUSTODY, state.getCustodyStatus());
        assertNull(state.getReservedByClaimId());
        assertFalse(state.isDisputeBlocked());
        perform(get("/api/v1/claims/mine"), owner).andExpect(jsonPath("$[0].status").value("UNDER_REVIEW"));

        // From now on the HIGH rules apply: the single automatic match is no longer enough.
        decide(otherStaff, id, "APPROVE", "Checked").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("HIGH risk needs at least 2")));
    }

    @Test
    void staffAccountsCannotUseStudentFeatures() throws Exception {
        String claimBody = "{\"lostReportId\":" + lost + ",\"foundReportId\":" + found + "}";
        perform(json(post("/api/v1/claims"), claimBody), staff).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("Staff accounts cannot use student features")));
        perform(get("/api/v1/claims/mine"), staff).andExpect(status().isForbidden());

        Long id = createClaim(owner, lost);
        perform(delete("/api/v1/claims/" + id), staff).andExpect(status().isForbidden());
        perform(json(post("/api/v1/claims/" + id + "/reconsideration"), "{\"reason\":\"x\"}"), staff).andExpect(status().isForbidden());

        String report = "{\"type\":\"LOST\",\"category\":\"Laptop\",\"colour\":\"Black\",\"campusZone\":\"Library\","
                + "\"eventDate\":\"" + LocalDate.now() + "\",\"riskLevel\":\"HIGH\"}";
        perform(json(post("/api/reports"), report), staff).andExpect(status().isForbidden());
        perform(json(post("/api/reports"), report), owner).andExpect(status().isCreated());
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
        claimAsStudentThenPromote(staff, staffLost);

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
        // The claim list used by Member 2's Evidence Workspace applies the same rule.
        perform(get("/api/v1/claims/staff/claims"), staff).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        perform(get("/api/v1/claims/staff/claims"), otherStaff).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void involvedStaffCannotEscalateDecideOrIntake_T09() throws Exception {
        Long ownerClaim = createClaim(owner, lost);
        Long staffLost = report(staff, ReportType.LOST);
        baseline(staffLost, staff);
        claimAsStudentThenPromote(staff, staffLost);
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
