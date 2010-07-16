/*
 * Copyright 2008-2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.codehaus.groovy.transform;

import groovy.transform.ToString;
import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.expr.BooleanExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.ConstructorCallExpression;
import org.codehaus.groovy.ast.expr.DeclarationExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.expr.StaticMethodCallExpression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.EmptyStatement;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.IfStatement;
import org.codehaus.groovy.ast.stmt.ReturnStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.codehaus.groovy.syntax.Token;
import org.codehaus.groovy.syntax.Types;

import java.util.List;

import static org.codehaus.groovy.transform.AbstractASTTransformUtil.getInstanceNonPropertyFields;
import static org.codehaus.groovy.transform.AbstractASTTransformUtil.getInstancePropertyFields;
import static org.codehaus.groovy.transform.AbstractASTTransformUtil.hasDeclaredMethod;

/**
 * Handles generation of code for the @ToString annotation.
 *
 * @author Paul King
 */
@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
public class ToStringASTTransformation extends AbstractASTTransformation {

    static final Class MY_CLASS = ToString.class;
    static final ClassNode MY_TYPE = new ClassNode(MY_CLASS);
    static final String MY_TYPE_NAME = "@" + MY_TYPE.getNameWithoutPackage();
    private static final ClassNode STRINGBUFFER_TYPE = new ClassNode(StringBuffer.class);
    private static final ClassNode INVOKER_TYPE = new ClassNode(InvokerHelper.class);
    private static final Token ASSIGN = Token.newSymbol(Types.ASSIGN, -1, -1);

    public void visit(ASTNode[] nodes, SourceUnit source) {
        init(nodes, source);
        AnnotatedNode parent = (AnnotatedNode) nodes[1];
        AnnotationNode anno = (AnnotationNode) nodes[0];
        if (!MY_TYPE.equals(anno.getClassNode())) return;

        if (parent instanceof ClassNode) {
            ClassNode cNode = (ClassNode) parent;
            checkNotInterface(cNode, MY_TYPE_NAME);
            boolean includeSuper = memberHasValue(anno, "includeSuper", true);
            if (includeSuper && cNode.getSuperClass().getName().equals("java.lang.Object")) {
                addError("Error during " + MY_TYPE_NAME + " processing: includeSuper=true but '" + cNode.getName() + "' has no super class.", anno);
            }
            boolean includeNames = memberHasValue(anno, "includeNames", true);
            boolean includeFields = memberHasValue(anno, "includeFields", true);
            List<String> excludes = tokenize((String) getMemberValue(anno, "excludes"));
            toStringInit(cNode, new ConstantExpression(includeNames));
            createToString(cNode, includeSuper, includeFields, excludes);
        }
    }

    public static void createToString(ClassNode cNode, boolean includeSuper, boolean includeFields, List<String> excludes) {
        // make a public method if none exists otherwise try a private method with leading underscore
        boolean hasExistingToString = hasDeclaredMethod(cNode, "toString", 0);
        if (hasExistingToString && hasDeclaredMethod(cNode, "_toString", 0)) return;

        final BlockStatement body = new BlockStatement();
        // def _result = new StringBuffer()
        final Expression result = new VariableExpression("_result");
        final Expression init = new ConstructorCallExpression(STRINGBUFFER_TYPE, MethodCallExpression.NO_ARGUMENTS);
        body.addStatement(new ExpressionStatement(new DeclarationExpression(result, ASSIGN, init)));

        body.addStatement(append(result, new ConstantExpression(cNode.getName())));
        body.addStatement(append(result, new ConstantExpression("(")));
        boolean first = true;
        List<FieldNode> list = getInstancePropertyFields(cNode);
        if (includeFields) {
            list.addAll(getInstanceNonPropertyFields(cNode));
        }
        for (FieldNode fNode : list) {
            if (excludes.contains(fNode.getName()) || fNode.getName().contains("$")) continue;
            first = appendPrefix(cNode, body, result, first, fNode.getName());
            body.addStatement(append(result, new StaticMethodCallExpression(INVOKER_TYPE, "toString", new VariableExpression(fNode))));
        }
        if (includeSuper) {
            appendPrefix(cNode, body, result, first, "super");
            // not through MOP to avoid infinite recursion
            body.addStatement(append(result, new MethodCallExpression(VariableExpression.SUPER_EXPRESSION, "toString", MethodCallExpression.NO_ARGUMENTS)));
        }
        body.addStatement(append(result, new ConstantExpression(")")));
        body.addStatement(new ReturnStatement(new MethodCallExpression(result, "toString", MethodCallExpression.NO_ARGUMENTS)));
        cNode.addMethod(new MethodNode(hasExistingToString ? "_toString" : "toString", hasExistingToString ? ACC_PRIVATE : ACC_PUBLIC,
                ClassHelper.STRING_TYPE, Parameter.EMPTY_ARRAY, ClassNode.EMPTY_ARRAY, body));
    }

    private static boolean appendPrefix(ClassNode cNode, BlockStatement body, Expression result, boolean first, String name) {
        if (first) {
            first = false;
        } else {
            body.addStatement(append(result, new ConstantExpression(", ")));
        }
        body.addStatement(new IfStatement(
                new BooleanExpression(new VariableExpression(cNode.getField("$print$names"))),
                toStringPropertyName(result, name),
                new EmptyStatement()
        ));
        return first;
    }

    private static Statement toStringPropertyName(Expression result, String fName) {
        final BlockStatement body = new BlockStatement();
        body.addStatement(append(result, new ConstantExpression(fName)));
        body.addStatement(append(result, new ConstantExpression(":")));
        return body;
    }

    private static ExpressionStatement append(Expression result, Expression expr) {
        return new ExpressionStatement(new MethodCallExpression(result, "append", expr));
    }

    public static void toStringInit(ClassNode cNode, ConstantExpression fieldValue) {
        cNode.addField("$print$names", ACC_PRIVATE | ACC_SYNTHETIC, ClassHelper.boolean_TYPE, fieldValue);
    }

}
