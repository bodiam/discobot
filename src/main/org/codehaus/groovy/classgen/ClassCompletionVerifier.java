/*******************************************************************************
 * Copyright (c) 2004 IBM Corporation and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which is available at:
 * http://www.eclipse.org/legal/cpl-v10.html
 *
 * Contributors:
 * IBM - Initial API and implementation
 * Groovy community - subsequent modifications
 ******************************************************************************/
package org.codehaus.groovy.classgen;

import java.lang.reflect.Modifier;
import java.util.List;

import org.codehaus.groovy.ast.ClassCodeVisitorSupport;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.ConstructorNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.PropertyNode;
import org.codehaus.groovy.ast.Variable;
import org.codehaus.groovy.ast.expr.BinaryExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.DeclarationExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.GStringExpression;
import org.codehaus.groovy.ast.expr.MapEntryExpression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.expr.TupleExpression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.ast.stmt.CatchStatement;
import org.codehaus.groovy.control.SourceUnit;
import org.objectweb.asm.Opcodes;
import org.codehaus.groovy.runtime.MetaClassHelper;
import org.codehaus.groovy.syntax.Types;

/**
 * ClassCompletionVerifier
 */
public class ClassCompletionVerifier extends ClassCodeVisitorSupport {

    private ClassNode currentClass;
    private SourceUnit source;
    private boolean inConstructor = false;
    private boolean inStaticConstructor = false;

    public ClassCompletionVerifier(SourceUnit source) {
        this.source = source;
    }

    public ClassNode getClassNode() {
        return currentClass;
    }

    public void visitClass(ClassNode node) {
        ClassNode oldClass = currentClass;
        currentClass = node;
        checkImplementsAndExtends(node);
        if (source != null && !source.getErrorCollector().hasErrors()) {
            checkClassForIncorrectModifiers(node);
            checkClassForOverwritingFinal(node);
            checkMethodsForIncorrectModifiers(node);
            checkMethodsForOverridingFinal(node);
            checkNoAbstractMethodsNonabstractClass(node);
        }
        super.visitClass(node);
        currentClass = oldClass;
    }

    private void checkNoAbstractMethodsNonabstractClass(ClassNode node) {
        if (Modifier.isAbstract(node.getModifiers())) return;
        List<MethodNode> abstractMethods = node.getAbstractMethods();
        if (abstractMethods == null) return;
        for (MethodNode method : abstractMethods) {
            addError("Can't have an abstract method in a non-abstract class." +
                    " The " + getDescription(node) + " must be declared abstract or" +
                    " the " + getDescription(method) + " must be implemented.", node);
        }
    }

    private void checkClassForIncorrectModifiers(ClassNode node) {
        checkClassForAbstractAndFinal(node);
        checkClassForOtherModifiers(node);
    }

    private void checkClassForAbstractAndFinal(ClassNode node) {
        if (!Modifier.isAbstract(node.getModifiers())) return;
        if (!Modifier.isFinal(node.getModifiers())) return;
        if (node.isInterface()) {
            addError("The " + getDescription(node) + " must not be final. It is by definition abstract.", node);
        } else {
            addError("The " + getDescription(node) + " must not be both final and abstract.", node);
        }
    }

    private void checkClassForOtherModifiers(ClassNode node) {
        checkClassForModifier(node, Modifier.isTransient(node.getModifiers()), "transient");
        checkClassForModifier(node, Modifier.isVolatile(node.getModifiers()), "volatile");
        checkClassForModifier(node, Modifier.isNative(node.getModifiers()), "native");
        // don't check synchronized here as it overlaps with ACC_SUPER
    }

    private void checkMethodForModifier(MethodNode node, boolean condition, String modifierName) {
        if (!condition) return;
        addError("The " + getDescription(node) + " has an incorrect modifier " + modifierName + ".", node);
    }

    private void checkClassForModifier(ClassNode node, boolean condition, String modifierName) {
        if (!condition) return;
        addError("The " + getDescription(node) + " has an incorrect modifier " + modifierName + ".", node);
    }

