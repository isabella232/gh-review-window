/*
 * Copyright 2015 The Querydsl Team.
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
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;

import org.kohsuke.github.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;

import com.github.shredder121.gh_event_api.GHEventApiServer;
import com.github.shredder121.gh_event_api.handler.pull_request.*;
import com.github.shredder121.gh_event_api.model.PullRequest;
import com.github.shredder121.gh_event_api.model.Ref;
import com.google.common.base.Throwables;
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
@SpringBootApplication
public class GithubReviewWindow {

    private static final Logger logger = LoggerFactory.getLogger(GithubReviewWindow.class);

    private static final GitHub github;

    private final TaskScheduler taskScheduler = new ConcurrentTaskScheduler();

    static {
        try {
            github = GitHub.connect();
        } catch (IOException ex) {
            throw Throwables.propagate(ex);
        }
    }

    public static void main(String... args) {
        GHEventApiServer.start(GithubReviewWindow.class, args);
    }

    @Bean
    public PullRequestHandler reviewWindowHandler(Environment environment) {
        Duration defaultReviewWindow = Duration.parse(environment.getRequiredProperty("duration")); //duration is the default window
        Map<String, ScheduledFuture<?>> asyncTasks = Maps.newConcurrentMap();

        return payload -> {
            PullRequest pullRequest = payload.getPullRequest();
            Ref head = pullRequest.getHead();

            try {
                GHRepository repository = github.getRepository(payload.getRepository().getFullName());
                Collection<GHLabel> labels = repository.getIssue(pullRequest.getNumber()).getLabels();

                Duration reviewTime = labels.stream().map(label -> "duration." + label.getName())   //for all duration.[label] properties
                        .map(environment::getProperty).filter(Objects::nonNull)                     //look for a Duration
                        .findFirst().map(Duration::parse).orElse(defaultReviewWindow);              //if none found, use the default window

                ZonedDateTime creationTime = pullRequest.getCreated_at();
                ZonedDateTime windowCloseTime = creationTime.plus(reviewTime);

                boolean windowPassed = now().isAfter(windowCloseTime);
                logger.info("creationTime({}) + reviewTime({}) = windowCloseTime({}), so windowPassed = {}",
                        creationTime, reviewTime, windowCloseTime, windowPassed);

                if (windowPassed) {
                    completeAndCleanUp(asyncTasks, repository, head);
                } else {
                    createPendingMessage(repository, head);

                    ScheduledFuture<?> scheduledTask = taskScheduler.schedule(
                            () -> completeAndCleanUp(asyncTasks, repository, head),
                            Date.from(windowCloseTime.toInstant()));

                    replaceCompletionTask(asyncTasks, scheduledTask, head);
                }
            } catch (IOException ex) {
                throw Throwables.propagate(ex);
            }
        };
    }

    private static void completeAndCleanUp(Map<String, ?> tasks, GHRepository repo, Ref head) {
        createSuccessMessage(repo, head);
        tasks.remove(head.getSha());
    }

    private static void replaceCompletionTask(Map<String, ScheduledFuture<?>> tasks,
            ScheduledFuture<?> completionTask, Ref head) {

        boolean interrupt = false;
        tasks.merge(head.getSha(), completionTask, (oldTask, newTask) -> {
            oldTask.cancel(interrupt);
            return newTask;
        });
    }

    private static void createSuccessMessage(GHRepository repo, Ref commit) {
        createStatusMessage(repo, commit, GHCommitState.SUCCESS, "The review window has passed");
    }

    private static void createPendingMessage(GHRepository repo, Ref commit) {
        createStatusMessage(repo, commit, GHCommitState.PENDING, "The review window has not passed");
    }

    private static void createStatusMessage(GHRepository repo, Ref commit, GHCommitState state, String message) {
        try {
            repo.createCommitStatus(commit.getSha(), state, null, message, "review-window");
        } catch (IOException ex) {
            logger.warn("Exception updating status", ex);
        }
    }

}
