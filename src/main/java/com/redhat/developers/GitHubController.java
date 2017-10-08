package com.redhat.developers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ResourceUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriTemplate;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;

/**
 * A simple demo rest controller that calls GitHub API to perform some simple operations which will require authorizations
 * The idea of this controller is to demonstrate how to use the Kubernetes Secrets mounted as file
 * and use the token and username to call GitHub
 *
 * @author kameshsampath
 */
@Slf4j
@RestController
public class GitHubController {

    @Value("${demo.secretsPath}")
    private String secretsPath; 

    private final RestTemplate restTemplate;

    public GitHubController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Get the list of organizations that the user belongs to in GitHub
     */
    @GetMapping("/mygithuborgs")
    public ResponseEntity<String> listMyOrganizations() {
        try {

            final URI githubUserSecretsURI = ResourceUtils.getURL(secretsPath + "/github/user").toURI(); 
            final URI githubUserTokenSecretsURI = ResourceUtils.getURL(secretsPath + "/github/token").toURI(); 

            final byte[] encodedGithubUser = Files.readAllBytes(Paths.get(githubUserSecretsURI));
            final byte[] encodedGithubToken = Files.readAllBytes(Paths.get(githubUserTokenSecretsURI));

            String githubUser = sanitize(encodedGithubUser);

            String githubUserToken = sanitize(encodedGithubToken);

            String authHeader = String.format("%s:%s", githubUser, githubUserToken);

            log.info("Listing Organizations of user :{}", githubUser);

            String basicAutheader = Base64.getEncoder().encodeToString(authHeader.getBytes());

            log.info("Auth Header : {}", basicAutheader);

            ResponseEntity<String> response =
                restTemplate.exchange(buildHttpEntity("user/orgs", basicAutheader), String.class);

            return ResponseEntity
                .status(response.getStatusCode().value())
                .body(response.getBody());

        } catch (URISyntaxException e) {
            log.error("Error querying github", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());

        } catch (IOException e) {
            log.error("Error querying github", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    /**
     * A method to build {@link RequestEntity} by adding needed basic authentication headers
     *
     * @param path           - the github api path to call with out leading &quot;/&quot;
     * @param basicAutheader - the Basic Authorization Base64 string representation header value
     * @return {@link RequestEntity}
     */
    private RequestEntity<Void> buildHttpEntity(String path, String basicAutheader) {
        URI githubApiUri = new UriTemplate("https://api.github.com/{path}").expand(path);
        log.info("Calling API:{}", githubApiUri.toASCIIString());
        RequestEntity<Void> requestEntity =
            RequestEntity.get(githubApiUri)
                .header("Authorization", String.format(" Basic %s", basicAutheader))
                .accept(MediaType.parseMediaType("application/vnd.github.v3+json"))
                .build();

        return requestEntity;
    }

    /**
     * remove all new lines from the String
     *
     * @param strBytes - the string bytes where newline to be removed
     * @return sanitized string without newlines
     */
    private String sanitize(byte[] strBytes) {
        return new String(strBytes)
            .replace("\r", "")
            .replace("\n", "");
    }
}