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
package org.apache.groovy.macrolib;

import groovy.lang.GString;
import groovy.lang.NamedValue;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.GStringExpression;
import org.codehaus.groovy.macro.runtime.Macro;
import org.codehaus.groovy.macro.runtime.MacroContext;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.codehaus.groovy.ast.tools.GeneralUtils.args;
import static org.codehaus.groovy.ast.tools.GeneralUtils.callX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.constX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.ctorX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.listX;

/**
 * Macro library helpers for string and named-value expansion.
 *
 * @since 2.5.0
 */
public final class MacroLibGroovyMethods {
    private MacroLibGroovyMethods() {}

    private static final ClassNode NAMED_VALUE = ClassHelper.make(NamedValue.class);

    /**
     * Builds a GString expression that labels each supplied expression with its source text.
     *
     * @param ctx the current macro context
     * @param exps the expressions to interpolate
     * @return the labeled GString expression
     */
    @Macro
    public static Expression SV(MacroContext ctx, final Expression... exps) {
        return new GStringExpression("", makeLabels(exps), Arrays.asList(exps));
    }

    /**
     * Runtime stub for {@link #SV(MacroContext, Expression...)}.
     *
     * @param self the receiver
     * @param args the interpolated values
     * @return never returns normally
     */
    public static GString SV(Object self, Object... args) {
        throw new IllegalStateException("MacroLibGroovyMethods.SV(Object...) should never be called at runtime. Are you sure you are using it correctly?");
    }

    /**
     * Builds a GString expression that labels each supplied expression with its inspected value.
     *
     * @param ctx the current macro context
     * @param exps the expressions to inspect
     * @return the labeled GString expression
     */
    @Macro
    public static Expression SVI(MacroContext ctx, final Expression... exps) {
        List<Expression> expList = Arrays.stream(exps).map(exp -> callX(exp, "inspect"))
                .collect(Collectors.toList());
        return new GStringExpression("", makeLabels(exps), expList);
    }

    /**
     * Runtime stub for {@link #SVI(MacroContext, Expression...)}.
     *
     * @param self the receiver
     * @param args the interpolated values
     * @return never returns normally
     */
    public static GString SVI(Object self, Object... args) {
        throw new IllegalStateException("MacroLibGroovyMethods.SVI(Object...) should never be called at runtime. Are you sure you are using it correctly?");
    }

    /**
     * Builds a GString expression that labels each supplied expression with its dumped value.
     *
     * @param ctx the current macro context
     * @param exps the expressions to dump
     * @return the labeled GString expression
     */
    @Macro
    public static Expression SVD(MacroContext ctx, final Expression... exps) {
        List<Expression> expList = Arrays.stream(exps).map(exp -> callX(exp, "dump"))
                .collect(Collectors.toList());
        return new GStringExpression("", makeLabels(exps), expList);
    }

    /**
     * Runtime stub for {@link #SVD(MacroContext, Expression...)}.
     *
     * @param self the receiver
     * @param args the interpolated values
     * @return never returns normally
     */
    public static GString SVD(Object self, Object... args) {
        throw new IllegalStateException("MacroLibGroovyMethods.SVD(Object...) should never be called at runtime. Are you sure you are using it correctly?");
    }

    private static List<ConstantExpression> makeLabels(Expression[] exps) {
        return IntStream
                .range(0, exps.length)
                .mapToObj(i -> constX((i > 0 ? ", " : "") + exps[i].getText() + "="))
                .collect(Collectors.toList());
    }

    /**
     * Builds a {@link NamedValue} expression from the supplied expression.
     *
     * @param ctx the current macro context
     * @param exp the expression to wrap
     * @return the named-value expression
     */
    @Macro
    public static Expression NV(MacroContext ctx, final Expression exp) {
        return namedValueExpr(exp);
    }

    /**
     * Runtime stub for {@link #NV(MacroContext, Expression)}.
     *
     * @param self the receiver
     * @param arg the runtime value
     * @param <T> the value type
     * @return never returns normally
     */
    public static <T> NamedValue<T> NV(Object self, T arg) {
        throw new IllegalStateException("MacroLibGroovyMethods.NV(Object) should never be called at runtime. Are you sure you are using it correctly?");
    }

    private static Expression namedValueExpr(Expression exp) {
        return ctorX(NAMED_VALUE, args(constX(exp.getText()), exp));
    }

    /**
     * Builds a list of {@link NamedValue} expressions from the supplied expressions.
     *
     * @param ctx the current macro context
     * @param exps the expressions to wrap
     * @return the list expression
     */
    @Macro
    public static Expression NVL(MacroContext ctx, final Expression... exps) {
        return listX(Arrays.stream(exps).map(exp -> namedValueExpr(exp)).collect(Collectors.toList()));
    }

    /**
     * Runtime stub for {@link #NVL(MacroContext, Expression...)}.
     *
     * @param self the receiver
     * @param args the runtime values
     * @param <T> the value type
     * @return never returns normally
     */
    @SuppressWarnings("unchecked")
    public static <T> List<NamedValue<T>> NVL(Object self, T... args) {
        throw new IllegalStateException("MacroLibGroovyMethods.NVL(Object...) should never be called at runtime. Are you sure you are using it correctly?");
    }

}
