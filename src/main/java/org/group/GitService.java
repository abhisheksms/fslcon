package org.group;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.io.Writer;
import java.util.Map;

@Slf4j
@Service
public class GitService {

    @Value("${github.username}")
    private String username;

    @Value("${github.token}")
    private String token;

    @Value("${github.repo}")
    private String repoName;

    private final String repoPath = "/Users/abhisheksms27/Desktop/study/fslcon"; // Adjust as needed
    private final String branchName = "auto-pr-" + Instant.now().toEpochMilli();
    private final String filePath = "src/main/resources/app.yml";

    public void processPrompt(String prompt) throws Exception {
        editYamlFile(prompt);
        commitAndPushChanges();
        createPullRequest();
    }

    private void editYamlFile(String prompt) throws IOException {
        // Simulate parsing prompt → key + value (you can later plug GPT here)
        String key = "newKey";         // Extract from prompt
        String newValue = "updatedValue"; // You can customize this

        Path yamlPath = Paths.get(repoPath, filePath);
        Yaml yaml = new Yaml();

        // Load existing YAML
        try (InputStream in = Files.newInputStream(yamlPath)) {
            Map<String, Object> data = yaml.load(in);

            // Update key if exists
            if (data.containsKey(key)) {
                data.put(key, newValue);
            } else {
                throw new RuntimeException("Key not found: " + key);
            }

            // Write back updated YAML
            DumperOptions options = new DumperOptions();
            options.setPrettyFlow(true);
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            yaml = new Yaml(options);

            try (Writer writer = Files.newBufferedWriter(yamlPath)) {
                yaml.dump(data, writer);
            }
        }
    }


    private void commitAndPushChanges() throws Exception {
        Git git = Git.open(new File(repoPath));

        git.checkout().setCreateBranch(true).setName(branchName).call();
        git.add().addFilepattern(".").call();
        git.commit().setMessage("Auto-edit YAML from prompt").call();
        git.push()
                .setCredentialsProvider(new UsernamePasswordCredentialsProvider(username, token))
                .call();
    }

    private void createPullRequest() throws IOException, InterruptedException {
        String json = """
            {
              "title": "Auto PR from prompt",
              "head": "%s",
              "base": "main",
              "body": "This PR was generated from a prompt."
            }
        """.formatted(branchName);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.github.com/repos/" + username + "/" + repoName + "/pulls"))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 400) {
            throw new RuntimeException("Failed to create PR: " + response.body());
        }

        log.info("PR created successfully: {}", response.body());
    }
}
