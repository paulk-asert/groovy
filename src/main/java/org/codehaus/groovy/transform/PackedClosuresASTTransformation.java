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
package org.codehaus.groovy.transform;

import groovy.transform.CompileStatic;
import groovy.transform.TypeCheckingMode;
import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassCodeExpressionTransformer;
import org.codehaus.groovy.ast.ClassCodeVisitorSupport;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.CodeVisitorSupport;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.Variable;
import org.codehaus.groovy.ast.VariableScope;
import org.codehaus.groovy.ast.expr.ArrayExpression;
import org.codehaus.groovy.ast.expr.BinaryExpression;
import org.codehaus.groovy.ast.expr.ClassExpression;
import org.codehaus.groovy.ast.expr.ClosureExpression;
import org.codehaus.groovy.ast.expr.ConstructorCallExpression;
import org.codehaus.groovy.ast.expr.DeclarationExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.expr.PropertyExpression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.ReturnStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.classgen.VariableScopeVisitor;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.runtime.PackedClosure;
import org.codehaus.groovy.syntax.Types;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static org.apache.groovy.ast.tools.ClassNodeUtils.addGeneratedMethod;
import static org.codehaus.groovy.ast.tools.GeneralUtils.args;
import static org.codehaus.groovy.ast.tools.GeneralUtils.castX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.constX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.ctorX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.varX;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_SYNTHETIC;

/**
 * Prototype implementation of {@link groovy.transform.PackedClosures}. See that annotation
 * and {@link PackedClosure} for the rationale. Runs at {@code SEMANTIC_ANALYSIS} (so
 * closure variable scopes are already populated) and, after rewriting, re-runs
 * {@link VariableScopeVisitor} to rebind moved variable references — mirroring
 * {@code MemoizedASTTransformation}.
 */
@GroovyASTTransformation(phase = CompilePhase.SEMANTIC_ANALYSIS)
public class PackedClosuresASTTransformation extends AbstractASTTransformation {

    private static final ClassNode PACKED_CLOSURE_TYPE = ClassHelper.make(PackedClosure.class);
    private static final ClassNode COMPILESTATIC_TYPE = ClassHelper.make(CompileStatic.class);
    private static final ClassNode TYPECHECKINGMODE_TYPE = ClassHelper.make(TypeCheckingMode.class);

    /** Closure pseudo-properties whose presence means the body needs a real Closure instance. */
    private static final Set<String> FORBIDDEN_NAMES = new HashSet<>(Arrays.asList(
            "owner", "delegate", "thisObject", "directive", "resolveStrategy"));

    /** Accessor/mutator forms of the same. */
    private static final Set<String> FORBIDDEN_CALLS = new HashSet<>(Arrays.asList(
            "getOwner", "getDelegate", "getThisObject", "getResolveStrategy", "getDirective",
            "setDelegate", "setResolveStrategy", "setDirective"));

    /** Methods that, called on a closure literal, require its real Closure identity. */
    private static final Set<String> FORBIDDEN_RECEIVER_METHODS = new HashSet<>(Arrays.asList(
            "memoize", "memoizeAtMost", "memoizeAtLeast", "memoizeBetween",
            "curry", "rcurry", "ncurry", "trampoline",
            "rehydrate", "dehydrate", "clone", "asWritable", "compose", "andThen"));

    @Override
    public void visit(final ASTNode[] nodes, final SourceUnit source) {
        init(nodes, source);
        AnnotatedNode target = (AnnotatedNode) nodes[1];

        List<MethodNode> methods = new ArrayList<>();
        ClassNode topClass;
        if (target instanceof ClassNode) {
            ClassNode cn = (ClassNode) target;
            methods.addAll(cn.getMethods());
            topClass = cn;
        } else if (target instanceof MethodNode) {
            MethodNode mn = (MethodNode) target;
            methods.add(mn);
            topClass = mn.getDeclaringClass();
        } else {
            return;
        }

        boolean[] changed = {false};
        for (MethodNode m : methods) {
            if (m.getCode() == null || m.isAbstract()) continue;
            if (m.isStatic()) continue; // prototype: instance methods only (owner = this)
            boolean staticScope = isStaticCompiled(m);
            Packer packer = new Packer(m.getDeclaringClass(), m.getName(), source, changed, staticScope);
            m.getCode().visit(packer);
            if (staticScope && packer.packedAny) {
                // Under @CompileStatic the raw adapter would break call-site inference (collect(...) ->
                // List<Object>). Where a packed result flows into a declared target (a typed local or a
                // typed method return), cast it to that target type so static type checking succeeds.
                m.getCode().visit(new CastInserter(source, m.getReturnType()));
                castImplicitReturn(m); // trailing expression is the implicit return (not yet a ReturnStatement)
            }
        }

        if (changed[0]) {
            ClassNode owner = topClass;
            VariableScopeVisitor scopeVisitor = new VariableScopeVisitor(source, owner.getOuterClass() != null);
            while (owner.getOuterClass() != null) {
                owner = owner.getOuterClass();
            }
            scopeVisitor.visitClass(owner);
        }
    }

