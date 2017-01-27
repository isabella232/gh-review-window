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
import java.util.function.Function;

import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.springframework.cache.annotation.Cacheable;

import com.google.common.base.Throwables;

/**
 * The lookup function for Repositories using the GitHub API.
 *
 * @author Shredder121
 */
@Cacheable("function")
public class RepositoryQuery implements Function<String, GHRepository> {

    private final GitHub gitHub;

    public RepositoryQuery(GitHub gitHub) {
        this.gitHub = gitHub;
    }

    @Override
    public GHRepository apply(String fullName) {
        try {
            return gitHub.getRepository(fullName);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }
}
