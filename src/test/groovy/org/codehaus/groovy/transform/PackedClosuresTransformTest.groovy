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
package org.codehaus.groovy.transform

import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.codehaus.groovy.control.Phases
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertThrows

/**
 * Prototype tests for {@link groovy.transform.PackedClosures}: eligible closure literals are
 * hoisted into synthetic methods on the enclosing class and replaced by a single shared
 * {@code PackedClosure} adapter, so no per-closure inner class is generated. Ineligible
 * closures are left exactly as they are today.
 */
final class PackedClosuresTransformTest {

    /** Compiles the source and returns the sorted names of every generated class. */
    private static List<String> generatedClassNames(String src) {
        def cu = new CompilationUnit(new CompilerConfiguration())
        cu.addSource('Sample.groovy', src)
        cu.compile(Phases.CLASS_GENERATION)
        cu.classes.collect { it.name }.sort()
    }

    private static int closureClassCount(List<String> names) {
        names.count { it.contains('_closure') }
    }

    /** Evaluates the source (which must end by yielding an instance) and returns it. */
    private static Object instance(String src) {
        new GroovyShell().evaluate(src)
    }

    private static final String SAMPLE = '''
        @groovy.transform.PackedClosures
        class Sample {
            String simple()          { [1, 2, 3].collect { it * 2 }.join(',') }
            String nested(Map m) {
                def out = []
                m.each { k, v -> [1, 2].each { i -> out << "${k}${v}${i}".toString() } }
                out.join(',')
            }
            int readCapture(List<Integer> xs, int base) { (xs.collect { it + base }.sum()) as int }
        }
    '''

    @Test
    void eligibleClosuresGenerateNoClosureClasses() {
        def names = generatedClassNames(SAMPLE)
        // Without @PackedClosures this same source produces 4 closure classes, including the
        // deeply-nested Sample$_nested_closure2$_closure4.
        assertEquals(['Sample'], names, "only the owner class should be generated, got: $names")
        assertEquals(0, closureClassCount(names))
    }

    @Test
    void packedClosuresBehaveIdenticallyToPlainOnes() {
        def plain = instance(SAMPLE.replace('@groovy.transform.PackedClosures', '') + '\n new Sample()')
        def packed = instance(SAMPLE + '\n new Sample()')

        assertEquals(plain.simple(), packed.simple())
        assertEquals('2,4,6', packed.simple())

        assertEquals(plain.nested([a: 1, b: 2]), packed.nested([a: 1, b: 2]))
        assertEquals('a11,a12,b21,b22', packed.nested([a: 1, b: 2])) // nested closures + captured k, v

        assertEquals(plain.readCapture([10, 20, 30], 5), packed.readCapture([10, 20, 30], 5))
        assertEquals(75, packed.readCapture([10, 20, 30], 5)) // captured 'base' read by value
    }

    @Test
    void ineligibleClosuresAreLeftIntactAndStillWork() {
        // Each class mixes one eligible closure (packed) with one ineligible one (kept as a
        // real closure). Exactly one closure class must remain, and behavior must be unchanged.
        def cases = [
            [name: 'memoize',
             src : '''@groovy.transform.PackedClosures
                      class X {
                        def eligible()   { [1, 2].collect { it + 1 } }
                        def ineligible() { def c = { int n -> n * n }.memoize(); c(3) + c(3) }
                      }''',
             expect: 18],
            [name: 'delegate',
             src : '''@groovy.transform.PackedClosures
                      class X {
                        def eligible()   { [1, 2].collect { it + 1 } }
                        def ineligible() { def sb = new StringBuilder(); def c = { delegate.append('hi') }
                                           c.delegate = sb; c(); sb.toString() }
                      }''',
             expect: 'hi'],
            [name: 'capturedWrite',
             src : '''@groovy.transform.PackedClosures
                      class X {
                        def eligible()   { [1, 2].collect { it + 1 } }
                        def ineligible() { int total = 0; [1, 2, 3].each { total += it }; total }
                      }''',
             expect: 6],
        ]
        cases.each { c ->
            def names = generatedClassNames(c.src)
            assertEquals(1, closureClassCount(names), "[${c.name}] expected the ineligible closure to remain: $names")
            def obj = instance(c.src + '\n new X()')
            assertEquals([2, 3], obj.eligible(), "[${c.name}] eligible closure result")
            assertEquals(c.expect, obj.ineligible(), "[${c.name}] ineligible closure result")
        }
    }

    /**
     * Under {@code @CompileStatic} the transform applies a cast-to-context heuristic: the hoisted
     * method is marked {@code @CompileStatic(SKIP)} (so its Object-typed body is checked dynamically),
     * and where a packed result flows into a syntactically-declared target — a typed method return or
     * a typed local declaration — the result is cast to that type so static type checking still infers
     * correctly (e.g. {@code collect(...)} → {@code List<Integer>}). This covers a large fraction of
     * real usage; the boundary is exercised by {@link #compileStaticBoundaryHasNoDeclaredTarget}.
     */
    @Test
    void compileStaticPacksResultsWithDeclaredTargets() {
        String src = '''import groovy.transform.CompileStatic
            @CompileStatic
            @groovy.transform.PackedClosures
            class S {
                List<Integer> doubled(List<Integer> xs)     { xs.collect { Integer it -> it * 2 } }          // implicit-return target
                List<String>  tag(List<Integer> xs, String p){ xs.collect { Integer n -> p + n } }
                String        join(List<Integer> xs)        { List<String> ss = xs.collect { Integer it -> "v$it".toString() }; ss.join(',') } // typed-local target
                int           total(List<Integer> xs, int base){ int t = 0; xs.each { t += it + base }; t }   // ineligible: captured write
            }'''
        // only the ineligible captured-write closure remains as a class; the rest are packed
        assertEquals(['S', 'S$_total_closure1'], generatedClassNames(src))

        def s = instance(src + '\n new S()')
        assertEquals([2, 4, 6], s.doubled([1, 2, 3]))
        assertEquals(['x1', 'x2'], s.tag([1, 2], 'x'))
        assertEquals('v1,v2,v3', s.join([1, 2, 3]))
        assertEquals(36, s.total([1, 2, 3], 10))
    }

    /**
     * The boundary of the cast-to-context heuristic: when a packed result flows somewhere with no
     * syntactic target type (passed as a typed argument, or laundered through a {@code def} local),
     * the untyped adapter fails static type checking. A production (writer-based) implementation,
     * which reads the already-computed inferred types, would not have this gap. This also shows the
     * prototype's @CompileStatic packing is not yet "safe" — it can turn otherwise-compilable code
     * into a compile error; a safe version must gate packing on finding a target.
     */
    @Test
    void compileStaticBoundaryHasNoDeclaredTarget() {
        String passedAsArg = '''import groovy.transform.CompileStatic
            @CompileStatic
            @groovy.transform.PackedClosures
            class Z {
                int need(List<Integer> ys) { ys.sum() as int }
                int m(List<Integer> xs)    { need(xs.collect { Integer it -> it * 2 }) }
            }'''
        assertThrows(MultipleCompilationErrorsException) { generatedClassNames(passedAsArg) }
    }
}
