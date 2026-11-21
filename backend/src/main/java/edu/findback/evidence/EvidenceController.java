package edu.findback.evidence;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/evidence")
public class EvidenceController {
    private final EvidenceService service;
    public EvidenceController(EvidenceService service) { this.service=service; }
    public record Correction(String requestId, String reason, EvidenceData evidence) {}
    public record RequestSupplement(String requestId, String reason) {}
    @PostMapping("/reports/{id}/baseline") public EvidenceService.VersionView baseline(@PathVariable Long id,@RequestBody EvidenceData data) { return service.baseline(id,data); }
    @GetMapping("/reports/{id}") public List<EvidenceService.VersionView> ownReport(@PathVariable Long id) { return service.ownReport(id); }
    @PostMapping("/reports/{id}/finder-corrections") public EvidenceService.VersionView finder(@PathVariable Long id,@RequestBody Correction body) { return service.finderCorrection(id,body.requestId(),required(body.evidence()),body.reason()); }
    @GetMapping("/claims/{id}") public EvidenceService.PairView ownClaim(@PathVariable Long id) { return service.ownClaim(id); }
    @PostMapping("/claims/{id}/supplements") public EvidenceService.PairView supplement(@PathVariable Long id,@RequestBody Correction body) { return service.supplement(id,body.requestId(),required(body.evidence()),body.reason()); }
    @GetMapping("/staff/claims/{id}") public EvidenceService.StaffView staff(@PathVariable Long id) { return service.staffView(id); }
    @PostMapping("/staff/claims/{id}/supplement-requests") public EvidenceService.PairView request(@PathVariable Long id,@RequestBody RequestSupplement body) { return service.requestSupplement(id,body.requestId(),body.reason()); }
    @PostMapping("/staff/claims/{id}/assessments") public EvidenceService.Summary assess(@PathVariable Long id,@RequestBody EvidenceService.Assessment body) { return service.assess(id,body); }
    @PostMapping("/staff/claims/{id}/corrections/{side}") public EvidenceService.VersionView correct(@PathVariable Long id,@PathVariable String side,@RequestBody Correction body) { return service.staffCorrection(id,side,body.requestId(),required(body.evidence()),body.reason()); }
    private EvidenceData required(EvidenceData data) {
        if(data==null) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"Evidence is required.");
        return data;
    }
}
