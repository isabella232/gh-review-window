package com.querydsl.webhooks;

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.is;

import java.util.concurrent.CountDownLatch;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;
import org.junit.runner.RunWith;
import org.springframework.boot.test.*;
import org.springframework.core.env.Environment;
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

    @Test
    public void TestServerShouldStart() {
        String pingPayload
                = "{\n"
                + "    \"zen\": \"all good!\"\n"
                + "}";

        given().header("X-GitHub-Event", "ping")
                .and().body(pingPayload).with().contentType(JSON)
        .expect().statusCode(200)
                .and().content(is("all good!"))
        .when().post();
    }

    @Test
    public void TestServerShouldStartAndAcceptPullRequestEvents() throws InterruptedException {
        String pullRequestPayload
                = "{\n"
                + "    \"action\": \"unlabeled\",\n"
                + "    \"number\": 1,\n"
                + "    \"pull_request\": {},\n"
                + "    \"label\": {},\n"
                + "    \"repository\": {},\n"
                + "    \"sender\": {}\n"
                + "}";

        given().header("X-GitHub-Event", "pull_request")
                .and().body(pullRequestPayload).with().contentType(JSON)
        .expect().statusCode(200)
        .when().post();
        completion.await();
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
