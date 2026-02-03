/*
 * CBOMkit-lib
 * Copyright (C) 2024 PQCA
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.pqca.indexing.go;

import jakarta.annotation.Nonnull;
import java.io.File;
import java.util.List;
import javax.annotation.Nullable;
import org.pqca.indexing.IBuildType;
import org.pqca.indexing.IndexingService;
import org.pqca.progress.IProgressDispatcher;

public final class GoIndexService extends IndexingService {

    public GoIndexService(@Nonnull File baseDirectory) {
        this(null, baseDirectory);
    }

    public GoIndexService(
            @Nullable IProgressDispatcher progressDispatcher, @Nonnull File baseDirectory) {
        super(progressDispatcher, baseDirectory, "go", ".go");
        this.setExcludePatterns(null);
    }

    public void setExcludePatterns(@Nullable List<String> patterns) {
        if (patterns == null) {
            super.setExcludePatterns(List.of("test/", "_test.go$"));
        } else {
            super.setExcludePatterns(patterns);
        }
    }

    @Override
    public boolean isModule(@Nonnull File directory) {
        if (!directory.isDirectory()) {
            return false;
        }
        for (String buildFileName :
                List.of("go.mod", "pom.xml", "build.gradle", "build.gradle.kts")) {
            final File file = new File(directory, buildFileName);
            if (file.exists() && file.isFile()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isLanguageFile(@Nonnull File file) {
        return file.getName().endsWith(languageFileExtension) || file.getName().equals("go.mod");
    }

    @Override
    public String getLanguage(File file) {
        if (file.getName().equals("go.mod")) {
            return "gomod";
        }
        return this.languageIdentifier;
    }

    @Override
    @Nullable public IBuildType getMainBuildTypeFromModuleDirectory(@Nonnull File directory) {
        if (!directory.isDirectory()) {
            return null;
        }
        // go
        final File goModFile = new File(directory, "go.mod");
        if (goModFile.exists() && goModFile.isFile()) {
            return GoBuildType.GO;
        }
        // maven
        final File pomFile = new File(directory, "pom.xml");
        if (pomFile.exists() && pomFile.isFile()) {
            return GoBuildType.MAVEN;
        }
        // gradle
        for (String gradleFileName : List.of("build.gradle", "build.gradle.kts")) {
            final File gradleFile = new File(directory, gradleFileName);
            if (gradleFile.exists() && gradleFile.isFile()) {
                return GoBuildType.GRADLE;
            }
        }
        return null;
    }
}
