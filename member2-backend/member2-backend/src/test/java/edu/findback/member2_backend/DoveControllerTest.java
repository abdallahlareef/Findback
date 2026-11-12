package edu.findback.member2_backend;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DoveControllerTest {

    private final DoveController controller = new DoveController();

    @Test
    void matchingSuffixesReturnMatch() {
        var response = controller.compare(
                new DoveController.CompareRequest("A82F", "A82F"));

        assertEquals("MATCH", response.outcome());
    }

    @Test
    void differentSuffixesReturnContradiction() {
        var response = controller.compare(
                new DoveController.CompareRequest("A82F", "B91K"));

        assertEquals("CONTRADICTION", response.outcome());
    }

    @Test
    void missingSuffixReturnsUnknown() {
        var response = controller.compare(
                new DoveController.CompareRequest("A82F", ""));

        assertEquals("UNKNOWN", response.outcome());
    }

    @Test
    void invalidSuffixReturnsUnknown() {
        var response = controller.compare(
                new DoveController.CompareRequest("AB", "A82F"));

        assertEquals("UNKNOWN", response.outcome());
    }

    @Test
    void spacesAndLowercaseAreNormalized() {
        var response = controller.compare(
                new DoveController.CompareRequest(" a82f ", "A82F"));

        assertEquals("MATCH", response.outcome());
    }

    @Test
    void missingRequestReturnsUnknown() {
        var response = controller.compare(null);

        assertEquals("UNKNOWN", response.outcome());
    }
}