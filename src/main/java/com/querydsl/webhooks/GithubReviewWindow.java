/*
 * Copyright 2016 The Querydsl Team.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.querydsl.webhooks;

import static java.time.ZonedDateTime.now;

import java.io.IOException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.kohsuke.github.GHCommitState;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;

import com.github.shredder121.gh_event_api.GHEventApiServer;
import com.github.shredder121.gh_event_api.handler.pull_request.PullRequestHandler;
import com.github.shredder121.gh_event_api.model.PullRequest;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;

/**
 * GitHub Review Window - A GitHub Webhook Implementation.
 *
 * <p>
 * When using Pull Requests, it's often necessary to review their contents.
 *
 * <p>
 * This piece of software adds a commit status to the PR's head commit, essentially blocking
 * The mergability until the duration of the review window has passed.
 *
 * <p>
 * usage:
 *   {@code java -Dduration=(defaultDurationString) [-Dduration.(labelName)=(durationString)] -jar gh-review-window-(version)-full.jar }
 *
 * @author Shredder121
 */
@EnableCaching
@SpringBootApplication
public class GithubReviewWindow {

    private static final Logger logger = LoggerFactory.getLogger(GithubReviewWindow.class);

    private final TaskScheduler taskScheduler = new ConcurrentTaskScheduler();

    public static void main(String... args) {
        GHEventApiServer.start(GithubReviewWindow.class, args);
    }

    @Autowired
    private GitHub gitHub;

    @Autowired
    private Environment environment;

    private final Map<String, ScheduledFuture<?>> asyncTasks = Maps.newConcurrentMap();

    @Bean
    @ConditionalOnProperty("startupRepos")
    public ApplicationListener<ContextRefreshedEvent> onStart() {
        return new StartupRepoProcessor(this, environment, repoQuery());
    }

    @Bean
    public GitHub gitHub() throws IOException {
        return GitHub.connect();
    }

    @Bean
    public Function<String, GHRepository> repoQuery() {
        return new RepositoryQuery(gitHub);
    }

    @Bean
    public BiFunction<GHRepository, Integer, Duration> reviewTimeQuery() {
        return new ReviewTimeQuery(environment);
    }

    @Bean
    public PullRequestHandler reviewWindowHandler() {
        return payload -> {
            PullRequest pullRequest = payload.getPullRequest();

            GHRepository repository = repoQuery().apply(payload.getRepository().getFullName());

            process(repository, pullRequest.getNumber(), pullRequest.getCreatedAt(), pullRequest.getHead().getSha());
        };
    }

    @VisibleForTesting
    protected static String makeHumanReadable(Duration duration) {
        StringBuilder output = new StringBuilder();
        duration = truncateAndAppend(duration, duration.toDays(), ChronoUnit.DAYS, "day", output);
        duration = truncateAndAppend(duration, duration.toHours(), ChronoUnit.HOURS, "hour", output);
        duration = truncateAndAppend(duration, duration.toMinutes(), ChronoUnit.MINUTES, "minute", output);
        duration = truncateAndAppend(duration, duration.getSeconds(), ChronoUnit.SECONDS, "second", output);
        return output.toString().trim();
    }

    private static Duration truncateAndAppend(Duration duration, long amount, ChronoUnit unit,
            String description, StringBuilder builder) {

        if (amount > 0) {
            builder.append(amount)
                    .append(' ')
                    .append(description)
                    .append(' ');
        }
        return duration.minus(amount, unit);
    }

    public void process(GHRepository repository, int number, ZonedDateTime creationTime, String sha) {
        Duration reviewTime = reviewTimeQuery().apply(repository, number);

        ZonedDateTime windowCloseTime = creationTime.plus(reviewTime);

        boolean windowPassed = now().isAfter(windowCloseTime);
        logger.info("creationTime({}) + reviewTime({}) = windowCloseTime({}), so windowPassed = {}",
                creationTime, reviewTime, windowCloseTime, windowPassed);

        if (windowPassed) {
            completeAndCleanUp(asyncTasks, repository, sha);
        } else {
            createPendingMessage(repository, sha, reviewTime);

            ScheduledFuture<?> scheduledTask = taskScheduler.schedule(
                    () -> completeAndCleanUp(asyncTasks, repository, sha),
                    Date.from(windowCloseTime.toInstant()));

            replaceCompletionTask(asyncTasks, scheduledTask, sha);
        }
    }

    private static void completeAndCleanUp(Map<String, ?> tasks, GHRepository repo, String sha) {
        createSuccessMessage(repo, sha);
        tasks.remove(sha);
    }

    private static void replaceCompletionTask(Map<String, ScheduledFuture<?>> tasks,
            ScheduledFuture<?> completionTask, String sha) {

        boolean interrupt = false;
        tasks.merge(sha, completionTask, (oldTask, newTask) -> {
            oldTask.cancel(interrupt);
            return newTask;
        });
    }

    private static void createSuccessMessage(GHRepository repo, String sha) {
        createStatusMessage(repo, sha, GHCommitState.SUCCESS, "The review window has passed");
    }

    private static void createPendingMessage(GHRepository repo, String sha, Duration reviewWindow) {
        createStatusMessage(repo, sha, GHCommitState.PENDING,
                "The " + makeHumanReadable(reviewWindow) + " review window has not passed");
    }

    private static void createStatusMessage(GHRepository repo, String sha, GHCommitState state, String message) {
        try {
            repo.createCommitStatus(sha, state, null, message, "review-window");
        } catch (IOException ex) {
            logger.warn("Exception updating status", ex);
        }
    }

}