    /**
     * Walks a method body, replacing eligible closure literals with an {@link PackedClosure}
     * that dispatches to a freshly-added synthetic method holding the closure body. Nested
     * closures are handled innermost-first because the body is transformed before its enclosing
     * closure is considered.
     */
    private final class Packer extends ClassCodeExpressionTransformer {
        private final ClassNode classNode;
        private final String methodName;
        private final SourceUnit source;
        private final boolean[] changed;
        private final boolean staticScope;
        private final Set<ClosureExpression> skip =
                Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        private int counter;
        boolean packedAny;

        Packer(ClassNode classNode, String methodName, SourceUnit source, boolean[] changed, boolean staticScope) {
            this.classNode = classNode;
            this.methodName = methodName;
            this.source = source;
            this.changed = changed;
            this.staticScope = staticScope;
        }

        @Override
        protected SourceUnit getSourceUnit() {
            return source;
        }

        @Override
        public Expression transform(final Expression exp) {
            if (exp == null) return null;

            // Record closure literals used as the receiver of a Closure-identity method (e.g.
            // { ... }.memoize()) so we leave them as normal closures.
            if (exp instanceof MethodCallExpression) {
                MethodCallExpression call = (MethodCallExpression) exp;
                if (call.getObjectExpression() instanceof ClosureExpression
                        && FORBIDDEN_RECEIVER_METHODS.contains(call.getMethodAsString())) {
                    skip.add((ClosureExpression) call.getObjectExpression());
                }
            }

            if (exp instanceof ClosureExpression) {
                ClosureExpression ce = (ClosureExpression) exp;
                // Transform the body first (innermost-first): nested eligible closures get replaced.
                if (ce.getCode() != null) ce.getCode().visit(this);
                if (skip.contains(ce) || !isEligible(ce)) {
                    return ce; // leave as a normal generated closure — always safe
                }
                return pack(ce);
            }

            return exp.transformExpression(this);
        }

        private Expression pack(final ClosureExpression ce) {
            // Captured (closure-shared) variables become leading parameters, passed by value.
            List<Parameter> params = new ArrayList<>();
            List<Expression> capturedArgs = new ArrayList<>();
            VariableScope scope = ce.getVariableScope();
            if (scope != null) {
                for (Iterator<Variable> it = scope.getReferencedLocalVariablesIterator(); it.hasNext(); ) {
                    Variable v = it.next();
                    ClassNode type = nonNull(v.getType());
                    params.add(new Parameter(type, v.getName()));
                    capturedArgs.add(varX(v.getName()));
                }
            }

            Parameter[] closureParams = visibleParameters(ce);
            int arity = closureParams.length;
            Collections.addAll(params, closureParams); // reuse the closure's own params (body is bound to them)

            String name = uniqueName();
            MethodNode hoisted = new MethodNode(name, ACC_PUBLIC | ACC_SYNTHETIC, ClassHelper.OBJECT_TYPE,
                    params.toArray(Parameter.EMPTY_ARRAY), ClassNode.EMPTY_ARRAY, ce.getCode());
            if (staticScope) {
                // The hoisted body carries Object-typed captures/params (inferred types are not known
                // this early), so type-check it dynamically: @CompileStatic(TypeCheckingMode.SKIP).
                hoisted.addAnnotation(skipTypeCheckingAnnotation());
            }
            addGeneratedMethod(classNode, hoisted);
            changed[0] = true;
            packedAny = true;

            Expression capturedArray = capturedArgs.isEmpty()
                    ? constX(null)
                    : new ArrayExpression(ClassHelper.OBJECT_TYPE, capturedArgs);
            return ctorX(PACKED_CLOSURE_TYPE, args(
                    VariableExpression.THIS_EXPRESSION,
                    constX(name),
                    capturedArray,
                    constX(arity)));
        }

