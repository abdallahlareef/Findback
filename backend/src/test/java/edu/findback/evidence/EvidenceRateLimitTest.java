package edu.findback.evidence;
import org.junit.jupiter.api.Test;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;
class EvidenceRateLimitTest {
    @Test void throttleIsPerAccountAndRejectsExcessRequests() {
        var limiter=new EvidenceRateLimit(2,Clock.fixed(Instant.EPOCH,ZoneOffset.UTC));
        assertTrue(limiter.allow("owner"));assertTrue(limiter.allow("owner"));assertFalse(limiter.allow("owner"));assertTrue(limiter.allow("finder"));
    }
}
