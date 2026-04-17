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
package org.pqca.scanning.csharp;

import com.ibm.engine.detection.Finding;
import com.ibm.engine.language.csharp.CSharpCheck;
import com.ibm.engine.language.csharp.CSharpScanContext;
import com.ibm.engine.language.csharp.CSharpSymbol;
import com.ibm.engine.language.csharp.tree.CSharpTree;
import com.ibm.mapper.model.INode;
import com.ibm.plugin.rules.CSharpInventoryRule;
import jakarta.annotation.Nonnull;
import java.util.List;
import java.util.function.Consumer;

public class CSharpDetectionCollectionRule extends CSharpInventoryRule {
    private final Consumer<List<INode>> handler;

    public CSharpDetectionCollectionRule(@Nonnull Consumer<List<INode>> findingConsumer) {
        this.handler = findingConsumer;
    }

    @Override
    public void update(
            @Nonnull Finding<CSharpCheck, CSharpTree, CSharpSymbol, CSharpScanContext> finding) {
        super.update(finding);
        final List<INode> nodes = csharpTranslationProcess.initiate(finding.detectionStore());
        handler.accept(nodes);
    }
}
