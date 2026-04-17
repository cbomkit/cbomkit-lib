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
import java.util.List;
import org.junit.jupiter.api.Test;
import org.pqca.errors.ClientDisconnected;
import org.pqca.indexing.ProjectModule;
import org.pqca.indexing.csharp.CSharpIndexService;
import org.pqca.scanning.csharp.CSharpScannerService;
import org.pqca.utils.AssertableCBOM;

class CSharpScannerServiceTest {

    @Test
    void test() throws ClientDisconnected {
        // indexing
        final File projectDirectory =
                new File("/Users/san/oss/sonar-cryptography/csharp/src/test/files");
        final CSharpIndexService cSharpIndexService = new CSharpIndexService(projectDirectory);
        final List<ProjectModule> cSharpModules = cSharpIndexService.index(null);
        assertThat(cSharpModules).hasSize(1);
        final ProjectModule projectModule = cSharpModules.getFirst();
        assertThat(projectModule.inputFileList()).hasSize(12);
        // scanning
        final CSharpScannerService cSharpScannerService =
                new CSharpScannerService(projectDirectory);
        ScanResultDTO scanResult = cSharpScannerService.scan(cSharpModules);

        // check - 21 unique cryptographic assets with 27 total occurrences
        AssertableCBOM assertableCBOM = new AssertableCBOM(scanResult.cbom());
        assertThat(scanResult.cbom().cycloneDXbom().getComponents()).hasSize(21);
        assertableCBOM.hasNumberOfDetections(27);

        assertThat(
                        assertableCBOM.hasDetectionWithNameAt(
                                "SHA256", "rules/detection/dotnet/DotNetSHATestFile.cs", 4))
                .isTrue();

        assertThat(
                        assertableCBOM.hasDetectionWithNameAt(
                                "AES256-CBC-PKCS7",
                                "rules/detection/dotnet/DotNetAESPropertyTestFile.cs",
                                7))
                .isTrue();

        assertThat(
                        assertableCBOM.hasDetectionWithNameAt(
                                "HMAC-SHA256", "rules/detection/dotnet/DotNetHMACTestFile.cs", 4))
                .isTrue();

        assertThat(
                        assertableCBOM.hasDetectionWithNameAt(
                                "RSA", "rules/detection/dotnet/DotNetRSATestFile.cs", 3))
                .isTrue();

        assertThat(
                        assertableCBOM.hasDetectionWithNameAt(
                                "PBKDF2",
                                "rules/detection/dotnet/DotNetRfc2898DeriveBytesTestFile.cs",
                                4))
                .isTrue();
    }
}