    private String getDescription(ClassNode node) {
        return (node.isInterface() ? "interface" : "class") + " '" + node.getName() + "'";
    }

    private String getDescription(MethodNode node) {
        return "method '" + node.getTypeDescriptor() + "'";
    }

    private String getDescription(FieldNode node) {
        return "field '" + node.getName() + "'";
    }

    private void checkAbstractDeclaration(MethodNode methodNode) {
        if (!Modifier.isAbstract(methodNode.getModifiers())) return;
        if (Modifier.isAbstract(currentClass.getModifiers())) return;
        addError("Can't have an abstract method in a non-abstract class." +
                " The " + getDescription(currentClass) + " must be declared abstract or the method '" +
                methodNode.getTypeDescriptor() + "' must not be abstract.", methodNode);
    }

    private void checkClassForOverwritingFinal(ClassNode cn) {
        ClassNode superCN = cn.getSuperClass();
        if (superCN == null) return;
        if (!Modifier.isFinal(superCN.getModifiers())) return;
        StringBuffer msg = new StringBuffer();
        msg.append("You are not allowed to overwrite the final ");
        msg.append(getDescription(superCN));
        msg.append(".");
        addError(msg.toString(), cn);
    }

    private void checkImplementsAndExtends(ClassNode node) {
        ClassNode cn = node.getSuperClass();
        if (cn.isInterface() && !node.isInterface()) {
            addError("You are not allowed to extend the " + getDescription(cn) + ", use implements instead.", node);
        }
        for (ClassNode anInterface : node.getInterfaces()) {
            cn = anInterface;
            if (!cn.isInterface()) {
                addError("You are not allowed to implement the " + getDescription(cn) + ", use extends instead.", node);
            }
        }
    }

    private void checkMethodsForIncorrectModifiers(ClassNode cn) {
        if (!cn.isInterface()) return;
        for (MethodNode method : cn.getMethods()) {
            if (Modifier.isFinal(method.getModifiers())) {
                addError("The " + getDescription(method) + " from " + getDescription(cn) +
                        " must not be final. It is by definition abstract.", method);
            }
            if (Modifier.isStatic(method.getModifiers()) && !isConstructor(method)) {
                addError("The " + getDescription(method) + " from " + getDescription(cn) +
                        " must not be static. Only fields may be static in an interface.", method);
            }
        }
    }

    private boolean isConstructor(MethodNode method) {
        return method.getName().equals("<clinit>");
    }

    private void checkMethodsForOverridingFinal(ClassNode cn) {
        for (MethodNode method : cn.getMethods()) {
            Parameter[] params = method.getParameters();
            for (MethodNode superMethod : cn.getSuperClass().getMethods(method.getName())) {
                Parameter[] superParams = superMethod.getParameters();
                if (!hasEqualParameterTypes(params, superParams)) continue;
                if (!Modifier.isFinal(superMethod.getModifiers())) break;
                addInvalidUseOfFinalError(method, params, superMethod.getDeclaringClass());
                return;
            }
        }
    }

    private void addInvalidUseOfFinalError(MethodNode method, Parameter[] parameters, ClassNode superCN) {
        StringBuffer msg = new StringBuffer();
        msg.append("You are not allowed to override the final method ").append(method.getName());
        msg.append("(");
        boolean needsComma = false;
        for (Parameter parameter : parameters) {
            if (needsComma) {
                msg.append(",");
            } else {
                needsComma = true;
            }
            msg.append(parameter.getType());
        }
        msg.append(") from ").append(getDescription(superCN));
        msg.append(".");
        addError(msg.toString(), method);
    }

    private boolean hasEqualParameterTypes(Parameter[] first, Parameter[] second) {
        if (first.length != second.length) return false;
        for (int i = 0; i < first.length; i++) {
            String ft = first[i].getType().getName();
            String st = second[i].getType().getName();
            if (ft.equals(st)) continue;
            return false;
        }
        return true;
    }

    protected SourceUnit getSourceUnit() {
        return source;
    }

