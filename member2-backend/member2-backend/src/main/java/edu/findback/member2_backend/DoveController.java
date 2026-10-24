package edu.findback.member2_backend;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/member2/dove")
public class DoveController {

    public record CompareRequest(String ownerSuffix, String finderSuffix) {
    }

    public record CompareResponse(String outcome) {
    }

    @PostMapping("/compare")
    public CompareResponse compare(@RequestBody CompareRequest request) {
        String owner = normalize(request.ownerSuffix());
        String finder = normalize(request.finderSuffix());

        if (!isValidSuffix(owner) || !isValidSuffix(finder)) {
            return new CompareResponse("UNKNOWN");
        }

        if (owner.equals(finder)) {
            return new CompareResponse("MATCH");
        }

        return new CompareResponse("CONTRADICTION");
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private boolean isValidSuffix(String value) {
        return value.matches("[A-Z0-9]{4}");
    }
}