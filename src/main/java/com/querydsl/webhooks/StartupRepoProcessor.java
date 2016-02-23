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

import java.io.IOException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.env.Environment;

/**
 * {@code ApplicationListener} that processes the pull requests of a given repo on startup of the Spring context.
 *
 * @author Shredder121
 */
public class StartupRepoProcessor implements ApplicationListener<ContextRefreshedEvent> {

    private static final Logger logger = LoggerFactory.getLogger(StartupRepoProcessor.class);

    private final GithubReviewWindow reviewWindow;
    private final Environment environment;
    private final GitHub gitHub;

    @Autowired
    public StartupRepoProcessor(GithubReviewWindow reviewWindow, Environment environment, GitHub gitHub) {
        this.reviewWindow = reviewWindow;
        this.environment = environment;
        this.gitHub = gitHub;
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        try {
            String startupRepo = environment.getProperty("startupRepo", String.class);
            GHRepository repo = gitHub.getRepository(startupRepo);
            repo.queryPullRequests().state(GHIssueState.OPEN).list()
                    .forEach(pr -> {
                        try {
                            reviewWindow.process(repo, pr.getNumber(),
                                    ZonedDateTime.ofInstant(pr.getCreatedAt().toInstant(), ZoneOffset.UTC),
                                    pr.getHead().getSha());
                        } catch (IOException ex) {
                            logger.error("Failure", ex);
                        }
                    });
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
}
