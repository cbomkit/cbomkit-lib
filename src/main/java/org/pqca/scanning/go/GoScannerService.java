/*
 * CBOMkit-lib
 * Copyright (C) 2026 PQCA
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
package org.pqca.scanning.go;

import com.ibm.plugin.CryptoGoSensor;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.pqca.errors.ClientDisconnected;
import org.pqca.indexing.ProjectModule;
import org.pqca.progress.IProgressDispatcher;
import org.pqca.progress.ProgressMessage;
import org.pqca.progress.ProgressMessageType;
import org.pqca.scanning.CBOM;
import org.pqca.scanning.ScanResultDTO;
import org.pqca.scanning.ScannerService;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.internal.SensorContextTester;
import org.sonar.api.impl.utils.DefaultTempFolder;
import org.sonar.api.rule.RuleKey;
import org.sonar.go.converter.GoConverter;
import org.sonar.go.plugin.GoChecks;
import org.sonar.plugins.go.api.checks.GoCheck;

public final class GoScannerService extends ScannerService {

    private final GoConverter goConverter;

    public GoScannerService(@Nonnull File projectDirectory) {
        this(null, projectDirectory);
    }

    public GoScannerService(
            @Nullable IProgressDispatcher progressDispatcher, @Nonnull File projectDirectory) {
        super(progressDispatcher, projectDirectory);

        try {
            DefaultTempFolder tempFolder = new DefaultTempFolder(createDirectory());
            goConverter = new GoConverter(tempFolder.newDir());
        } catch (IOException ioe) {
            throw new IllegalStateException(ioe);
        }
    }

    @Nonnull
    private File createDirectory() throws IOException {
        // create directory
        final String folderId = UUID.randomUUID().toString().replace("-", "");
        final File tempDir = new File(this.projectDirectory + File.separator + folderId);
        if (tempDir.exists()) {
            throw new IOException("Temp dir already exists " + tempDir.getPath());
        }
        if (!tempDir.mkdirs()) {
            throw new IOException("Could not create temp dir" + tempDir.getPath());
        }
        return tempDir;
    }

    @Override
    public @Nonnull ScanResultDTO scan(@Nonnull List<ProjectModule> index)
            throws ClientDisconnected {
        LOGGER.info("Start scanning {} go projects", index.size());

        long scanTimeStart = System.currentTimeMillis();
        int counter = 1;
        int numberOfScannedLines = 0;
        int numberOfScannedFiles = 0;

        GoCheck visitor = new GoDetectionCollectionRule(this);
        GoChecks checks = new GoRuleChecks(visitor);
        final SensorContextTester sensorContext = SensorContextTester.create(projectDirectory);
        // Go scanner (CryptoGoSensor) reads files from context
        index.forEach(project -> project.inputFileList().forEach(sensorContext.fileSystem()::add));

        for (ProjectModule project : index) {
            numberOfScannedFiles += project.inputFileList().size();
            numberOfScannedLines +=
                    project.inputFileList().stream().mapToInt(InputFile::lines).sum();

            final String projectStr =
                    project.identifier() + " (" + counter + "/" + index.size() + ")";
            if (this.progressDispatcher != null) {
                this.progressDispatcher.send(
                        new ProgressMessage(
                                ProgressMessageType.LABEL, "Scanning go project " + projectStr));
            }
            LOGGER.info("Scanning go project {}", projectStr);

            CryptoGoSensor.execute((SensorContext) sensorContext, goConverter, checks);

            counter += 1;
        }
        LOGGER.info("Scanned {} go projects", index.size());

        return new ScanResultDTO(
                scanTimeStart,
                System.currentTimeMillis(),
                numberOfScannedLines,
                numberOfScannedFiles,
                this.getBOM().map(CBOM::new).orElse(null));
    }

    private class GoRuleChecks extends GoChecks {
        private final List<GoCheck> checks = new ArrayList<>();

        public GoRuleChecks(GoCheck check) {
            super(null);
            checks.add(check);
        }

        public List<GoCheck> all() {
            return this.checks;
        }

        @SuppressWarnings("null")
        public RuleKey ruleKey(GoCheck check) {
            return RuleKey.of("sonar-cryptography", "go-rule");
        }
    }
}
