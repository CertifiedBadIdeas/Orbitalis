/*
 * The Orbitalis Developers
 *
 * Copyright 2026 Vsevolod Petrov (lazyhat)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.cbi.orbitalis.neoforge.integration;

import org.spongepowered.asm.mixin.injection.selectors.ElementNode;
import org.spongepowered.asm.mixin.injection.selectors.ISelectorContext;
import org.spongepowered.asm.mixin.injection.selectors.ITargetSelector;
import org.spongepowered.asm.mixin.injection.selectors.ITargetSelectorDynamic;
import org.spongepowered.asm.mixin.injection.selectors.MatchResult;
import org.spongepowered.asm.mixin.transformer.meta.MixinMerged;
import org.spongepowered.asm.util.Annotations;

/** Select exactly one upstream merged handler without depending on Mixin's generated prefix. */
@ITargetSelectorDynamic.SelectorId(namespace = "orbitalis", value = "sableFluidGuard")
public final class SableFluidGuardSelector implements ITargetSelectorDynamic {
    public static SableFluidGuardSelector parse(String input, ISelectorContext context) { return new SableFluidGuardSelector(); }
    public ITargetSelector next() { return null; }
    public ITargetSelector configure(Configure configure, String... args) { return this; }
    public ITargetSelector validate() { return this; }
    public ITargetSelector attach(ISelectorContext context) { return this; }
    public int getMinMatchCount() { return 0; }
    public int getMaxMatchCount() { return 1; }
    public <TNode> MatchResult match(ElementNode<TNode> node) {
        if (node == null || node.getMethod() == null || !node.getName().endsWith("$sable$canSpreadTo")) return MatchResult.NONE;
        var annotation = Annotations.getVisible(node.getMethod(), MixinMerged.class);
        return annotation != null && "dev.ryanhcode.sable.mixin.fluids_on_sub_levels.FlowingFluidMixin".equals(Annotations.getValue(annotation, "mixin"))
            ? MatchResult.EXACT_MATCH : MatchResult.NONE;
    }
}
