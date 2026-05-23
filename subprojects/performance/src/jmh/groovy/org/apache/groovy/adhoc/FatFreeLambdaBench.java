/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.apache.groovy.adhoc;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Benchmarks comparing candidate "fat-free" {@code *With} APIs against the
 * existing closure / {@code rcurry} idioms for predicate-style iteration.
 * <p>
 * Variants (see {@link FatFreeLambda}):
 * <ul>
 *   <li><b>A</b> {@code data.any { it.startsWith(prefix) }} — capturing closure literal</li>
 *   <li><b>B</b> {@code data.any(pred.rcurry(prefix))} — closure + rcurry wrap</li>
 *   <li><b>C</b> {@code anyWith(data, (s,p) -> s.startsWith(p), prefix)} — candidate API + indy lambda</li>
 *   <li><b>D</b> {@code anyWith(data, String::startsWith, prefix)} — candidate API + method ref</li>
 *   <li><b>E</b> hand-written {@code for} loop — baseline</li>
 *   <li><b>F</b> hoisted-singleton closure {@code + rcurry} — upper bound for what fat-free Closures could give</li>
 *   <li><b>G</b> {@code anyPred(data, curryWith(STARTS_WITH, prefix))} — fat-free curry helper (one small capturing lambda)</li>
 * </ul>
 * <p>
 * All variants perform a full traversal (prefixes never match) and identical
 * per-element work; only the closure / lambda machinery differs.  Prefix
 * values rotate through a pre-built array so capture sites must keep
 * allocating, but the rotation cost is constant across variants.
 * <p>
 * To collect allocation stats, add {@code profilers = ['gc']} to the
 * {@code jmh{}} block in {@code build-logic/src/main/groovy/org.apache.groovy-performance.gradle}
 * before running.  Run with:
 * <pre>
 *   ./gradlew :perf:jmh -PbenchInclude=FatFreeLambda
 * </pre>
 * Or repeat with {@code -Pindy=false} to compare with classic call sites.
 */
@Warmup(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(2)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
public class FatFreeLambdaBench {

    /** Workload size — how many elements the predicate is applied to per call. */
    @Param({"100", "1000", "10000"})
    private int size;

    private List<String> data;
    private String[] prefixes;
    private int idx;

    @Setup(Level.Trial)
    public void setup() {
        data = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            data.add("word_" + i);
        }
        // Prefixes that never match any element — forces full traversal in every variant.
        // Eight values so the rotate index is a cheap mask-and-increment.
        prefixes = new String[]{"z_4", "z_70", "z_300", "z_99", "z_500", "z_7", "z_888", "z_1"};
    }

    private String nextPrefix() {
        return prefixes[(idx++) & 7];
    }

    // ----- any (short-circuit; here always traverses fully because no match) -----

    @Benchmark public boolean anyA_captureClosure()      { return FatFreeLambda.anyCaptureClosure(data, nextPrefix()); }
    @Benchmark public boolean anyB_rcurryClosure()       { return FatFreeLambda.anyRcurryClosure(data, nextPrefix()); }
    @Benchmark public boolean anyC_anyWithBiPredicate()  { return FatFreeLambda.anyWithBiPredicate(data, nextPrefix()); }
    @Benchmark public boolean anyD_anyWithMethodRef()    { return FatFreeLambda.anyWithMethodRef(data, nextPrefix()); }
    @Benchmark public boolean anyE_baseline()            { return FatFreeLambda.anyBaseline(data, nextPrefix()); }
    @Benchmark public boolean anyF_sharedSingletonCurry(){ return FatFreeLambda.anySharedCurry(data, nextPrefix()); }
    @Benchmark public boolean anyG_curryWith()         { return FatFreeLambda.anyCurryWith(data, nextPrefix()); }

    // ----- count (full traversal — amplifies per-element dispatch differences) ---

    @Benchmark public int countA_captureClosure()        { return FatFreeLambda.countCaptureClosure(data, nextPrefix()); }
    @Benchmark public int countB_rcurryClosure()         { return FatFreeLambda.countRcurryClosure(data, nextPrefix()); }
    @Benchmark public int countC_countWithBiPredicate()  { return FatFreeLambda.countWithBiPredicate(data, nextPrefix()); }
    @Benchmark public int countD_countWithMethodRef()    { return FatFreeLambda.countWithMethodRef(data, nextPrefix()); }
    @Benchmark public int countE_baseline()              { return FatFreeLambda.countBaseline(data, nextPrefix()); }
    @Benchmark public int countG_curryWith()           { return FatFreeLambda.countCurryWith(data, nextPrefix()); }
}
