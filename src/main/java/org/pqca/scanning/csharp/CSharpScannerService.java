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
package org.pqca.scanning.csharp;

import com.ibm.engine.language.csharp.CSharpCheck;
import com.ibm.plugin.CryptoCSharpSensor;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.io.File;
import java.util.Collection;
import java.util.List;
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

public final class CSharpScannerService extends ScannerService {

    public CSharpScannerService(@Nonnull File projectDirectory) {
        this(null, projectDirectory);
    }

    public CSharpScannerService(
            @Nullable IProgressDispatcher progressDispatcher, @Nonnull File projectDirectory) {
        super(progressDispatcher, projectDirectory);
    }

    @Override
    public @Nonnull ScanResultDTO scan(@Nonnull List<ProjectModule> index)
            throws ClientDisconnected {
        LOGGER.info("Start scanning {} C# projects", index.size());

        long scanTimeStart = System.currentTimeMillis();
        int counter = 1;
        int numberOfScannedLines = 0;
        int numberOfScannedFiles = 0;

        CSharpCheck visitor = new CSharpDetectionCollectionRule(this);
        Collection<CSharpCheck> checks = List.of(visitor);
        final SensorContextTester sensorContext = SensorContextTester.create(projectDirectory);
        // C# scanner (CryptoCSharpSensor) reads files from context
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
                                ProgressMessageType.LABEL, "Scanning C# project " + projectStr));
            }
            LOGGER.info("Scanning C# project {}", projectStr);

            CryptoCSharpSensor.execute((SensorContext) sensorContext, checks);

            counter += 1;
        }
        LOGGER.info("Scanned {} C# projects", index.size());

        return new ScanResultDTO(
                scanTimeStart,
                System.currentTimeMillis(),
                numberOfScannedLines,
                numberOfScannedFiles,
                this.getBOM().map(CBOM::new).orElse(null));
    }
}
