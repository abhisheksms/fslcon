package org.group;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class PromptController {


    @Autowired
    private GitService gitService;

    @PostMapping("/prompt")
    public ResponseEntity<String> handlePrompt(@RequestBody PromptRequest request) {
        try {
            gitService.processPrompt(request.getPrompt());
            return ResponseEntity.ok("PR created successfully!");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    @GetMapping("/callback")
    public String dummyCallback() {
        return "Callback received. You may close this tab.";
    }
}