    public void visitMethod(MethodNode node) {
        inConstructor = false;
        inStaticConstructor = node.isStaticConstructor();
        checkAbstractDeclaration(node);
        checkRepetitiveMethod(node);
        checkOverloadingPrivateAndPublic(node);
        checkMethodModifiers(node);
        super.visitMethod(node);
    }

    private void checkMethodModifiers(MethodNode node) {
        // don't check volatile here as it overlaps with ACC_BRIDGE
        // additional modifiers not allowed for interfaces
        if ((this.currentClass.getModifiers() & Opcodes.ACC_INTERFACE) != 0) {
            checkMethodForModifier(node, Modifier.isStrict(node.getModifiers()), "strictfp");
            checkMethodForModifier(node, Modifier.isSynchronized(node.getModifiers()), "synchronized");
            checkMethodForModifier(node, Modifier.isNative(node.getModifiers()), "native");
        }
    }

    private void checkOverloadingPrivateAndPublic(MethodNode node) {
        if (isConstructor(node)) return;
        boolean hasPrivate = false;
        boolean hasPublic = false;
        for (MethodNode method : currentClass.getMethods(node.getName())) {
            if (method == node) continue;
            if (!method.getDeclaringClass().equals(node.getDeclaringClass())) continue;
            int modifiers = method.getModifiers();
            if (Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers)) {
                hasPublic = true;
            } else {
                hasPrivate = true;
            }
        }
        if (hasPrivate && hasPublic) {
            addError("Mixing private and public/protected methods of the same name causes multimethods to be disabled and is forbidden to avoid surprising behaviour. Renaming the private methods will solve the problem.", node);
        }
    }

    private void checkRepetitiveMethod(MethodNode node) {
        if (isConstructor(node)) return;
        for (MethodNode method : currentClass.getMethods(node.getName())) {
            if (method == node) continue;
            if (!method.getDeclaringClass().equals(node.getDeclaringClass())) continue;
            Parameter[] p1 = node.getParameters();
            Parameter[] p2 = method.getParameters();
            if (p1.length != p2.length) continue;
            addErrorIfParamsAndReturnTypeEqual(p2, p1, node, method);
        }
    }

    private void addErrorIfParamsAndReturnTypeEqual(Parameter[] p2, Parameter[] p1,
                                                    MethodNode node, MethodNode element) {
        boolean isEqual = true;
        for (int i = 0; i < p2.length; i++) {
            isEqual &= p1[i].getType().equals(p2[i].getType());
        }
        isEqual &= node.getReturnType().equals(element.getReturnType());
        if (isEqual) {
            addError("Repetitive method name/signature for " + getDescription(node) +
                    " in " + getDescription(currentClass) + ".", node);
        }
    }

    public void visitField(FieldNode node) {
        if (currentClass.getDeclaredField(node.getName()) != node) {
            addError("The " + getDescription(node) + " is declared multiple times.", node);
        }
        checkInterfaceFieldModifiers(node);
        super.visitField(node);
    }

    public void visitProperty(PropertyNode node) {
        checkDuplicateProperties(node);
        super.visitProperty(node);
    }

    private void checkDuplicateProperties(PropertyNode node) {
        ClassNode cn = node.getDeclaringClass();
        String name = node.getName();
        String getterName = "get" + MetaClassHelper.capitalize(name);
        if (Character.isUpperCase(name.charAt(0))) {
            for (PropertyNode propNode : cn.getProperties()) {
                String otherName = propNode.getField().getName();
                String otherGetterName = "get" + MetaClassHelper.capitalize(otherName);
                if (node != propNode && getterName.equals(otherGetterName)) {
                    String msg = "The field " + name + " and " + otherName + " on the class " +
                            cn.getName() + " will result in duplicate JavaBean properties, which is not allowed";
                    addError(msg, node);
                }
            }
        }
    }

    private void checkInterfaceFieldModifiers(FieldNode node) {
        if (!currentClass.isInterface()) return;
        if ((node.getModifiers() & (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL)) == 0) {
            addError("The " + getDescription(node) + " is not 'public final static' but is defined in the " +
                    getDescription(currentClass) + ".", node);
        }
    }

    public void visitBinaryExpression(BinaryExpression expression) {
        if (expression.getOperation().getType() == Types.LEFT_SQUARE_BRACKET &&
                expression.getRightExpression() instanceof MapEntryExpression) {
            addError("You tried to use a map entry for an index operation, this is not allowed. " +
                    "Maybe something should be set in parentheses or a comma is missing?",
                    expression.getRightExpression());
        }
        super.visitBinaryExpression(expression);

        switch (expression.getOperation().getType()) {
            case Types.EQUAL: // = assignment
            case Types.BITWISE_AND_EQUAL:
            case Types.BITWISE_OR_EQUAL:
            case Types.BITWISE_XOR_EQUAL:
            case Types.PLUS_EQUAL:
            case Types.MINUS_EQUAL:
            case Types.MULTIPLY_EQUAL:
            case Types.DIVIDE_EQUAL:
            case Types.INTDIV_EQUAL:
            case Types.MOD_EQUAL:
            case Types.POWER_EQUAL:
            case Types.LEFT_SHIFT_EQUAL:
            case Types.RIGHT_SHIFT_EQUAL:
            case Types.RIGHT_SHIFT_UNSIGNED_EQUAL:
                checkFinalFieldAccess(expression.getLeftExpression());
                break;
            default:
                break;
        }
    }

    private void checkFinalFieldAccess(Expression expression) {
        if (!(expression instanceof VariableExpression)) return;
        VariableExpression ve = (VariableExpression) expression;
        Variable v = ve.getAccessedVariable();
        if (v instanceof FieldNode) {
            FieldNode fn = (FieldNode) v;
            int modifiers = fn.getModifiers();

            /*
             *  if it is static final but not accessed inside a static constructor, or,
             *  if it is an instance final but not accessed inside a instance constructor, it is an error
             */
            boolean isFinal = (modifiers & Opcodes.ACC_FINAL) != 0;
            boolean isStatic = (modifiers & Opcodes.ACC_STATIC) != 0;
            boolean error = isFinal && ((isStatic && !inStaticConstructor) || (!isStatic && !inConstructor));

            if (error) addError("cannot modify" + (isStatic ? " static" : "") + " final field '" + fn.getName() +
                    "' outside of " + (isStatic ? "static initialization block." : "constructor."), expression);
        }
    }

    public void visitConstructor(ConstructorNode node) {
        inConstructor = true;
        inStaticConstructor = node.isStaticConstructor();
        super.visitConstructor(node);
    }

    public void visitCatchStatement(CatchStatement cs) {
        if (!(cs.getExceptionType().isDerivedFrom(ClassHelper.make(Throwable.class)))) {
            addError("Catch statement parameter type is not a subclass of Throwable.", cs);
        }
        super.visitCatchStatement(cs);
    }

    public void visitMethodCallExpression(MethodCallExpression mce) {
        super.visitMethodCallExpression(mce);
        Expression aexp = mce.getArguments();
        if (aexp instanceof TupleExpression) {
            TupleExpression arguments = (TupleExpression) aexp;
            for (Expression e : arguments.getExpressions()) {
                checkForInvalidDeclaration(e);
            }
        } else {
            checkForInvalidDeclaration(aexp);
        }
    }

    private void checkForInvalidDeclaration(Expression exp) {
        if (!(exp instanceof DeclarationExpression)) return;
        addError("Invalid use of declaration inside method call.", exp);
    }

    public void visitConstantExpression(ConstantExpression expression) {
        super.visitConstantExpression(expression);
        checkStringExceedingMaximumLength(expression);
    }

    public void visitGStringExpression(GStringExpression expression) {
        super.visitGStringExpression(expression);
        for (ConstantExpression ce : expression.getStrings()) {
            checkStringExceedingMaximumLength(ce);
        }
    }

    private void checkStringExceedingMaximumLength(ConstantExpression expression) {
        Object value = expression.getValue();
        if (value instanceof String) {
            String s = (String) value;
            if (s.length() > 65535) {
                addError("String too long. The given string is " + s.length() + " Unicode code units long, but only a maximum of 65535 is allowed.", expression);
            }
        }
    }

}
