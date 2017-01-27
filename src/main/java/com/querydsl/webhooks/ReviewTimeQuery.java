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
import java.time.Duration;
import java.util.Objects;
import java.util.function.BiFunction;

import org.kohsuke.github.GHRepository;
import org.springframework.core.env.Environment;

import com.google.common.base.Throwables;

/**
 * The calculation function that computes the review time.
 *
 * @author Shredder121
 */
public class ReviewTimeQuery implements BiFunction<GHRepository, Integer, Duration> {

    private final Environment environment;
    private final Duration defaultReviewWindow;

    public ReviewTimeQuery(Environment environment) {
        this.environment = environment;
        defaultReviewWindow = Duration.parse(environment.getRequiredProperty("duration"));      // duration is the default window
    }

    @Override
    public Duration apply(GHRepository repo, Integer id) {
        try {
            return repo.getIssue(id).getLabels().stream()
                    .map(label -> "duration." + label.getName())                                // for all duration.[label] properties
                    .map(environment::getProperty).filter(Objects::nonNull)                     // look for a Duration
                    .findFirst().map(Duration::parse).orElse(defaultReviewWindow);              // if none found, use the default window
        } catch (IOException ex) {
            throw Throwables.propagate(ex);
        }
    }
}
