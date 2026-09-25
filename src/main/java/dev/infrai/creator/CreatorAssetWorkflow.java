package dev.infrai.creator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.models.moderations.ModerationCreateParams;
import com.openai.models.moderations.ModerationCreateResponse;
import com.openai.models.moderations.ModerationImageUrlInput;
import com.openai.models.moderations.ModerationMultiModalInput;
import com.openai.models.moderations.ModerationTextInput;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
public final class CreatorAssetWorkflow {
    private final OpenAIClient moderation;
    private final HttpClient http;
    private final ObjectMapper json;
    private final ScreeningConfig config;
    private final int width;
    private final int height;

    public CreatorAssetWorkflow(OpenAIClient moderation, HttpClient http, ObjectMapper json,
                                ScreeningConfig config,
                                @Value("${creator.output-width}") int width,
                                @Value("${creator.output-height}") int height) {
        this.moderation = moderation;
        this.http = http;
        this.json = json;
        this.config = config;
        this.width = width;
        this.height = height;
    }

    public Result screenAndPrepare(byte[] image, String filename, String contentType, String caption,
                                   String submissionId) throws IOException, InterruptedException {
        String dataUrl = "data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(image);
        ModerationTextInput text = ModerationTextInput.builder().text(caption).build();
        ModerationImageUrlInput visual = ModerationImageUrlInput.builder().imageUrl(
                ModerationImageUrlInput.ImageUrl.builder().url(dataUrl).build()).build();
        ModerationCreateResponse response = moderation.moderations().create(
                ModerationCreateParams.builder().model("omni-moderation-latest")
                        .input(ModerationCreateParams.Input.ofModerationMultiModalArray(List.of(
                                ModerationMultiModalInput.ofText(text),
                                ModerationMultiModalInput.ofImageUrl(visual)))).build());
        boolean flagged = response.results().stream().anyMatch(result -> result.flagged());
        List<String> reasons = flagged ? List.of("policy_review") : List.of();
        PublicationDecision decision = PublicationDecision.from(flagged, reasons);
        if (decision.state() == PublicationDecision.State.QUARANTINED) {
            return new Result(submissionId, decision, null);
        }

        JsonNode transformed = resize(image, filename, submissionId);
        return new Result(submissionId, decision, transformed);
    }

    private JsonNode resize(byte[] image, String filename, String submissionId)
            throws IOException, InterruptedException {
        String boundary = "infrai-" + UUID.randomUUID();
        byte[] body = multipart(boundary, image, filename);
        HttpRequest request = HttpRequest.newBuilder(URI.create(config.getBaseUrl() + "/image/resize"))
                .timeout(Duration.ofSeconds(45))
                .header("Authorization", "Bearer " + config.getApiKey())
                .header("Idempotency-Key", submissionId + ":resize")
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .method("POST", HttpRequest.BodyPublishers.ofByteArray(body)).build();

        HttpResponse<String> response = sendWithBackoff(request);
        JsonNode envelope = json.readTree(response.body());
        if (!envelope.path("ok").asBoolean(false)) {
            JsonNode error = envelope.path("error");
            throw new InfraiCallException(error.path("code").asText("REQUEST_REJECTED"),
                    error.path("message").asText("Image request rejected"), response.statusCode());
        }
        if (response.statusCode() >= 500) {
            throw new IOException("Image service returned status " + response.statusCode());
        }
        return envelope.path("data");
    }

    private HttpResponse<String> sendWithBackoff(HttpRequest request) throws IOException, InterruptedException {
        for (int attempt = 0; ; attempt++) {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 429 || attempt >= config.getMaxRetries()) return response;
            long delay = response.headers().firstValue("Retry-After").map(CreatorAssetWorkflow::retryMillis)
                    .orElse(250L * (1L << attempt));
            Thread.sleep(delay);
        }
    }

    private static long retryMillis(String value) {
        try { return Math.max(0, Long.parseLong(value) * 1000); }
        catch (NumberFormatException ignored) { return 1000; }
    }

    private byte[] multipart(String boundary, byte[] image, String filename) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        filePart(out, boundary, "image", filename, image);
        field(out, boundary, "width", Integer.toString(width));
        field(out, boundary, "height", Integer.toString(height));
        field(out, boundary, "fit", "contain");
        field(out, boundary, "enlarge", "false");
        field(out, boundary, "format", "webp");
        field(out, boundary, "store", "true");
        out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private static void field(ByteArrayOutputStream out, String boundary, String name, String value) throws IOException {
        out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name
                + "\"\r\n\r\n" + value + "\r\n").getBytes(StandardCharsets.UTF_8));
    }

    private static void filePart(ByteArrayOutputStream out, String boundary, String name,
                                 String filename, byte[] bytes) throws IOException {
        out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name
                + "\"; filename=\"" + filename.replace("\"", "")
                + "\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(bytes);
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    public record Result(String submissionId, PublicationDecision publication, JsonNode asset) {}
}
