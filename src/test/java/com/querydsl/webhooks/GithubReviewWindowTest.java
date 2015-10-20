package com.querydsl.webhooks;

import static org.hamcrest.CoreMatchers.is;

import java.util.concurrent.CountDownLatch;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;
import org.junit.runner.RunWith;
import org.springframework.boot.test.*;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.github.shredder121.gh_event_api.GHEventApiServer;
import com.github.shredder121.gh_event_api.handler.pull_request.PullRequestHandler;

@RunWith(SpringJUnit4ClassRunner.class)
@WebIntegrationTest("spring.main.show-banner=false")
@SpringApplicationConfiguration(classes = {GithubReviewWindowTest.class, GHEventApiServer.class})
@DirtiesContext
public class GithubReviewWindowTest extends GithubReviewWindow {

    private static final CountDownLatch completion = new CountDownLatch(1);

    @Rule
    public final ErrorCollector errorCollector = new ErrorCollector();

    private final TestRestTemplate restTemplate = new TestRestTemplate();

    @Test
    public void TestServerShouldStart() {
        ResponseEntity<String> pingResponse = post("ping", "{\"zen\": \"all good!\"}");
        errorCollector.checkThat("Server should be started",
                pingResponse.getStatusCode(), is(HttpStatus.OK));
        errorCollector.checkThat("Server should be started",
                pingResponse.getBody(), is("all good!"));
    }

    @Test
    public void TestServerShouldStartAndAcceptPullRequests() throws InterruptedException {
        ResponseEntity<String> pullRequestResponse = post("pull_request",
                "{\n" +
                "    \"action\": \"unlabeled\",\n" +
                "    \"number\": 1,\n" +
                "    \"pull_request\": {},\n" +
                "    \"label\": {},\n" +
                "    \"repository\": {},\n" +
                "    \"sender\": {}\n" +
                "}");
        errorCollector.checkThat("Server should be started and accepting pull_request events",
                pullRequestResponse.getStatusCode(), is(HttpStatus.OK));
        completion.await();
    }

    private ResponseEntity<String> post(String event, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-GitHub-Event", event);
        headers.setContentType(MediaType.APPLICATION_JSON);

        return restTemplate.postForEntity("http://127.0.0.1:8080", new HttpEntity<>(body, headers), String.class);
    }

    @Override
    public PullRequestHandler reviewWindowHandler(Environment environment) {
        // Compile time assertion that the handler bean method is invoked
        return payload -> {
            // don't actually handle the payload
            completion.countDown();
        };
    }

}
