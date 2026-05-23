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
package org.apache.groovy.adhoc

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic

import java.util.function.BiFunction
import java.util.function.BiPredicate
import java.util.function.Predicate

/**
 * Helper class for benchmarking "fat-free lambda" candidate APIs vs. the
 * existing closure / curry idioms (see donraab.medium.com/fat-free-lambdas-in-java).
 * <p>
 * The candidate {@code anyWith}/{@code countWith} sidecar methods accept a
 * {@link BiPredicate} plus an auxiliary parameter, so the lambda body has
 * nothing to capture.  Under {@code @CompileStatic}, such lambdas are emitted
 * as static methods and shared as singletons by {@code LambdaMetafactory}
 * (the GROOVY-11905 optimisation), giving zero per-call lambda allocation.
 * <p>
 * Compared variants iterate the same data with the same per-element work; only
 * the closure / lambda machinery differs.  Prefixes never match, so every
 * variant performs a full traversal.
 */
@CompileStatic
class FatFreeLambda {

    // ----- Candidate "*With" sidecar methods (proposed for DGM) -----------------

    /** Candidate DGM addition: stateless predicate + auxiliary parameter. */
    static <T, P> boolean anyWith(Iterable<T> self, BiPredicate<? super T, ? super P> test, P p) {
        for (T t : self) {
            if (test.test(t, p)) return true
        }
        return false
    }

    /** Candidate DGM addition: stateless predicate + auxiliary parameter. */
    static <T, P> int countWith(Iterable<T> self, BiPredicate<? super T, ? super P> test, P p) {
        int n = 0
        for (T t : self) {
            if (test.test(t, p)) n++
        }
        return n
    }

    /** Candidate DGM addition (collect-shaped) for the BiFunction case. */
    static <T, P, R> List<R> collectWith(Iterable<T> self, BiFunction<? super T, ? super P, ? extends R> f, P p) {
        List<R> out = new ArrayList<>()
        for (T t : self) out.add(f.apply(t, p))
        return out
    }

    /**
     * Candidate fat-free curry: adapts a stateless {@link BiPredicate} + value to a
     * unary {@link Predicate} without involving {@link Closure} / {@code CurriedClosure}.
     * <p>
     * Per-call cost as currently compiled: 1 synthetic lambda instance + 2
     * {@code groovy.lang.Reference} wrappers (Groovy's {@code LambdaWriter} wraps
     * captured method parameters even when effectively final).  Still substantially
     * lighter than rcurry (B), but heavier than the theoretical optimum a future
     * LambdaWriter pass could reach by capturing effectively-final method
     * parameters by value, matching javac.
     */
    static <T, P> Predicate<T> curryWith(BiPredicate<? super T, ? super P> bi, P p) {
        return (T t) -> bi.test(t, p)
    }

    /** Predicate-shaped callee for the curryWith comparison. */
    static <T> boolean anyPred(Iterable<T> self, Predicate<? super T> p) {
        for (T t : self) {
            if (p.test(t)) return true
        }
        return false
    }

    /** Predicate-shaped callee for the curryWith count comparison. */
    static <T> int countPred(Iterable<T> self, Predicate<? super T> p) {
        int n = 0
        for (T t : self) {
            if (p.test(t)) n++
        }
        return n
    }

    // ----- Variants ---------------------------------------------------------------
    // The closure variants are written in @CompileDynamic to reflect typical Groovy
    // call-site idiom; static-compiling them does not change the closure allocation
    // pattern because DGM.any/count take Closure, not a functional interface.

    /** A: capturing closure literal — the classic Groovy idiom. */
    @CompileDynamic
    static boolean anyCaptureClosure(List<String> data, String prefix) {
        data.any { it.startsWith(prefix) }
    }

    /** B: closure literal + rcurry — Groovy's existing "don't capture" idiom. */
    @CompileDynamic
    static boolean anyRcurryClosure(List<String> data, String prefix) {
        Closure pred = { String s, String p -> s.startsWith(p) }
        data.any(pred.rcurry(prefix))
    }

    /** C: candidate {@code anyWith} + stateless BiPredicate lambda (singleton via indy). */
    static boolean anyWithBiPredicate(List<String> data, String prefix) {
        BiPredicate<String, String> p = (String s, String x) -> s.startsWith(x)
        anyWith(data, p, prefix)
    }

    /** D: candidate {@code anyWith} + unbound method reference (singleton via indy). */
    static boolean anyWithMethodRef(List<String> data, String prefix) {
        BiPredicate<String, String> ref = String::startsWith
        anyWith(data, ref, prefix)
    }

    /** E: baseline plain for-loop — lower bound. */
    static boolean anyBaseline(List<String> data, String prefix) {
        for (String s : data) {
            if (s.startsWith(prefix)) return true
        }
        return false
    }

    /** F: hoisted-singleton closure + rcurry — upper bound for what a fat-free Closure could give. */
    private static final Closure<Boolean> SHARED_BI_PRED =
            { String s, String p -> s.startsWith(p) } as Closure<Boolean>

    @CompileDynamic
    static boolean anySharedCurry(List<String> data, String prefix) {
        data.any(SHARED_BI_PRED.rcurry(prefix))
    }

    /** Singleton BiPredicate (method ref hoisted via indy) used by the curryWith variants. */
    private static final BiPredicate<String, String> STARTS_WITH = String::startsWith

    /** G: curryWith — one small capturing lambda per call, no Closure / CurriedClosure. */
    static boolean anyCurryWith(List<String> data, String prefix) {
        anyPred(data, curryWith(STARTS_WITH, prefix))
    }

    // ----- count variants (full traversal — amplifies per-element dispatch cost) --

    @CompileDynamic
    static int countCaptureClosure(List<String> data, String prefix) {
        data.count { String s -> s.startsWith(prefix) } as int
    }

    @CompileDynamic
    static int countRcurryClosure(List<String> data, String prefix) {
        Closure pred = { String s, String p -> s.startsWith(p) }
        data.count(pred.rcurry(prefix)) as int
    }

    static int countWithBiPredicate(List<String> data, String prefix) {
        BiPredicate<String, String> p = (String s, String x) -> s.startsWith(x)
        countWith(data, p, prefix)
    }

    static int countWithMethodRef(List<String> data, String prefix) {
        BiPredicate<String, String> ref = String::startsWith
        countWith(data, ref, prefix)
    }

    static int countBaseline(List<String> data, String prefix) {
        int n = 0
        for (String s : data) {
            if (s.startsWith(prefix)) n++
        }
        return n
    }

    static int countCurryWith(List<String> data, String prefix) {
        countPred(data, curryWith(STARTS_WITH, prefix))
    }
}
