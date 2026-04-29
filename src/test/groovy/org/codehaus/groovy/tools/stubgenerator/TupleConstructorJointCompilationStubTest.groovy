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
package org.codehaus.groovy.tools.stubgenerator

/**
 * Captures the joint-compilation surface for {@code @TupleConstructor} on a class
 * that mixes a directly-declared property with a trait-injected property.
 *
 * GEP-21 learnings surfaced by this spike:
 *
 * <ol>
 *   <li>For the default {@code @TupleConstructor} configuration, the stubber's
 *       view at CONVERSION (directly-declared properties only) HAPPENS TO MATCH
 *       what the full transform produces at CANONICALIZATION. {@code @TupleConstructor}
 *       does not pull trait properties into its constructor by default, so there
 *       is no stub/runtime divergence to worry about.</li>
 *   <li>The Verifier-generated overload chain ({@code defaults=true}) means Java
 *       callers using a prefix of the constructor parameters resolve correctly
 *       at runtime even though Verifier hasn't run when the stub is emitted.</li>
 *   <li>Real divergence risk lives in less common configurations:
 *       <ul>
 *         <li>{@code includeSuperProperties=true} — super-class properties may
 *             not be resolvable yet at CONVERSION;</li>
 *         <li>{@code includes}/{@code excludes} naming trait or super properties
 *             that the stubber cannot see;</li>
 *         <li>{@code defaults=false} — no overload chain to rescue mismatched
 *             prefix calls at runtime.</li>
 *       </ul>
 *       In these cases a Shape C stubber must either accept reduced Java visibility
 *       (subset stub) or fall back to a marker-based approach (Shape A/B).</li>
 * </ol>
 */
final class TupleConstructorJointCompilationStubTest extends StringSourcesStubTestCase {

    @Override
    Map<String, String> provideSources() {
        [
            'foo/Task.groovy': '''
                package foo

                import groovy.transform.TupleConstructor

                trait Prioritized {
                    Integer priority
                }

                @TupleConstructor
                class Task implements Prioritized {
                    String label
                }
            ''',
            'foo/Foo.groovy': '''
                package foo

                @groovy.transform.TupleConstructor
                class Foo {
                    String bar
                    String baz = 'BAZ'
                    String render() { "$bar$baz" }
                }
            ''',
            'foo/Named.groovy': '''
                package foo

                // namedVariant=true causes the full transform to delegate to
                // NamedVariantASTTransformation.createMapVariant for an extra
                // Foo(Map) constructor at runtime. The stubber mirrors this.
                @groovy.transform.TupleConstructor(namedVariant = true)
                class Named {
                    String label
                    int seed
                }
            ''',
            'foo/JavaUser.java': '''
                package foo;

                import java.util.HashMap;
                import java.util.Map;

                public class JavaUser {
                    public static Task createTask() {
                        // The directly-declared property is visible in the stub.
                        return new Task("hello");
                    }
                    public static Task createDefaultTask() {
                        // No-arg overload exists at runtime (defaults=true) — the
                        // stubber must expose it to keep the stub a subset.
                        return new Task();
                    }
                    public static String fooFull()    { return new Foo("BAR", "baz").render(); }
                    public static String fooMiddle()  { return new Foo("BAR").render(); }
                    public static String fooDefault() { return new Foo().render(); }
                    public static Named viaMap() {
                        // namedVariant=true → Foo(Map) is callable from Java.
                        Map<String, Object> args = new HashMap<>();
                        args.put("label", "alpha");
                        args.put("seed", 42);
                        return new Named(args);
                    }
                }
            '''
        ]
    }

    @Override
    void verifyStubs() {
        // Stub view: the stubber emitted the prefix-overload chain using the
        // directly-declared properties — Task(), Task(String).
        String taskStub = stubJavaSourceFor('foo.Task')
        assert taskStub =~ /public\s+Task\s*\(\s*java\.lang\.String\s+\w+\s*\)/
        assert taskStub =~ /public\s+Task\s*\(\s*\)/
        assert !taskStub.contains('Task(java.lang.String,') // no trait-aware overload

        // Foo stub: prefix chain Foo(), Foo(String), Foo(String,String).
        String fooStub = stubJavaSourceFor('foo.Foo')
        assert fooStub =~ /public\s+Foo\s*\(\s*\)/
        assert fooStub =~ /public\s+Foo\s*\(\s*java\.lang\.String\s+\w+\s*\)/
        assert fooStub =~ /public\s+Foo\s*\(\s*java\.lang\.String\s+\w+\s*,\s*java\.lang\.String\s+\w+\s*\)/

        Class taskClass = loader.loadClass('foo.Task')
        Class fooClass = loader.loadClass('foo.Foo')

        // Task: Java code calls every overload — stub matches runtime.
        def t1 = taskClass.newInstance('hello')
        assert t1.label == 'hello'
        assert t1.priority == null
        def t2 = taskClass.newInstance()
        assert t2.label == null

        // Foo: explicit default value 'BAZ' is honoured by the runtime overload chain.
        // The user's example.
        assert fooClass.newInstance('BAR', 'baz').render() == 'BARbaz'
        assert fooClass.newInstance('BAR').render() == 'BARBAZ'

        // namedVariant=true: stub has Named(Map) plus the regular tuple chain.
        String namedStub = stubJavaSourceFor('foo.Named')
        assert namedStub =~ /public\s+Named\s*\(\s*java\.util\.Map\s+\w+\s*\)/
        assert namedStub =~ /public\s+Named\s*\(\s*java\.lang\.String\s+\w+\s*,\s*int\s+\w+\s*\)/

        // Runtime view: Map-based construction works.
        Class namedClass = loader.loadClass('foo.Named')
        def viaMap = namedClass.getConstructor(Map).newInstance([label: 'alpha', seed: 42])
        assert viaMap.label == 'alpha'
        assert viaMap.seed == 42

        // The placeholder stubber constructors were discarded by the full transform —
        // they must not survive into the runtime class file. Runtime exposes only
        // the full transform's maximal constructor plus Verifier's overloads.
        def ctorSignatures = taskClass.declaredConstructors.collect {
            it.parameterTypes*.simpleName
        }.toSet()
        assert ctorSignatures == [['String'], []].toSet()
    }
}
