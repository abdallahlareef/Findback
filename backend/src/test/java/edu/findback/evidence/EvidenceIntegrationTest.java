package edu.findback.evidence;

import edu.findback.user.*;
import edu.findback.report.*;
import com.findback.module3.domain.*;
import com.findback.module3.repository.*;
import com.findback.module3.service.ClaimReviewService;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.junit.jupiter.api.Assertions.*;
import com.jayway.jsonpath.JsonPath;

@SpringBootTest(classes=edu.findback.BackendApplication.class)
@AutoConfigureMockMvc
class EvidenceIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired ReportRepository reports;
    @Autowired ClaimRepository claims;
    @Autowired EvidenceVersions versions;
    @Autowired EvidencePairs pairs;
    @Autowired ClaimReviewService claimService;
    @Autowired EvidenceService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired ItemCustodyStateRepository custody;
    User owner,finder,staff,other;
    Long lost,found;
    static final String DATA="{\"serialSuffix\":\" a82f \",\"privateMark\":\"PRIVATE-MARK-TEST\",\"accessoryDescription\":\"PRIVATE-ACCESSORY-TEST\"}";
    @BeforeEach void setup() {
        SecurityContextHolder.clearContext();
        for(String table:new String[]{"m2_verification_attempts","m2_supplement_requests","m2_evidence_versions","m2_evidence_pairs","admin_review","custody_event","item_custody_state","claim","reports","app_users"}) jdbc.execute("DELETE FROM "+table);
        owner=user("owner@test.example",Role.USER); finder=user("finder@test.example",Role.USER);
        staff=user("staff@test.example",Role.ADMIN); other=user("other@test.example",Role.USER);
        lost=report(owner,ReportType.LOST); found=report(finder,ReportType.FOUND);
    }
    User user(String email,Role role) { User u=new User();u.setName(email);u.setEmail(email);u.setRole(role);u.setPasswordHash("{noop}testpassword"); return users.save(u); }
    Long report(User actor,ReportType type) { Report r=new Report();r.setAuthor(actor);r.setType(type);r.setCategory("EARBUDS");r.setColour("Black");r.setCampusZone("Library");r.setEventDate(LocalDate.now());return reports.save(r).getId(); }
    ResultActions perform(MockHttpServletRequestBuilder request,User actor) throws Exception {
        return mvc.perform(request.with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(actor.getEmail()).roles(actor.getRole().name())).with(csrf()));
    }
    MockHttpServletRequestBuilder json(String path,String body) { return post(path).contentType("application/json").content(body); }
    void baselines() throws Exception {
        perform(json("/api/evidence/reports/"+lost+"/baseline",DATA),owner).andExpect(status().isOk());
        perform(json("/api/evidence/reports/"+found+"/baseline",DATA),finder).andExpect(status().isOk());
    }
    Long createClaim() throws Exception {
        String body=perform(json("/api/v1/claims","{\"lostReportId\":"+lost+",\"foundReportId\":"+found+"}"),owner).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(body.contains("flagReason")); assertFalse(body.contains("strength"));
        return ((Number)JsonPath.read(body,"$.claimId")).longValue();
    }
    void request(Long claim,String key) throws Exception { perform(json("/api/evidence/staff/claims/"+claim+"/supplement-requests","{\"requestId\":\""+key+"\",\"reason\":\"Please add any independently known details.\"}"),staff).andExpect(status().isOk()); }
    String supplement(String key,String data) { return "{\"requestId\":\""+key+"\",\"reason\":\"I remembered another detail\",\"evidence\":"+data+"}"; }

    @Test void baselineIsImmutableAndReplayDoesNotAddVersions() throws Exception {
        baselines(); perform(json("/api/evidence/reports/"+lost+"/baseline",DATA),owner).andExpect(status().isOk());
        perform(json("/api/evidence/reports/"+lost+"/baseline",DATA.replace("a82f","b123")),owner).andExpect(status().isConflict());
        assertEquals(2,versions.count());
        perform(get("/api/evidence/reports/"+lost),owner).andExpect(jsonPath("$[0].evidence.serialSuffix").value("A82F"));
    }
    @Test void unauthorizedAndOpposingUsersCannotReadOrWrite() throws Exception {
        baselines(); Long id=createClaim();
        mvc.perform(get("/api/evidence/reports/"+lost)).andExpect(status().isUnauthorized());
        perform(get("/api/evidence/reports/"+lost),finder).andExpect(status().isForbidden());
        perform(get("/api/evidence/claims/"+id),other).andExpect(status().isForbidden());
        perform(get("/api/evidence/staff/claims/"+id),owner).andExpect(status().isForbidden());
        perform(json("/api/evidence/reports/"+lost+"/baseline",DATA),other).andExpect(status().isForbidden());
        mvc.perform(json("/api/evidence/reports/"+lost+"/baseline",DATA).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(owner.getEmail()))).andExpect(status().isForbidden());
    }
    @Test void staffWhoIsAlsoPartyCannotReview() throws Exception {
        baselines();Long id=createClaim(); owner.setRole(Role.ADMIN);users.save(owner);
        perform(get("/api/evidence/staff/claims/"+id),owner).andExpect(status().isForbidden());
    }
    @Test void staffCannotUseACompetingClaimToReadTheirOpposingEvidence() throws Exception {
        baselines();Long first=createClaim();
        Long staffLost=report(staff,ReportType.LOST);
        perform(json("/api/evidence/reports/"+staffLost+"/baseline",DATA),staff).andExpect(status().isOk());
        // Staff accounts cannot claim (admin-only accounts): model a student who claimed, then became staff.
        staff.setRole(Role.USER);users.save(staff);
        perform(json("/api/v1/claims","{\"lostReportId\":"+staffLost+",\"foundReportId\":"+found+"}"),staff).andExpect(status().isOk());
        staff.setRole(Role.ADMIN);users.save(staff);
        perform(get("/api/evidence/staff/claims/"+first),staff).andExpect(status().isForbidden());
    }
    @Test void discoveryRequiresOwnedBaseline() throws Exception {
        perform(get("/api/reports/"+lost+"/candidates"),owner).andExpect(status().isConflict());
        baselines();perform(get("/api/reports/"+lost+"/candidates"),other).andExpect(status().isForbidden());
        String result=perform(get("/api/reports/"+lost+"/candidates"),owner).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(result.contains("PRIVATE-"));assertFalse(result.contains("serialSuffix"));
    }
    @Test void claimantSeesNeutralStatusAndStaffGetsConservativeComparison() throws Exception {
        baselines();Long id=createClaim();
        perform(get("/api/evidence/claims/"+id),owner).andExpect(jsonPath("$.status").value("UnderReview")).andExpect(jsonPath("$.summary").doesNotExist());
        perform(get("/api/evidence/staff/claims/"+id),staff).andExpect(jsonPath("$.summary.strength").value("WEAK"))
            .andExpect(jsonPath("$.summary.markOutcome").value("UNKNOWN")).andExpect(jsonPath("$.summary.serialOutcome").value("MATCH"));
    }
    @Test void twoSupplementsIdempotencyAndFrozenHistory() throws Exception {
        baselines();Long id=createClaim();String path="/api/evidence/claims/"+id+"/supplements";
        perform(json(path,supplement(UUID.randomUUID().toString(),DATA)),owner).andExpect(status().isConflict());
        for(int i=0;i<2;i++) {
            String key=UUID.randomUUID().toString();request(id,key);request(id,key);
            perform(json(path,supplement(key,DATA)),owner).andExpect(status().isOk()).andExpect(jsonPath("$.submissionsUsed").value(i+2));
            perform(json(path,supplement(key,DATA)),owner).andExpect(status().isOk()).andExpect(jsonPath("$.submissionsUsed").value(i+2));
            perform(json(path,supplement(key,DATA.replace("a82f","z999"))),owner).andExpect(status().isConflict());
        }
        perform(json("/api/evidence/staff/claims/"+id+"/supplement-requests","{\"requestId\":\""+UUID.randomUUID()+"\",\"reason\":\"Another\"}"),staff).andExpect(status().isConflict());
        perform(get("/api/evidence/claims/"+id),owner).andExpect(jsonPath("$.versions.length()").value(3)).andExpect(jsonPath("$.versions[0].version").value(1));
    }
    @Test void recreatedClaimAndReportCannotResetPairCounter() throws Exception {
        baselines();Long id=createClaim();String key=UUID.randomUUID().toString();request(id,key);
        perform(json("/api/evidence/claims/"+id+"/supplements",supplement(key,DATA)),owner).andExpect(status().isOk());
        perform(delete("/api/v1/claims/"+id),owner).andExpect(status().isOk());
        lost=report(owner,ReportType.LOST);perform(json("/api/evidence/reports/"+lost+"/baseline",DATA.replace("a82f","z999")),owner).andExpect(status().isOk());
        Long next=createClaim();
        perform(get("/api/evidence/claims/"+next),owner).andExpect(jsonPath("$.submissionsUsed").value(2)).andExpect(jsonPath("$.versions[0].evidence.serialSuffix").value("A82F"));
        assertEquals(1,pairs.count());
    }
    @Test void malformedEvidenceRollsBackAndDoesNotConsumeRequest() throws Exception {
        baselines();Long id=createClaim();String key=UUID.randomUUID().toString();request(id,key);
        perform(json("/api/evidence/claims/"+id+"/supplements",supplement(key,DATA.replace("a82f","TOOLONG"))),owner).andExpect(status().isBadRequest());
        perform(get("/api/evidence/claims/"+id),owner).andExpect(jsonPath("$.submissionsUsed").value(1)).andExpect(jsonPath("$.pendingRequestId").value(key));
    }
    @Test void conflictingSuffixPlacesCaseOnHoldWithoutLeakingResults() throws Exception {
        baselines();Long id=createClaim();String key=UUID.randomUUID().toString();
        perform(json("/api/evidence/reports/"+found+"/finder-corrections",supplement(key,DATA.replace("a82f","z999"))),finder).andExpect(status().isOk());
        assertEquals(ClaimStatus.ON_HOLD,claims.findById(id).orElseThrow().getStatus());
        perform(get("/api/evidence/staff/claims/"+id),staff).andExpect(jsonPath("$.summary.strength").value("REVIEW_REQUIRED"));
        perform(get("/api/evidence/claims/"+id),owner).andExpect(jsonPath("$.submissionsUsed").value(1));
    }
    @Test void manualAssessmentDeduplicatesCluesAndRequiresReason() throws Exception {
        baselines();Long id=createClaim();String assessment="{\"serialOutcome\":\"MATCH\",\"markOutcome\":\"MATCH\",\"accessoryOutcome\":\"MATCH\",\"serialClueGroup\":\"serial\",\"markClueGroup\":\"sticker\",\"accessoryClueGroup\":\"sticker\",\"criticalConflict\":false,\"reason\":\"Physical inspection confirms two independent private clues\"}";
        String caseJson=perform(get("/api/evidence/staff/claims/"+id),staff).andReturn().getResponse().getContentAsString();
        assessment=assessment.replaceFirst("\\{", "{\"claimantVersionId\":"+JsonPath.read(caseJson,"$.summary.claimantVersionId")+",\"finderVersionId\":"+JsonPath.read(caseJson,"$.summary.finderVersionId")+",");
        perform(json("/api/evidence/staff/claims/"+id+"/assessments",assessment),staff).andExpect(status().isOk()).andExpect(jsonPath("$.distinctMatches").value(2)).andExpect(jsonPath("$.strength").value("MODERATE"));
        perform(get("/api/evidence/staff/claims/"+id),staff).andExpect(jsonPath("$.history.length()").value(2));
    }
    @Test void submittedUserHeaderCannotImpersonateOwner() throws Exception {
        baselines();perform(json("/api/v1/claims","{\"lostReportId\":"+lost+",\"foundReportId\":"+found+"}").header("X-User-Id",owner.getId()),other).andExpect(status().isForbidden());
    }
    @Test void parallelRetriesSaveOnlyOneSupplement() throws Exception {
        baselines(); Long id=createClaim(); String key=UUID.randomUUID().toString(); request(id,key);
        var executor=java.util.concurrent.Executors.newFixedThreadPool(2);
        var start=new java.util.concurrent.CountDownLatch(1);
        try {
            java.util.concurrent.Callable<Integer> task=()->{
                start.await();
                return perform(json("/api/evidence/claims/"+id+"/supplements",supplement(key,DATA)),owner).andReturn().getResponse().getStatus();
            };
            var first=executor.submit(task);var second=executor.submit(task);start.countDown();
            assertEquals(200,first.get(15,java.util.concurrent.TimeUnit.SECONDS));assertEquals(200,second.get(15,java.util.concurrent.TimeUnit.SECONDS));
            perform(get("/api/evidence/claims/"+id),owner).andExpect(jsonPath("$.submissionsUsed").value(2)).andExpect(jsonPath("$.versions.length()").value(2));
        } finally { executor.shutdownNow(); }
    }
    @Test void evidenceChangeAfterApprovalHoldsCaseAndReleasesReservation() throws Exception {
        baselines();Long id=createClaim();
        claimService.recordCustodyIntake(staff.getId(),found,"TEST-LOCKER","Synthetic item");
        // FR-10: one matching characteristic only suffices for LOW risk; this test is about evidence holds, not tiers.
        Claim lowRisk=claims.findById(id).orElseThrow(); lowRisk.setRiskTier(RiskTier.LOW); claims.save(lowRisk);
        claimService.recordStaffDecision(staff.getId(),id,"APPROVE","Physical check complete","Synthetic check");
        assertEquals(ClaimStatus.APPROVED,claims.findById(id).orElseThrow().getStatus());
        request(id,UUID.randomUUID().toString());
        assertEquals(ClaimStatus.ON_HOLD,claims.findById(id).orElseThrow().getStatus());
        assertNull(custody.findById(found).orElseThrow().getReservedByClaimId());
        assertEquals(CustodyStatus.IN_CUSTODY,custody.findById(found).orElseThrow().getCustodyStatus());
    }
    @Test void sessionLoginUsesServerIdentityAndLogoutClearsAccess() throws Exception {
        var result=mvc.perform(json("/api/auth/login","{\"email\":\"owner@test.example\",\"password\":\"testpassword\"}").with(csrf())).andExpect(status().isOk()).andReturn();
        var session=(org.springframework.mock.web.MockHttpSession)result.getRequest().getSession(false);
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(owner.getId()));
        mvc.perform(post("/api/auth/logout").session(session).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }
    @Test void staffCorrectionDoesNotConsumeAttemptOrLeakStaffNotes() throws Exception {
        baselines();Long id=createClaim();String key=UUID.randomUUID().toString();
        perform(json("/api/evidence/staff/claims/"+id+"/corrections/CLAIMANT",supplement(key,DATA.replace("PRIVATE-MARK-TEST","STAFF-ONLY-OBSERVATION"))),staff).andExpect(status().isOk());
        String body=perform(get("/api/evidence/claims/"+id),owner).andExpect(status().isOk()).andExpect(jsonPath("$.submissionsUsed").value(1)).andReturn().getResponse().getContentAsString();
        assertFalse(body.contains("STAFF-ONLY-OBSERVATION"));
        key=UUID.randomUUID().toString();request(id,key);
        perform(json("/api/evidence/claims/"+id+"/supplements",supplement(key,DATA)),owner).andExpect(status().isOk()).andExpect(jsonPath("$.submissionsUsed").value(2));
        perform(get("/api/evidence/staff/claims/"+id),staff).andExpect(jsonPath("$.claimant.versions.length()").value(3));
    }
    @Test void staleManualAssessmentIsRejected() throws Exception {
        baselines();Long id=createClaim();
        perform(json("/api/evidence/staff/claims/"+id+"/assessments","{\"claimantVersionId\":-1,\"finderVersionId\":-1,\"serialOutcome\":\"MATCH\",\"markOutcome\":\"UNKNOWN\",\"accessoryOutcome\":\"UNKNOWN\",\"serialClueGroup\":\"serial\",\"criticalConflict\":false,\"reason\":\"Inspection\"}"),staff).andExpect(status().isConflict());
    }
    @Test void pendingRequestCannotBeUsedForAnotherFoundItem() throws Exception {
        baselines();Long first=createClaim();String key=UUID.randomUUID().toString();request(first,key);
        found=report(finder,ReportType.FOUND);perform(json("/api/evidence/reports/"+found+"/baseline",DATA),finder).andExpect(status().isOk());Long second=createClaim();
        perform(json("/api/evidence/claims/"+second+"/supplements",supplement(key,DATA)),owner).andExpect(status().isConflict());
        perform(get("/api/evidence/claims/"+first),owner).andExpect(jsonPath("$.pendingRequestId").value(key)).andExpect(jsonPath("$.submissionsUsed").value(1));
    }

    @Test void evidenceChangeInvalidatesFirstHighRiskApproval() throws Exception {
        baselines(); Long id=createClaim();
        Claim claim=claims.findById(id).orElseThrow();
        claim.setRiskTier(RiskTier.HIGH); claim.recordFirstApproval(staff.getId()); claims.save(claim);
        perform(json("/api/evidence/reports/"+found+"/finder-corrections",
            supplement(UUID.randomUUID().toString(),DATA)),finder).andExpect(status().isOk());
        Claim changed=claims.findById(id).orElseThrow();
        assertNull(changed.getFirstApproverId(),"Approval of an earlier evidence version must not survive a correction");
        assertEquals(ClaimStatus.ON_HOLD,changed.getStatus());
    }

    @Test void closedClaimDoesNotOfferPendingSupplement() throws Exception {
        baselines(); Long id=createClaim(); String key=UUID.randomUUID().toString(); request(id,key);
        for (ClaimStatus state : new ClaimStatus[]{ClaimStatus.REJECTED,ClaimStatus.WITHDRAWN,ClaimStatus.COMPLETED}) {
            Claim claim=claims.findById(id).orElseThrow(); claim.setStatus(state); claims.save(claim);
            perform(get("/api/evidence/claims/"+id),owner).andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingRequestId").isEmpty())
                .andExpect(jsonPath("$.status").value(switch(state) {
                    case REJECTED -> "Rejected"; case WITHDRAWN -> "Withdrawn"; default -> "Completed";
                }));
            perform(json("/api/evidence/claims/"+id+"/supplements",supplement(key,DATA)),owner)
                .andExpect(status().isConflict());
        }
    }
}
