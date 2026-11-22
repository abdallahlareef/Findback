package edu.findback;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import edu.findback.report.ReportRepository;
import edu.findback.user.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ReportHttpTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired ReportRepository reportRepository;
    @Autowired UserRepository userRepository;

    @BeforeEach
    void cleanDatabase() {
        reportRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void protectedReportEndpointsRejectUnauthenticatedRequests() throws Exception {
        mvc.perform(get("/api/reports/mine")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/reports/search")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/reports/1/candidates")).andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedUserCanCreateLostAndFoundReportsUsingSessionIdentity() throws Exception {
        MockHttpSession session = user("reporter");
        for (String type : new String[] {"LOST", "FOUND"}) {
            JsonNode response = createReport(session, type, "Electronics", "Library", "ACTIVE",
                    "2026-11-11", "JBL", "Tune Buds");
            assertEquals(type, response.get("type").asText());
            assertEquals("reporter@example.test", userRepository.findById(response.get("authorId").asLong())
                    .orElseThrow().getEmail());
        }
    }

    @Test
    void discoveryReturnsRankedSafeCandidatesAndUsesCorrectErrors() throws Exception {
        MockHttpSession owner = user("owner");
        JsonNode lost = createReport(owner, "LOST", "Electronics", "Library", "ACTIVE",
                "2026-11-11", "JBL", "Tune Buds");
        createReport(user("finderHigh"), "FOUND", "Electronics", "Library", "ACTIVE",
                "2026-11-11", "JBL", "Tune Buds");
        createReport(user("finderLow"), "FOUND", "Books", "Cafeteria", "ACTIVE",
                "2026-10-01", null, null);

        MvcResult candidateResult = mvc.perform(get("/api/reports/{id}/candidates", lost.get("id").asLong())
                        .session(owner))
                .andExpect(status().isOk()).andReturn();
        JsonNode candidates = objectMapper.readTree(candidateResult.getResponse().getContentAsString());
        assertEquals(2, candidates.size());
        assertTrue(candidates.get(0).get("score").asDouble() >= candidates.get(1).get("score").asDouble());
        assertSafeJson(candidateResult.getResponse().getContentAsString());

        JsonNode found = objectMapper.readTree(mvc.perform(get("/api/reports/search?type=FOUND").session(owner))
                .andReturn().getResponse().getContentAsString()).get(0);
        mvc.perform(get("/api/reports/{id}/candidates", found.get("reportId").asLong()).session(owner))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        mvc.perform(get("/api/reports/999999/candidates").session(owner))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));

        MockHttpSession anotherUser = user("intruder");
        mvc.perform(get("/api/reports/{id}/candidates", lost.get("id").asLong()).session(anotherUser))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void candidateSourceAndSearchParameterParsingUseBadRequest() throws Exception {
        MockHttpSession session = user("parser");
        mvc.perform(get("/api/reports/search?type=ALIEN").session(session)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/reports/search?eventDate=not-a-date").session(session)).andExpect(status().isBadRequest());
    }

    @Test
    void searchSupportsEveryFilterCombinationsCaseFoldingAndNewestFirst() throws Exception {
        MockHttpSession session = user("searcher");
        createReport(session, "FOUND", "Electronics", "Library", "ACTIVE", "2026-11-11", "JBL", "New");
        JsonNode closed = createReport(session, "FOUND", "Electronics", "Library", "ACTIVE", "2026-11-11", "Sony", "Old");
        var closedEntity = reportRepository.findById(closed.get("id").asLong()).orElseThrow();
        closedEntity.setStatus(edu.findback.report.ReportStatus.CLOSED);
        reportRepository.save(closedEntity);
        createReport(session, "LOST", "Books", "Cafeteria", "ACTIVE", "2026-10-01", null, null);

        assertSearchCount(session, "type=FOUND", 2);
        assertSearchCount(session, "category=books", 1);
        assertSearchCount(session, "category=ELECTRONICS", 2);
        assertSearchCount(session, "campusZone=library", 2);
        assertSearchCount(session, "campusZone=CAFETERIA", 1);
        assertSearchCount(session, "status=CLOSED", 1);
        assertSearchCount(session, "eventDate=2026-10-01", 1);
        assertSearchCount(session, "type=FOUND&category=electronics&campusZone=LIBRARY&status=ACTIVE&eventDate=2026-11-11", 1);

        MvcResult all = mvc.perform(get("/api/reports/search").session(session)).andExpect(status().isOk()).andReturn();
        JsonNode rows = objectMapper.readTree(all.getResponse().getContentAsString());
        assertEquals(3, rows.size());
        assertTrue(rows.get(0).get("createdAt").asText().compareTo(rows.get(1).get("createdAt").asText()) >= 0);
        assertTrue(rows.get(0).get("reportId").asLong() > rows.get(1).get("reportId").asLong());
        assertSafeJson(all.getResponse().getContentAsString());
    }

    private void assertSearchCount(MockHttpSession session, String query, int count) throws Exception {
        MvcResult result = mvc.perform(get("/api/reports/search?" + query).session(session))
                .andExpect(status().isOk()).andReturn();
        assertEquals(count, objectMapper.readTree(result.getResponse().getContentAsString()).size(), query);
        assertSafeJson(result.getResponse().getContentAsString());
    }

    private JsonNode createReport(MockHttpSession session, String type, String category, String zone,
            String status, String eventDate, String brand, String model) throws Exception {
        String payload = """
                {"type":"%s","category":"%s","brand":%s,"model":%s,"colour":"Black",
                 "campusZone":"%s","eventDate":"%s","safeDescription":"safe visible description",
                 "authorId":999999,"status":"%s"}
                """.formatted(type, category, brand == null ? "null" : "\"" + brand + "\"",
                model == null ? "null" : "\"" + model + "\"", zone, eventDate, status);
        MvcResult result = mvc.perform(post("/api/reports").session(session).contentType(APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated()).andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertNotEquals(999999, response.get("authorId").asLong());
        assertEquals("safe visible description", response.get("safeDescription").asText());
        return response;
    }

    private MockHttpSession user(String prefix) throws Exception {
        String email = prefix.toLowerCase() + "@example.test";
        String password = "test-password-123";
        mvc.perform(post("/api/auth/register").contentType(APPLICATION_JSON)
                        .content(("{\"name\":\"%s\",\"email\":\"%s\",\"password\":\"%s\"}").formatted(prefix, email, password)))
                .andExpect(status().isCreated());
        MvcResult login = mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                        .content(("{\"email\":\"%s\",\"password\":\"%s\"}").formatted(email, password)))
                .andExpect(status().isOk()).andReturn();
        HttpSession session = login.getRequest().getSession(false);
        assertNotNull(session);
        return (MockHttpSession) session;
    }

    private void assertSafeJson(String json) {
        String lower = json.toLowerCase();
        assertFalse(lower.contains("authorid"));
        assertFalse(lower.contains("email"));
        assertFalse(lower.contains("password"));
        assertFalse(lower.contains("private evidence"));
        assertFalse(lower.contains("serial number"));
    }
}
