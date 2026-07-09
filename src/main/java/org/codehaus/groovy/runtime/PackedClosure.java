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
package org.codehaus.groovy.runtime;

import groovy.lang.Closure;

import java.util.Arrays;

/**
 * A single, shared {@link Closure} adapter used by the {@code @PackedClosures} prototype.
 * <p>
 * Normally the Groovy compiler generates one inner class per closure literal
 * (e.g. {@code Owner$_method_closure1}). Under {@code @PackedClosures} an <em>eligible</em>
 * closure body is instead hoisted into a synthetic method on the enclosing ("owner")
 * class, and the closure literal is replaced by an instance of this one adapter class,
 * which dispatches back to that method. This removes the per-closure generated class
 * (and the deeply-nested {@code $_closure1$_closure2$_closure3} name explosion) while
 * still yielding a real {@code groovy.lang.Closure} instance, so features that operate
 * through {@code call()} (iteration, {@code curry}, {@code memoize}, {@code trampoline})
 * continue to work.
 * <p>
 * Captured values are stored at construction and prepended to the call arguments before
 * dispatch, so the hoisted method has the shape {@code method(captured..., closureParams...)}.
 * This is a prototype: dispatch goes through {@link InvokerHelper} rather than an
 * {@code invokedynamic} call site.
 */
public final class PackedClosure extends Closure<Object> {

    private static final long serialVersionUID = 1L;
    private static final Object[] EMPTY = new Object[0];

    private final String method;
    private final Object[] captured;

    /**
     * @param owner     the enclosing instance the hoisted method lives on (also used as thisObject)
     * @param method    the name of the hoisted synthetic method
     * @param captured  values captured from the enclosing scope, prepended to each call
     * @param maxParams the number of parameters the original closure declared (its visible arity)
     */
    public PackedClosure(final Object owner, final String method, final Object[] captured, final int maxParams) {
        super(owner, owner);
        this.method = method;
        this.captured = (captured != null) ? captured : EMPTY;
        this.maximumNumberOfParameters = maxParams;
        Class<?>[] pt = new Class<?>[maxParams];
        Arrays.fill(pt, Object.class);
        this.parameterTypes = pt;
    }

    public Object doCall(final Object... args) {
        Object[] provided = (args != null) ? args : EMPTY;
        int arity = getMaximumNumberOfParameters();
        // Normalise the visible arguments to the closure's declared arity: pad missing with
        // null (implicit-it / under-application) and drop extras (over-application), matching
        // how a normal Groovy closure tolerates arity mismatches from callers such as each().
        Object[] all = new Object[captured.length + arity];
        System.arraycopy(captured, 0, all, 0, captured.length);
        for (int i = 0; i < arity; i++) {
            all[captured.length + i] = (i < provided.length) ? provided[i] : null;
        }
        return InvokerHelper.invokeMethod(getOwner(), method, all);
    }
}
