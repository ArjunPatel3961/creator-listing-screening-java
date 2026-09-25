package dev.infrai.creator;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
public final class CreatorSubmissionController {
    private final CreatorAssetWorkflow workflow;

    public CreatorSubmissionController(CreatorAssetWorkflow workflow) { this.workflow = workflow; }

    @PostMapping("/creator-submissions/screen")
    CreatorAssetWorkflow.Result screen(@RequestParam MultipartFile image,
                                       @RequestParam String caption,
                                       @RequestHeader("Idempotency-Key") String submissionId)
            throws IOException, InterruptedException {
        return workflow.screenAndPrepare(image.getBytes(), image.getOriginalFilename(),
                image.getContentType(), caption, submissionId);
    }

    @ExceptionHandler(InfraiCallException.class)
    ResponseEntity<Map<String, Object>> rejected(InfraiCallException error) {
        int status = error.status() >= 400 && error.status() < 500 ? error.status() : 502;
        return ResponseEntity.status(HttpStatus.valueOf(status))
                .body(Map.of("code", error.code(), "message", error.getMessage()));
    }
}
