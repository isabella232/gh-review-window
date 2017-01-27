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
import java.util.function.Function;

import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.env.Environment;

/**
 * {@code ApplicationListener} that processes the pull requests of a given repo on startup of the Spring context.
 *
 * @author Shredder121
 */
public class StartupRepoProcessor implements ApplicationListener<ContextRefreshedEvent> {

    private final GithubReviewWindow reviewWindow;
    private final Environment environment;
    private final Function<String, GHRepository> repoQuery;

    public StartupRepoProcessor(GithubReviewWindow reviewWindow, Environment environment, Function<String, GHRepository> repoQuery) {
        this.reviewWindow = reviewWindow;
        this.environment = environment;
        this.repoQuery = repoQuery;
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        String[] startupRepos = environment.getProperty("startupRepos", String[].class);
        for (String startupRepo : startupRepos) {
            GHRepository repo = repoQuery.apply(startupRepo);
            repo.queryPullRequests().state(GHIssueState.OPEN).list()
                    .forEach(pr -> reviewWindow.process(repo,
                            pr.getNumber(),
                            creationTime(pr),
                            pr.getHead().getSha())
                    );
        }
    }

    private static ZonedDateTime creationTime(GHPullRequest pr) {
        try {
            return ZonedDateTime.ofInstant(pr.getCreatedAt().toInstant(), ZoneOffset.UTC);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
}
