package edu.findback.evidence;

import com.findback.module3.domain.*;
import com.findback.module3.repository.*;
import com.findback.module3.ports.CrossModulePorts.*;
import edu.findback.report.*;
import edu.findback.user.User;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class EvidenceService implements DoveEvaluationPort, ReportLookupPort {
    private final EvidenceVersions versions;
    private final EvidencePairs pairs;
    private final SupplementRequests requests;
    private final VerificationAttempts attempts;
    private final ReportRepository reports;
    private final ClaimRepository claims;
    private final ItemCustodyStateRepository custody;
    private final AdminReviewRepository reviews;
    private final EvidenceAccess access;
    private final OperationsPort operations;
    public EvidenceService(EvidenceVersions versions, EvidencePairs pairs, SupplementRequests requests,
            VerificationAttempts attempts, ReportRepository reports, ClaimRepository claims,
            ItemCustodyStateRepository custody, AdminReviewRepository reviews, EvidenceAccess access, OperationsPort operations) {
        this.versions=versions; this.pairs=pairs; this.requests=requests; this.attempts=attempts;
        this.reports=reports; this.claims=claims; this.custody=custody; this.reviews=reviews; this.access=access; this.operations=operations;
    }
    public record VersionView(Long id, int version, String provenance, java.time.Instant createdAt, EvidenceData evidence, String reason) {}
    public record PairView(String status, int submissionsUsed, String pendingRequestId, List<VersionView> versions) {}
    public record Summary(String strength, String serialOutcome, String markOutcome, String accessoryOutcome,
            int distinctMatches, boolean criticalConflict, Long claimantVersionId, Long finderVersionId,
            Long reviewerId, String reason) {}
    public record StaffView(Long claimId, PairView claimant, List<VersionView> finder, Summary summary, List<Summary> history) {}
    public enum Outcome { MATCH, UNKNOWN, CONTRADICTION }
    public record Assessment(Long claimantVersionId, Long finderVersionId, Outcome serialOutcome, Outcome markOutcome, Outcome accessoryOutcome,
            String serialClueGroup, String markClueGroup, String accessoryClueGroup, boolean criticalConflict, String reason) {}

    private static String reportKey(Long id) { return "REPORT:"+id; }
    private static ResponseStatusException error(HttpStatus status, String message) { return new ResponseStatusException(status,message); }
    private Report report(Long id) { return reports.findById(id).orElseThrow(()->error(HttpStatus.NOT_FOUND,"Report not found.")); }
    private Report lockReport(Long id) { return reports.lockById(id).orElseThrow(()->error(HttpStatus.NOT_FOUND,"Report not found.")); }
    private Claim claim(Long id) { return claims.findById(id).orElseThrow(()->error(HttpStatus.NOT_FOUND,"Claim not found.")); }
    private void owns(Report report, User actor) {
        if(!report.getAuthor().getId().equals(actor.getId())) throw error(HttpStatus.FORBIDDEN,"Report access denied.");
    }
    private User reviewer(Claim claim) {
        User actor=access.staff();
        if(actor.getId().equals(claim.getClaimantId()) || actor.getId().equals(report(claim.getFoundReportId()).getAuthor().getId())
                || !claims.findByClaimantIdAndFoundReportId(actor.getId(),claim.getFoundReportId()).isEmpty())
            throw error(HttpStatus.FORBIDDEN,"You cannot review your own case.");
        return actor;
    }
    private EvidenceVersion latest(String key) { return versions.findFirstByStreamKeyOrderByVersionNumberDesc(key).orElse(null); }
    private VersionView view(EvidenceVersion v) { return new VersionView(v.id,v.versionNumber,v.provenance,v.createdAt,v.data(),v.reason); }
    private List<VersionView> history(String key) { return versions.findByStreamKeyOrderByVersionNumberAsc(key).stream().map(this::view).toList(); }
    private void audit(Long actor, String action, String ref) { operations.recordAuditEvent(actor,action,ref,"RECORDED"); }

    public VersionView baseline(Long reportId, EvidenceData data) {
        User actor=access.current(); Report report=lockReport(reportId); owns(report,actor);
        EvidenceVersion existing=latest(reportKey(reportId));
        if(existing!=null) {
            EvidenceVersion first=versions.findByStreamKeyOrderByVersionNumberAsc(reportKey(reportId)).get(0);
            if(first.data().equals(data)) return view(first);
            throw error(HttpStatus.CONFLICT,"The baseline is frozen. Use the authorized correction workflow.");
        }
        EvidenceVersion saved=versions.save(new EvidenceVersion(reportKey(reportId),reportId,actor.getId(),1,
            report.getType()==ReportType.LOST ? "CLAIMANT" : "FINDER",data,null,null));
        audit(actor.getId(),"EVIDENCE_BASELINE","REPORT-"+reportId);
        return view(saved);
    }
    public List<VersionView> ownReport(Long reportId) {
        User actor=access.current(); owns(report(reportId),actor);
        audit(actor.getId(),"EVIDENCE_OWN_READ","REPORT-"+reportId);
        return history(reportKey(reportId)).stream().filter(v->!v.provenance().equals("STAFF_CORRECTION")).toList();
    }
    public void requireDiscoveryBaseline(Long reportId) {
        owns(report(reportId),access.current());
        if(!versions.existsByStreamKey(reportKey(reportId))) throw error(HttpStatus.CONFLICT,"Save private baseline evidence before viewing candidates.");
    }
    @Override public ReportSummaryDto getReportById(Long reportId) {
        Report r=report(reportId);
        return new ReportSummaryDto(r.getId(),r.getAuthor().getId(),r.getType().name(),r.getCategory(),
            r.getCategory(),r.getCampusZone(),versions.existsByStreamKey(reportKey(reportId)),r.getRiskLevel());
    }
    @Override public ReportSummaryDto lockReportForUpdate(Long reportId) { lockReport(reportId); return getReportById(reportId); }
    // The found-report row serializes all writes for a pair, including first creation.
    // Counters survive claim withdrawal/recreation; they are not owned by the claim row.
    private EvidencePair pair(Claim claim) {
        lockReport(claim.getFoundReportId());
        var existing=pairs.findByClaimantIdAndFoundReportId(claim.getClaimantId(),claim.getFoundReportId());
        if(existing.isPresent()) return existing.get();
        EvidenceVersion baseline=versions.findByStreamKeyOrderByVersionNumberAsc(reportKey(claim.getLostReportId())).stream().findFirst()
            .orElseThrow(()->error(HttpStatus.CONFLICT,"Baseline evidence is required."));
        EvidencePair pair=pairs.saveAndFlush(new EvidencePair(claim.getClaimantId(),claim.getFoundReportId(),claim.getLostReportId()));
        pair.submissions=1;
        versions.save(new EvidenceVersion(pair.stream(),claim.getLostReportId(),claim.getClaimantId(),1,"CLAIMANT",baseline.data(),null,null));
        return pair;
    }
    private PairView pairView(EvidencePair pair) {
        return new PairView(pair.pendingRequest==null ? "UnderReview" : "MoreInformationRequested", pair.submissions,pair.pendingRequest,history(pair.stream()));
    }
    private PairView ownPairView(EvidencePair pair, Claim claim) {
        // Keep detailed review flags private, but never advertise a submission on a closed case.
        String status=switch(claim.getStatus()) {
            case COMPLETED -> "Completed";
            case REJECTED -> "Rejected";
            case WITHDRAWN -> "Withdrawn";
            case APPROVED -> "Approved";
            default -> pair.pendingRequest==null ? "UnderReview" : "MoreInformationRequested";
        };
        boolean closed=claim.getStatus()==ClaimStatus.COMPLETED || claim.getStatus()==ClaimStatus.REJECTED
            || claim.getStatus()==ClaimStatus.WITHDRAWN;
        return new PairView(status,pair.submissions,closed ? null : pair.pendingRequest,
            history(pair.stream()).stream().filter(v->v.provenance().equals("CLAIMANT")).toList());
    }
    public PairView ownClaim(Long claimId) {
        Claim c=claim(claimId); User actor=access.current();
        if(!c.getClaimantId().equals(actor.getId())) throw error(HttpStatus.FORBIDDEN,"Claim access denied.");
        audit(actor.getId(),"EVIDENCE_OWN_READ","CLAIM-"+claimId);
        return ownPairView(pair(c),c);
    }
    public StaffView staffView(Long claimId) {
        Claim c=claim(claimId); User actor=reviewer(c); EvidencePair p=pair(c);
        VerificationAttempt result=compare(c,p);
        audit(actor.getId(),"EVIDENCE_STAFF_READ","CLAIM-"+claimId);
        return new StaffView(claimId,pairView(p),history(reportKey(c.getFoundReportId())),summary(result),
            attempts.findByClaimIdOrderByIdAsc(claimId).stream().map(this::summary).toList());
    }
    public PairView requestSupplement(Long claimId, String requestId, String reason) {
        Claim c=claim(claimId); User actor=reviewer(c); EvidencePair p=pair(c);
        String key=requestKey(requestId); String why=EvidenceData.reason(reason);
        var previous=requests.findByPairIdAndRequestKey(p.id,key);
        if(previous.isPresent()) {
            if(!previous.get().reason.equals(why) || !previous.get().actorId.equals(actor.getId()))
                throw error(HttpStatus.CONFLICT,"Request ID already used with different content.");
            return pairView(p);
        }
        if(c.getStatus()==ClaimStatus.COMPLETED || c.getStatus()==ClaimStatus.WITHDRAWN || c.getStatus()==ClaimStatus.REJECTED)
            throw error(HttpStatus.CONFLICT,"Reopen the case before requesting evidence.");
        if(p.pendingRequest!=null) throw error(HttpStatus.CONFLICT,"A supplement is already pending.");
        if(p.requests>=2 || p.submissions>=3) throw error(HttpStatus.CONFLICT,"The two-supplement limit has been reached.");
        p.pendingRequest=key; p.requests++;
        requests.save(new SupplementRequest(p.id,key,actor.getId(),why));
        hold(c,actor.getId());
        audit(actor.getId(),"SUPPLEMENT_REQUESTED","CLAIM-"+claimId);
        return pairView(p);
    }
    public PairView supplement(Long claimId, String requestId, EvidenceData data, String reason) {
        Claim c=claim(claimId); User actor=access.current();
        if(!c.getClaimantId().equals(actor.getId())) throw error(HttpStatus.FORBIDDEN,"Claim access denied.");
        EvidencePair p=pair(c); String key=requestKey(requestId); String why=EvidenceData.reason(reason);
        var duplicate=versions.findByStreamKeyOrderByVersionNumberAsc(p.stream()).stream().filter(v->key.equals(v.requestId)).findFirst();
        if(duplicate.isPresent()) {
            if(!duplicate.get().actorId.equals(actor.getId()) || !duplicate.get().provenance.equals("CLAIMANT") || !duplicate.get().data().equals(data) || !Objects.equals(duplicate.get().reason,why))
                throw error(HttpStatus.CONFLICT,"Request ID already used with different content.");
            return ownPairView(p,c);
        }
        if(c.getStatus()==ClaimStatus.COMPLETED || c.getStatus()==ClaimStatus.WITHDRAWN || c.getStatus()==ClaimStatus.REJECTED)
            throw error(HttpStatus.CONFLICT,"This case is closed for submissions.");
        if(!key.equals(p.pendingRequest) || p.submissions>=3) throw error(HttpStatus.CONFLICT,"A valid staff supplement request is required.");
        versions.save(new EvidenceVersion(p.stream(),p.lostReportId,actor.getId(),latest(p.stream()).versionNumber+1,"CLAIMANT",data,why,key));
        p.submissions++;
        p.pendingRequest=null;
        for(Claim linked:claims.findByClaimantIdAndFoundReportId(c.getClaimantId(),c.getFoundReportId())) {
            hold(linked,actor.getId()); compare(linked,p);
        }
        audit(actor.getId(),"EVIDENCE_SUPPLEMENT","CLAIM-"+claimId);
        return ownPairView(p,c);
    }
    public VersionView finderCorrection(Long reportId, String requestId, EvidenceData data, String reason) {
        User actor=access.current(); Report r=lockReport(reportId); owns(r,actor);
        if(r.getType()!=ReportType.FOUND) throw error(HttpStatus.CONFLICT,"Claimant changes require a staff-requested supplement.");
        String key=requestKey(requestId), why=EvidenceData.reason(reason); String stream=reportKey(reportId);
        var duplicate=versions.findByStreamKeyOrderByVersionNumberAsc(stream).stream().filter(v->key.equals(v.requestId)).findFirst();
        if(duplicate.isPresent()) {
            if(!duplicate.get().actorId.equals(actor.getId()) || !duplicate.get().provenance.equals("FINDER") || !duplicate.get().data().equals(data) || !Objects.equals(duplicate.get().reason,why)) throw error(HttpStatus.CONFLICT,"Request ID already used with different content.");
            return view(duplicate.get());
        }
        EvidenceVersion previous=latest(stream);
        if(previous==null) throw error(HttpStatus.CONFLICT,"Save baseline evidence first.");
        EvidenceVersion saved=versions.save(new EvidenceVersion(stream,reportId,actor.getId(),previous.versionNumber+1,"FINDER",data,why,key));
        for(Claim c:claims.findByFoundReportId(reportId)) { hold(c,actor.getId()); compare(c,pair(c)); }
        audit(actor.getId(),"FINDER_CORRECTION","REPORT-"+reportId);
        return view(saved);
    }
    private String requestKey(String value) {
        try { return UUID.fromString(value).toString(); }
        catch(Exception e) { throw error(HttpStatus.BAD_REQUEST,"A UUID requestId is required."); }
    }
    public VersionView staffCorrection(Long claimId, String side, String requestId, EvidenceData data, String reason) {
        Claim c=claim(claimId); User actor=reviewer(c); EvidencePair p=pair(c);
        if(!"CLAIMANT".equals(side) && !"FINDER".equals(side)) throw error(HttpStatus.BAD_REQUEST,"Side must be CLAIMANT or FINDER.");
        String stream="CLAIMANT".equals(side)?p.stream():reportKey(c.getFoundReportId());
        String key=requestKey(requestId), why=EvidenceData.reason(reason);
        var duplicate=versions.findByStreamKeyOrderByVersionNumberAsc(stream).stream().filter(v->key.equals(v.requestId)).findFirst();
        if(duplicate.isPresent()) {
            EvidenceVersion v=duplicate.get();
            if(!v.actorId.equals(actor.getId()) || !v.data().equals(data) || !Objects.equals(v.reason,why)) throw error(HttpStatus.CONFLICT,"Request ID already used with different content.");
            return view(v);
        }
        EvidenceVersion previous=latest(stream);
        if(previous==null) throw error(HttpStatus.CONFLICT,"Baseline evidence is required before a correction.");
        EvidenceVersion saved=versions.save(new EvidenceVersion(stream,previous.reportId,actor.getId(),previous.versionNumber+1,"STAFF_CORRECTION",data,why,key));
        var linked="CLAIMANT".equals(side)?claims.findByClaimantIdAndFoundReportId(c.getClaimantId(),c.getFoundReportId()):claims.findByFoundReportId(c.getFoundReportId());
        for(Claim item:linked) { hold(item,actor.getId()); compare(item,pair(item)); }
        audit(actor.getId(),"STAFF_EVIDENCE_CORRECTION","CLAIM-"+claimId);
        return view(saved);
    }
    @Override public DoveSummaryDto evaluateOrGetSummary(Long claimId, Long lostReportId, Long foundReportId) {
        Claim c=claim(claimId);
        if(!Objects.equals(c.getLostReportId(),lostReportId) || !Objects.equals(c.getFoundReportId(),foundReportId))
            throw error(HttpStatus.CONFLICT,"Claim/report mismatch.");
        EvidencePair p=pair(c); VerificationAttempt a=compare(c,p);
        return new DoveSummaryDto(claimId,a.strength,a.distinctMatches,
            count(a,"UNKNOWN"),count(a,"CONTRADICTION"),a.criticalConflict,p.submissions,"MATCH".equals(a.serialOutcome));
    }
    @Override public boolean hasPendingSupplement(Long claimId) { return pair(claim(claimId)).pendingRequest!=null; }
    private int count(VerificationAttempt a,String outcome) { return (int)List.of(a.serialOutcome,a.markOutcome,a.accessoryOutcome).stream().filter(outcome::equals).count(); }
    private VerificationAttempt compare(Claim c,EvidencePair p) {
        EvidenceVersion owner=latest(p.stream()), finder=latest(reportKey(c.getFoundReportId()));
        var old=attempts.findFirstByClaimIdOrderByIdDesc(c.getClaimId());
        if(old.isPresent() && old.get().claimantVersionId.equals(owner.id) && Objects.equals(old.get().finderVersionId,finder==null?null:finder.id)) return old.get();
        VerificationAttempt a=new VerificationAttempt();
        a.claimId=c.getClaimId(); a.claimantVersionId=owner.id; a.finderVersionId=finder==null?null:finder.id;
        a.serialOutcome=owner.serialSuffix==null || finder==null || finder.serialSuffix==null ? "UNKNOWN"
            : owner.serialSuffix.equals(finder.serialSuffix)?"MATCH":"CONTRADICTION";
        a.markOutcome="UNKNOWN"; a.accessoryOutcome="UNKNOWN";
        a.distinctMatches=a.serialOutcome.equals("MATCH")?1:0;
        a.criticalConflict=a.serialOutcome.equals("CONTRADICTION");
        a.strength=strength(a); attempts.save(a);
        if(a.criticalConflict && c.getStatus()!=ClaimStatus.COMPLETED && c.getStatus()!=ClaimStatus.REJECTED && c.getStatus()!=ClaimStatus.WITHDRAWN) {
            hold(c,null); c.setStatus(ClaimStatus.ON_HOLD); c.setInternalReviewFlag(true); c.setFlagReason("DOVE requires staff review.");
        }
        audit(null,"EVIDENCE_COMPARED","CLAIM-"+c.getClaimId());
        return a;
    }
    private String strength(VerificationAttempt a) {
        if(count(a,"CONTRADICTION")>0) return "REVIEW_REQUIRED";
        return a.distinctMatches>=3?"STRONG":a.distinctMatches==2?"MODERATE":"WEAK";
    }
    private Summary summary(VerificationAttempt a) { return new Summary(a.strength,a.serialOutcome,a.markOutcome,a.accessoryOutcome,a.distinctMatches,a.criticalConflict,a.claimantVersionId,a.finderVersionId,a.reviewerId,a.reason); }
    public Summary assess(Long claimId, Assessment input) {
        Claim c=claim(claimId); User actor=reviewer(c); EvidencePair p=pair(c); VerificationAttempt previous=compare(c,p);
        if(!Objects.equals(input.claimantVersionId(),previous.claimantVersionId) || !Objects.equals(input.finderVersionId(),previous.finderVersionId))
            throw error(HttpStatus.CONFLICT,"Evidence changed. Reload the case before recording an assessment.");
        if(input.serialOutcome()==null || input.markOutcome()==null || input.accessoryOutcome()==null) throw error(HttpStatus.BAD_REQUEST,"All three outcomes are required.");
        VerificationAttempt a=new VerificationAttempt(); a.claimId=claimId; a.claimantVersionId=previous.claimantVersionId; a.finderVersionId=previous.finderVersionId;
        a.serialOutcome=input.serialOutcome().name(); a.markOutcome=input.markOutcome().name(); a.accessoryOutcome=input.accessoryOutcome().name();
        Set<String> groups=new HashSet<>();
        matchGroup(groups,input.serialOutcome(),input.serialClueGroup()); matchGroup(groups,input.markOutcome(),input.markClueGroup()); matchGroup(groups,input.accessoryOutcome(),input.accessoryClueGroup());
        a.distinctMatches=groups.size(); a.criticalConflict=input.criticalConflict() || a.serialOutcome.equals("CONTRADICTION");
        if(a.criticalConflict && count(a,"CONTRADICTION")==0) throw error(HttpStatus.BAD_REQUEST,"Critical conflict requires a contradiction.");
        a.reason=EvidenceData.reason(input.reason()); a.reviewerId=actor.getId(); a.strength=strength(a); attempts.save(a);
        hold(c,actor.getId());
        if(a.criticalConflict && c.getStatus()!=ClaimStatus.COMPLETED && c.getStatus()!=ClaimStatus.REJECTED && c.getStatus()!=ClaimStatus.WITHDRAWN) c.setStatus(ClaimStatus.ON_HOLD);
        audit(actor.getId(),"EVIDENCE_MANUAL_ASSESSMENT","CLAIM-"+claimId);
        return summary(a);
    }
    private void matchGroup(Set<String> groups, Outcome outcome, String group) {
        if(outcome==Outcome.MATCH) {
            String key=EvidenceData.clean(group,100);
            if(key==null) throw error(HttpStatus.BAD_REQUEST,"Each confirmed match needs a distinct private clue group.");
            groups.add(key.toLowerCase(Locale.ROOT));
        }
    }
    private void hold(Claim c,Long actor) {
        if(c.getStatus()==ClaimStatus.UNDER_REVIEW && c.isAwaitingSecondApproval()) {
            // The first review was of an older snapshot. Restart both approvals after any evidence change.
            c.setStatus(ClaimStatus.ON_HOLD);
            if(actor!=null) reviews.save(new AdminReview(c.getClaimId(),actor,"EVIDENCE_CHANGED",
                "UNDER_REVIEW","ON_HOLD","Evidence changed after the first approval; both reviewers must reassess.",null));
            audit(actor,"EVIDENCE_HOLD","CLAIM-"+c.getClaimId());
            return;
        }
        if(c.getStatus()!=ClaimStatus.APPROVED) return;
        custody.findByIdForUpdate(c.getFoundReportId()).ifPresent(s->{
            if(c.getClaimId().equals(s.getReservedByClaimId())) { s.setReservedByClaimId(null); s.setCustodyStatus(CustodyStatus.IN_CUSTODY); }
        });
        c.setStatus(ClaimStatus.ON_HOLD);
        if(actor!=null) reviews.save(new AdminReview(c.getClaimId(),actor,"EVIDENCE_CHANGED","APPROVED","ON_HOLD","Evidence requires fresh review.",null));
        operations.revokeActiveHandoverCredential(c.getClaimId(),"Evidence requires fresh review.");
        audit(actor,"EVIDENCE_HOLD","CLAIM-"+c.getClaimId());
    }
}