        /** The closure's visible parameters: implicit {@code it} when unspecified, else as declared. */
        private Parameter[] visibleParameters(final ClosureExpression ce) {
            // NB: getParameters() returns an EMPTY array (not null) for an implicit-it closure such
            // as { it * 2 }; isParameterSpecified() is the correct discriminator. An explicit { -> }
            // is parameter-specified with a genuinely empty array (arity 0).
            if (!ce.isParameterSpecified()) {
                return new Parameter[]{new Parameter(ClassHelper.OBJECT_TYPE, "it")};
            }
            return ce.getParameters(); // may be empty for an explicit { -> }
        }

        private String uniqueName() {
            String base = "$packed$" + methodName + "$" + (counter++);
            String name = base;
            int extra = 0;
            while (!classNode.getMethods(name).isEmpty()) {
                name = base + "$" + (extra++);
            }
            return name;
        }

        private boolean isEligible(final ClosureExpression ce) {
            Set<String> captured = new HashSet<>();
            VariableScope scope = ce.getVariableScope();
            if (scope != null) {
                for (Iterator<Variable> it = scope.getReferencedLocalVariablesIterator(); it.hasNext(); ) {
                    captured.add(it.next().getName());
                }
            }
            EligibilityChecker checker = new EligibilityChecker(captured);
            if (ce.getCode() != null) ce.getCode().visit(checker);
            return checker.eligible;
        }
    }

    private static ClassNode nonNull(final ClassNode type) {
        return (type != null) ? type : ClassHelper.OBJECT_TYPE;
    }

    /** Whether the method is statically compiled (nearest {@code @CompileStatic}, honouring SKIP). */
    private static boolean isStaticCompiled(final MethodNode m) {
        Boolean mode = compileMode(m.getAnnotations());
        if (mode != null) return mode;
        for (ClassNode c = m.getDeclaringClass(); c != null; c = c.getOuterClass()) {
            Boolean classMode = compileMode(c.getAnnotations());
            if (classMode != null) return classMode;
        }
        return false;
    }

    /** TRUE for {@code @CompileStatic}, FALSE for {@code @CompileStatic(SKIP)}/{@code @CompileDynamic}, null if neither. */
    private static Boolean compileMode(final List<AnnotationNode> annotations) {
        for (AnnotationNode a : annotations) {
            ClassNode type = a.getClassNode();
            if (type == null) continue;
            if (COMPILESTATIC_TYPE.equals(type)) {
                Expression value = a.getMember("value");
                return (value instanceof PropertyExpression
                        && "SKIP".equals(((PropertyExpression) value).getPropertyAsString()))
                        ? Boolean.FALSE : Boolean.TRUE;
            }
            if ("groovy.transform.CompileDynamic".equals(type.getName())) return Boolean.FALSE; // unexpanded
        }
        return null;
    }

    private static AnnotationNode skipTypeCheckingAnnotation() {
        AnnotationNode a = new AnnotationNode(COMPILESTATIC_TYPE);
        a.setMember("value", new PropertyExpression(new ClassExpression(TYPECHECKINGMODE_TYPE), "SKIP"));
        return a;
    }

    private static boolean isConcreteTarget(final ClassNode t) {
        return t != null && !ClassHelper.isObjectType(t) && !t.isGenericsPlaceHolder();
    }

    private static boolean containsAdapter(final Expression e) {
        if (e == null) return false;
        boolean[] found = {false};
        e.visit(new CodeVisitorSupport() {
            @Override
            public void visitConstructorCallExpression(final ConstructorCallExpression call) {
                if (PACKED_CLOSURE_TYPE.equals(call.getType())) found[0] = true;
                super.visitConstructorCallExpression(call);
            }
        });
        return found[0];
    }

