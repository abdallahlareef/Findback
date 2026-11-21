package com.findback.module3.service;

import com.findback.module3.domain.Claim;
import com.findback.module3.repository.ClaimRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.Mockito.lenient;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ClaimReviewServiceTest {
    @Mock private com.findback.module3.ports.CrossModulePorts.ReportLookupPort reportPort;
    @Mock private com.findback.module3.ports.CrossModulePorts.OperationsPort opsPort;

    @Mock
    private ClaimRepository claimRepository;

    @InjectMocks
    private ClaimReviewService claimReviewService;

    @Test
    void shouldPreventDuplicateActiveClaims_FR06() {
        // Arrange: Use lenient() so Mockito doesn't crash if your service checks duplicates differently
        when(reportPort.getReportById(510L)).thenReturn(new com.findback.module3.ports.CrossModulePorts.ReportSummaryDto(510L,2012L,"LOST","EARBUDS","Earbuds","Library",true));
        when(reportPort.getReportById(610L)).thenReturn(new com.findback.module3.ports.CrossModulePorts.ReportSummaryDto(610L,99L,"FOUND","EARBUDS","Earbuds","Library",true));
        when(claimRepository.existsActiveClaimForPair(2012L,610L)).thenReturn(true);

        // Act & Assert
        var exception = assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> {
            claimReviewService.createClaim(2012L, 510L, 610L);
        });
        
        org.junit.jupiter.api.Assertions.assertEquals(org.springframework.http.HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    void shouldPreventStaffSelfReview_T09() {
        // Arrange
        Claim mockClaim = new Claim();
        mockClaim.setClaimantId(2012L); 
        mockClaim.setFoundReportId(610L);
        when(reportPort.getReportById(610L)).thenReturn(new com.findback.module3.ports.CrossModulePorts.ReportSummaryDto(610L,99L,"FOUND","EARBUDS","Earbuds","Library",true));
        when(claimRepository.findById(anyLong())).thenReturn(Optional.of(mockClaim));

        // Act & Assert
        var exception = assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> {
            claimReviewService.recordStaffDecision(2012L, 1L, "APPROVE", "Self approval", "Looks good");
        });
        
        org.junit.jupiter.api.Assertions.assertEquals(org.springframework.http.HttpStatus.FORBIDDEN, exception.getStatusCode());
    }
}
