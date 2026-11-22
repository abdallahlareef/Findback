package edu.findback.evidence;

import edu.findback.BackendApplication;
import edu.findback.user.*;
import edu.findback.report.*;
import com.findback.module3.service.ClaimReviewService;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;

class EvidenceRestartTest {
    @TempDir Path temp;
    ConfigurableApplicationContext start() {
        return new SpringApplicationBuilder(BackendApplication.class).run(
            "--server.port=0", "--spring.datasource.url=jdbc:h2:file:"+temp.resolve("evidence").toString().replace('\\','/')+";DB_CLOSE_ON_EXIT=FALSE",
            "--spring.datasource.driver-class-name=org.h2.Driver", "--spring.datasource.username=sa", "--spring.datasource.password=",
            "--spring.jpa.hibernate.ddl-auto=update", "--spring.devtools.restart.enabled=false", "--spring.main.banner-mode=off");
    }
    void as(String email) { SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(email,"",java.util.List.of())); }
    User user(ConfigurableApplicationContext app,String email,Role role) {
        User u=new User();u.setEmail(email);u.setName(email);u.setRole(role);u.setPasswordHash("{noop}syntheticpassword");return app.getBean(UserRepository.class).save(u);
    }
    Long report(ConfigurableApplicationContext app,User actor,ReportType type) {
        Report r=new Report();r.setAuthor(actor);r.setType(type);r.setCategory("EARBUDS");r.setColour("Black");r.setCampusZone("Library");r.setEventDate(LocalDate.now());return app.getBean(ReportRepository.class).save(r).getId();
    }
    @Test void versionsAndPendingRequestSurviveApplicationRestart() {
        Long claimId;String requestId=UUID.randomUUID().toString();
        try(var app=start()) {
            User owner=user(app,"restart-owner@example.test",Role.USER), finder=user(app,"restart-finder@example.test",Role.USER);
            user(app,"restart-staff@example.test",Role.ADMIN);
            Long lost=report(app,owner,ReportType.LOST),found=report(app,finder,ReportType.FOUND);
            var service=app.getBean(EvidenceService.class);
            as(owner.getEmail());service.baseline(lost,new EvidenceData("A82F","Synthetic mark",null));
            as(finder.getEmail());service.baseline(found,new EvidenceData("A82F","Synthetic mark",null));
            claimId=app.getBean(ClaimReviewService.class).createClaim(owner.getId(),lost,found).getClaimId();
            as("restart-staff@example.test");service.requestSupplement(claimId,requestId,"Additional independent details requested");
        } finally { SecurityContextHolder.clearContext(); }
        try(var app=start()) {
            as("restart-owner@example.test");var service=app.getBean(EvidenceService.class);var state=service.ownClaim(claimId);
            assertEquals(requestId,state.pendingRequestId());assertEquals(1,state.submissionsUsed());assertEquals("A82F",state.versions().get(0).evidence().serialSuffix());
            var updated=service.supplement(claimId,requestId,new EvidenceData("A82F","Synthetic supplement",null),"Remembered a detail");
            assertEquals(2,updated.submissionsUsed());assertEquals(2,updated.versions().size());assertNull(updated.pendingRequestId());
        } finally { SecurityContextHolder.clearContext(); }
    }
}