    /**
     * A method's trailing expression is its implicit return, but at this phase it is still an
     * {@link ExpressionStatement} (ReturnAdder has not run). When the return type is concrete and that
     * expression packs a closure, cast it to the return type — the counterpart of {@link CastInserter}'s
     * explicit-return handling.
     */
    private static void castImplicitReturn(final MethodNode m) {
        ClassNode rt = m.getReturnType();
        if (!isConcreteTarget(rt) || !(m.getCode() instanceof BlockStatement)) return;
        List<Statement> statements = ((BlockStatement) m.getCode()).getStatements();
        if (statements.isEmpty()) return;
        Statement last = statements.get(statements.size() - 1);
        if (last instanceof ExpressionStatement) {
            ExpressionStatement es = (ExpressionStatement) last;
            if (containsAdapter(es.getExpression())) {
                es.setExpression(castX(rt, es.getExpression()));
            }
        }
    }

    /**
     * Restores the static type a packed call site would otherwise lose. Because the {@link PackedClosure}
     * adapter is an untyped {@code Closure}, {@code collect(...)} infers {@code List<Object>}; where such a
     * result flows into a declared target (a typed local declaration or a typed method return), this wraps
     * it in a cast to that target type so {@code @CompileStatic} type checking succeeds. Cases with no
     * declared target (e.g. a discarded {@code each} result) need no cast.
     */
    private static final class CastInserter extends ClassCodeVisitorSupport {
        private final SourceUnit source;
        private final ClassNode returnType;

        CastInserter(SourceUnit source, ClassNode returnType) {
            this.source = source;
            this.returnType = returnType;
        }

        @Override
        protected SourceUnit getSourceUnit() {
            return source;
        }

        @Override
        public void visitExpressionStatement(final ExpressionStatement statement) {
            if (statement.getExpression() instanceof DeclarationExpression) {
                DeclarationExpression de = (DeclarationExpression) statement.getExpression();
                ClassNode target = de.getLeftExpression().getType();
                if (isConcreteTarget(target) && containsAdapter(de.getRightExpression())) {
                    de.setRightExpression(castX(target, de.getRightExpression()));
                }
            }
            super.visitExpressionStatement(statement);
        }

        @Override
        public void visitReturnStatement(final ReturnStatement statement) {
            if (isConcreteTarget(returnType) && containsAdapter(statement.getExpression())) {
                statement.setExpression(castX(returnType, statement.getExpression()));
            }
            super.visitReturnStatement(statement);
        }

        @Override
        public void visitClosureExpression(final ClosureExpression expression) {
            // don't descend into remaining (ineligible) closures
        }
    }

    /**
     * Scans a (already-inner-transformed) closure body for anything that would require a real
     * {@code groovy.lang.Closure} instance. Does not descend into remaining nested closures —
     * their own (in)eligibility is independent.
     */
    private static final class EligibilityChecker extends CodeVisitorSupport {
        private final Set<String> captured;
        boolean eligible = true;

        EligibilityChecker(Set<String> captured) {
            this.captured = captured;
        }

        @Override
        public void visitVariableExpression(final VariableExpression ve) {
            if (ve.isSuperExpression() || FORBIDDEN_NAMES.contains(ve.getName())) {
                eligible = false;
            }
            super.visitVariableExpression(ve);
        }

        @Override
        public void visitPropertyExpression(final PropertyExpression pe) {
            if (FORBIDDEN_NAMES.contains(pe.getPropertyAsString())) {
                eligible = false;
            }
            super.visitPropertyExpression(pe);
        }

        @Override
        public void visitMethodCallExpression(final MethodCallExpression call) {
            if (FORBIDDEN_CALLS.contains(call.getMethodAsString())) {
                eligible = false;
            }
            super.visitMethodCallExpression(call);
        }

        @Override
        public void visitBinaryExpression(final BinaryExpression be) {
            if (Types.isAssignment(be.getOperation().getType())
                    && be.getLeftExpression() instanceof VariableExpression
                    && captured.contains(((VariableExpression) be.getLeftExpression()).getName())) {
                eligible = false; // writes to a captured variable need shared Reference semantics
            }
            super.visitBinaryExpression(be);
        }

        @Override
        public void visitClosureExpression(final ClosureExpression expression) {
            // Do not descend into a remaining (ineligible) nested closure — its refs are its own.
        }
    }
}
