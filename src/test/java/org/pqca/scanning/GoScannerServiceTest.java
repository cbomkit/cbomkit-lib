/*
 * CBOMkit-lib
 * Copyright (C) 2025 PQCA
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
package org.pqca.scanning;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.pqca.errors.ClientDisconnected;
import org.pqca.indexing.ProjectModule;
import org.pqca.indexing.go.GoIndexService;
import org.pqca.scanning.go.GoScannerService;
import org.pqca.utils.AssertableCBOM;

class GoScannerServiceTest {

    @Test
    void test() throws ClientDisconnected, IOException {
        // indexing
        final File projectDirectory = new File("src/test/testdata/go/gocrypto");
        final Set<String> projectDirectoryEntriesBeforeScan =
                listRelativePaths(projectDirectory.toPath());
        final GoIndexService goIndexService = new GoIndexService(projectDirectory);
        final List<ProjectModule> goModules = goIndexService.index(null);
        assertThat(goModules).hasSize(1);
        final ProjectModule projectModule = goModules.getFirst();
        assertThat(projectModule.inputFileList()).isNotEmpty();
        // scanning
        final GoScannerService goScannerService = new GoScannerService(projectDirectory);
        ScanResultDTO scanResult = goScannerService.scan(goModules);

        // check - verify cryptographic assets are detected
        AssertableCBOM assertableCBOM = new AssertableCBOM(scanResult.cbom());
        assertThat(scanResult.cbom().cycloneDXbom().getComponents()).hasSize(28);
        assertableCBOM.hasNumberOfDetections(69);

        assertThat(
                        assertableCBOM.hasDetectionWithNameAt(
                                "SHA-256",
                                "src/test/testdata/go/gocrypto/GoCryptoSHA256TestFile.go",
                                10))
                .isTrue();

        assertThat(
                        assertableCBOM.hasDetectionWithNameAt(
                                "AES-GCM",
                                "src/test/testdata/go/gocrypto/GoCryptoAESTestFile.go",
                                18))
                .isTrue();

        assertThat(
                        assertableCBOM.hasDetectionWithNameAt(
                                "RSA-2048",
                                "src/test/testdata/go/gocrypto/GoCryptoRSATestFile.go",
                                10))
                .isTrue();

        assertThat(
                        assertableCBOM.hasDetectionWithNameAt(
                                "HMAC-SHA-256",
                                "src/test/testdata/go/gocrypto/GoCryptoHMACTestFile.go",
                                11))
                .isTrue();

        assertThat(
                        assertableCBOM.hasDetectionWithNameAt(
                                "PBKDF2",
                                "src/test/testdata/go/gocrypto/GoCryptoPBKDF2TestFile.go",
                                15))
                .isTrue();

        assertThat(listRelativePaths(projectDirectory.toPath()))
                .isEqualTo(projectDirectoryEntriesBeforeScan);
    }

    private static Set<String> listRelativePaths(Path projectDirectory) throws IOException {
        try (Stream<Path> paths = Files.walk(projectDirectory)) {
            return paths.map(projectDirectory::relativize)
                    .map(Path::toString)
                    .collect(Collectors.toSet());
        }
    }
}
