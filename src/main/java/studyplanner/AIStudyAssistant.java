package studyplanner;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

// wraps the Anthropic Messages API; all AI output is advisory — app works without a key
// set ANTHROPIC_API_KEY environment variable to enable AI features
public class AIStudyAssistant {

    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String MODEL   = "claude-haiku-4-5-20251001";

    private final String     apiKey;
    private final HttpClient httpClient;

    public AIStudyAssistant(String apiKey) {
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public boolean isAvailable() {
        return apiKey != null && !apiKey.isBlank();
    }

    // ── 1. Study roadmap ───────────────────────────────────────────────────────

    // returns a 4-phase learning roadmap: basics → core → advanced → review
    public String getStudyRoadmap(String subject) {
        if (!isAvailable()) return unavailableMsg();
        String prompt =
                "Create a structured study roadmap for the subject: \"" + subject + "\". " +
                "Output exactly 4 phases labeled: " +
                "Phase 1: Foundations, Phase 2: Core Concepts, Phase 3: Advanced Topics, " +
                "Phase 4: Review and Practice. " +
                "For each phase list 3-4 specific topics as bullet points, " +
                "ordered from most important to least important. " +
                "Be concise and specific — name actual topics, not generic advice.";
        return callApi(prompt, 550);
    }

    public CompletableFuture<String> getStudyRoadmapAsync(String subject) {
        return async(() -> getStudyRoadmap(subject));
    }

    /**
     * Richer roadmap that incorporates actual material file names.
     * Suggests reading order and how many files to cover per study phase.
     */
    public String getStudyRoadmapWithMaterials(
            String subject, List<String> fileNames, long totalPages) {
        if (!isAvailable()) return unavailableMsg();

        StringBuilder sb = new StringBuilder();
        sb.append("I am studying \"").append(subject).append("\".\n");
        sb.append("I have ").append(fileNames.size()).append(" study material file(s)");
        if (totalPages > 0) sb.append(" totalling approximately ").append(totalPages).append(" pages");
        sb.append(".\n\nMy materials:\n");
        int limit = Math.min(fileNames.size(), 25);
        for (int i = 0; i < limit; i++) {
            sb.append("  ").append(i + 1).append(". ").append(fileNames.get(i)).append("\n");
        }
        if (fileNames.size() > 25) {
            sb.append("  ... and ").append(fileNames.size() - 25).append(" more file(s)\n");
        }
        sb.append("\nBased on these materials, create a structured 4-phase study roadmap:\n");
        sb.append("Phase 1: Foundations, Phase 2: Core Concepts, Phase 3: Advanced Topics, ");
        sb.append("Phase 4: Review and Practice.\n");
        sb.append("For each phase:\n");
        sb.append("- List 3-4 specific topics as bullet points\n");
        sb.append("- Recommend which files from the list above to read (by number or name)\n");
        sb.append("- Suggest roughly how many files or pages to cover\n");
        sb.append("Be concise and specific. Base recommendations on the actual file names provided.");
        return callApi(sb.toString(), 750);
    }

    public CompletableFuture<String> getStudyRoadmapWithMaterialsAsync(
            String subject, List<String> fileNames, long totalPages) {
        return async(() -> getStudyRoadmapWithMaterials(subject, fileNames, totalPages));
    }

    // ── 2. Plan optimization ───────────────────────────────────────────────────

    // analyzes scheduled sessions and gives 3 actionable improvement tips
    public String getPlanOptimization(List<StudySession> sessions) {
        if (!isAvailable()) return unavailableMsg();
        StringBuilder sb = new StringBuilder(
                "Here is a student's current study schedule:\n");
        int limit = Math.min(sessions.size(), 20);
        for (int i = 0; i < limit; i++) {
            StudySession s = sessions.get(i);
            sb.append(String.format("  %s  %.1fh  %s%n",
                    s.getDate(), s.getHoursPlanned(), s.getStudyItemTitle()));
        }
        if (sessions.size() > 20) {
            sb.append("  ... (").append(sessions.size() - 20).append(" more sessions)\n");
        }
        sb.append("\nProvide exactly 3 numbered, specific, actionable suggestions to " +
                "improve this schedule. Focus on: realistic pacing, avoiding cramming, " +
                "and effective study habits for this workload.");
        return callApi(sb.toString(), 380);
    }

    public CompletableFuture<String> getPlanOptimizationAsync(List<StudySession> sessions) {
        return async(() -> getPlanOptimization(sessions));
    }

    // ── shared infrastructure ──────────────────────────────────────────────────

    String callApi(String prompt, int maxTokens) {
        try {
            String body = buildRequestBody(prompt, maxTokens);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(30))
                    .build();
            HttpResponse<String> resp =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() == 200
                    ? parseTextFromResponse(resp.body())
                    : "[AI error] HTTP " + resp.statusCode();
        } catch (Exception e) {
            return "[AI error] " + e.getMessage();
        }
    }

    private CompletableFuture<String> async(java.util.function.Supplier<String> task) {
        return CompletableFuture.supplyAsync(task)
                .orTimeout(35, TimeUnit.SECONDS)
                .exceptionally(ex -> "[AI timeout/error] " + ex.getMessage());
    }

    private String unavailableMsg() {
        return "[AI unavailable] Set the ANTHROPIC_API_KEY environment variable to enable AI features.";
    }

    private String buildRequestBody(String prompt, int maxTokens) {
        String escaped = prompt
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
        return String.format(
                "{\"model\":\"%s\",\"max_tokens\":%d," +
                "\"messages\":[{\"role\":\"user\",\"content\":\"%s\"}]}",
                MODEL, maxTokens, escaped);
    }

    private String parseTextFromResponse(String json) {
        int typeIdx = json.indexOf("\"type\":\"text\"");
        if (typeIdx == -1) return "[AI] No text in response.";
        int textKeyIdx = json.indexOf("\"text\":", typeIdx);
        if (textKeyIdx == -1) return "[AI] Parse error.";
        int quoteStart = json.indexOf('"', textKeyIdx + 7);
        if (quoteStart == -1) return "[AI] Parse error.";

        StringBuilder sb = new StringBuilder();
        int i = quoteStart + 1;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                char next = json.charAt(i + 1);
                switch (next) {
                    case 'n'  -> { sb.append('\n'); i += 2; continue; }
                    case '"'  -> { sb.append('"');  i += 2; continue; }
                    case '\\' -> { sb.append('\\'); i += 2; continue; }
                    case 't'  -> { sb.append('\t'); i += 2; continue; }
                }
            }
            if (c == '"') break;
            sb.append(c);
            i++;
        }
        return sb.toString().trim();
    }
}
