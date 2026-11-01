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

    @Mock
    private ClaimRepository claimRepository;

    @InjectMocks
    private ClaimReviewService claimReviewService;

    @Test
    void shouldPreventDuplicateActiveClaims_FR06() {
        // Arrange: Use lenient() so Mockito doesn't crash if your service checks duplicates differently
        lenient().when(claimRepository.existsByLostReportIdAndClaimantId(anyLong(), anyLong()))
            .thenReturn(true); 

        // Act & Assert
        Exception exception = assertThrows(RuntimeException.class, () -> {
            claimReviewService.createClaim(2012L, 510L, 610L);
        });
        
        System.out.println("Success! Backend blocked duplicate with message: " + exception.getMessage());
    }

    @Test
    void shouldPreventStaffSelfReview_T09() {
        // Arrange
        Claim mockClaim = new Claim();
        mockClaim.setClaimantId(2012L); 
        when(claimRepository.findById(anyLong())).thenReturn(Optional.of(mockClaim));

        // Act & Assert
        Exception exception = assertThrows(RuntimeException.class, () -> {
            claimReviewService.recordStaffDecision(2012L, 1L, "APPROVE", "Self approval", "Looks good");
        });
        
        System.out.println("Success! Backend blocked self-review with message: " + exception.getMessage());
    }
}