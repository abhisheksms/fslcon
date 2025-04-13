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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
        ParsedPrompt parsed = parsePrompt(prompt);
        String key = parsed.key;
        String value = parsed.value;

        Path yamlPath = Paths.get(repoPath, filePath);
        Yaml yaml = new Yaml();

        try (InputStream in = Files.newInputStream(yamlPath)) {
            Map<String, Object> data = yaml.load(in);

            if (data == null || !data.containsKey(key)) {
                throw new RuntimeException("Key not found in app.yml: " + key);
            }

            switch (parsed.type) {
                case UPDATE -> data.put(key, value);
                case ADD_TO_LIST -> {
                    Object existing = data.get(key);
                    if (existing instanceof List<?> list) {
                        List<String> updatedList = list.stream()
                                .map(Object::toString)
                                .map(v -> v.replace("\"", "")) // clean up existing values
                                .collect(Collectors.toList());

                        updatedList.add(value.replace("\"", "")); // avoid nested quotes
                        data.put(key, updatedList);
                    }
                    else {
                        throw new RuntimeException("Key " + key + " is not a list.");
                    }
                }
            }

            DumperOptions options = new DumperOptions();
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.FLOW);
            options.setPrettyFlow(true);
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

    private ParsedPrompt parsePrompt(String prompt) {
        // Match: update key to value
        Pattern updatePattern = Pattern.compile("update\\s+(\\w+)\\s+to\\s+(.+)", Pattern.CASE_INSENSITIVE);

        // Match: add value to key
        Pattern addPattern = Pattern.compile("add\\s+(.+?)\\s+to\\s+(\\w+)", Pattern.CASE_INSENSITIVE);

        Matcher updateMatcher = updatePattern.matcher(prompt);
        Matcher addMatcher = addPattern.matcher(prompt);

        if (updateMatcher.find()) {
            return new ParsedPrompt(updateMatcher.group(1), updateMatcher.group(2), PromptType.UPDATE);
        } else if (addMatcher.find()) {
            return new ParsedPrompt(addMatcher.group(2), addMatcher.group(1), PromptType.ADD_TO_LIST);
        }

        throw new IllegalArgumentException("Invalid prompt format.");
    }

    private enum PromptType {
        UPDATE,
        ADD_TO_LIST
    }

    private static class ParsedPrompt {
        String key;
        String value;
        PromptType type;

        public ParsedPrompt(String key, String value, PromptType type) {
            this.key = key;
            this.value = value;
            this.type = type;
        }
    }

}


/**
 *  - update version to 2.0.1
 *  - add value3 to releaseRefs
 */
