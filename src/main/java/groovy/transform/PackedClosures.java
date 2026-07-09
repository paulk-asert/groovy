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
package groovy.transform;

import org.codehaus.groovy.transform.GroovyASTTransformationClass;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Prototype opt-in: compile eligible closure literals in the annotated class or method by
 * hoisting their bodies into synthetic methods on the enclosing class and replacing the
 * literal with a shared {@code PackedClosure} adapter, rather than generating one inner
 * class per closure.
 * <p>
 * The transform only rewrites closures it can prove do not depend on per-closure
 * {@code groovy.lang.Closure} identity/semantics. Ineligible closures are left exactly as
 * they are today (a normal generated closure class), so the annotation is always safe to add.
 * A closure is left alone when it (or, for the receiver checks, its immediate use):
 * <ul>
 *   <li>references {@code owner}, {@code delegate}, {@code thisObject}, {@code directive}
 *       or {@code resolveStrategy};</li>
 *   <li>is the direct receiver of {@code memoize*}, {@code curry}/{@code rcurry}/{@code ncurry},
 *       {@code trampoline}, {@code rehydrate}/{@code dehydrate}, {@code clone} or {@code asWritable};</li>
 *   <li>writes to a captured (closure-shared) variable;</li>
 *   <li>uses {@code super};</li>
 *   <li>appears in a {@code static} method (prototype restriction).</li>
 * </ul>
 * This is a prototype and intentionally conservative.
 */
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE, ElementType.METHOD})
// Writer-spike mode: the AST-transform trigger is disabled so the codegen path (ClosureWriter)
// owns @PackedClosures. Re-enable to use the AST-transform prototype instead.
// @GroovyASTTransformationClass("org.codehaus.groovy.transform.PackedClosuresASTTransformation")
public @interface PackedClosures {
}
