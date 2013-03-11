package com.codingstory.polaris.parser;

import com.codingstory.polaris.parser.ParserProtos.ClassType;
import com.codingstory.polaris.parser.ParserProtos.ClassTypeHandle;
import com.codingstory.polaris.parser.ParserProtos.FileHandle;
import com.codingstory.polaris.parser.ParserProtos.JumpTarget;
import com.codingstory.polaris.parser.ParserProtos.Method;
import com.codingstory.polaris.parser.ParserProtos.MethodUsage;
import com.codingstory.polaris.parser.ParserProtos.Span;
import com.codingstory.polaris.parser.ParserProtos.TypeHandle;
import com.codingstory.polaris.parser.ParserProtos.TypeKind;
import com.codingstory.polaris.parser.ParserProtos.TypeUsage;
import com.codingstory.polaris.parser.ParserProtos.Usage;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import japa.parser.ast.ImportDeclaration;
import japa.parser.ast.body.AnnotationDeclaration;
import japa.parser.ast.body.ClassOrInterfaceDeclaration;
import japa.parser.ast.body.EnumDeclaration;
import japa.parser.ast.body.MethodDeclaration;
import japa.parser.ast.body.TypeDeclaration;
import japa.parser.ast.body.VariableDeclarator;
import japa.parser.ast.expr.Expression;
import japa.parser.ast.expr.MethodCallExpr;
import japa.parser.ast.expr.NameExpr;
import japa.parser.ast.expr.ObjectCreationExpr;
import japa.parser.ast.expr.ThisExpr;
import japa.parser.ast.expr.VariableDeclarationExpr;
import japa.parser.ast.visitor.VoidVisitorAdapter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.util.List;

import static com.codingstory.polaris.CollectionUtils.nullToEmptyCollection;
import static com.codingstory.polaris.parser.ParserUtils.dropGenericTypes;
import static com.codingstory.polaris.parser.ParserUtils.nodeJumpTarget;
import static com.codingstory.polaris.parser.ParserUtils.nodeSpan;
import static com.codingstory.polaris.parser.TypeUtils.handleOf;
import static com.codingstory.polaris.parser.TypeUtils.snippetLine;
import static com.codingstory.polaris.parser.TypeUtils.usageOf;

/** Generates cross references for local variable declarationmethod calls. */
public class ThirdPassProcessor {

    private static final Log LOG = LogFactory.getLog(ThirdPassProcessor.class);

    private static class ThirdPassVisitor extends VoidVisitorAdapter<Void> {
        private final FileHandle file;
        private final SymbolTable symbolTable;
        private final List<Usage> usages = Lists.newArrayList();
        private final String[] lines;

        private ThirdPassVisitor(FileHandle file, String source, SymbolTable symbolTable) {
            this.file = Preconditions.checkNotNull(file);
            this.symbolTable = Preconditions.checkNotNull(symbolTable);
            this.lines = source.split("\n");
        }

        @Override
        public void visit(ImportDeclaration node, Void arg) {
            Preconditions.checkNotNull(node);
            if (node.isAsterisk()) {
                symbolTable.registerImportPackage(node.getName().toString());
            } else {
                String name = node.getName().toString();
                ClassTypeHandle clazz = symbolTable.resolveClassHandle(name);
                symbolTable.registerImportClass(clazz);
            }
            super.visit(node, arg);
        }

        @Override
        public void visit(final ClassOrInterfaceDeclaration node, final Void arg) {
            Preconditions.checkNotNull(node);
            processTypeDeclaration(node, new Runnable() {
                @Override
                public void run() {
                    ThirdPassVisitor.super.visit(node, arg);
                }
            });
        }

        @Override
        public void visit(final EnumDeclaration node, final Void arg) {
            Preconditions.checkNotNull(node);
            processTypeDeclaration(node, new Runnable() {
                @Override
                public void run() {
                    ThirdPassVisitor.super.visit(node, arg);
                }
            });
        }

        @Override
        public void visit(final AnnotationDeclaration node, final Void arg) {
            Preconditions.checkNotNull(node);
            processTypeDeclaration(node, new Runnable() {
                @Override
                public void run() {
                    ThirdPassVisitor.super.visit(node, arg);
                }
            });
        }

        private void processTypeDeclaration(TypeDeclaration typeDeclaration, Runnable visitChildren) {
            ClassType clazz = symbolTable.getClassTypeByLocation(file, nodeSpan(typeDeclaration.getNameExpr()));
            if (clazz == null) {
                LOG.error("nodeSpan(typeDeclaration.getNameExpr()) = " + nodeSpan(typeDeclaration.getNameExpr()));
                throw new AssertionError("The class should have been discovered by previous passes "
                        + typeDeclaration.getName());
            }
            symbolTable.enterClassScope(clazz);
            visitChildren.run();
            symbolTable.leaveClassScope();
        }

        @Override
        public void visit(MethodDeclaration node, Void arg) {
            Preconditions.checkNotNull(node);
            Method method = findMethodBySpan(symbolTable.currentClass(), nodeSpan(node.getNameExpr()));
            if (method == null) {
                throw new AssertionError("This method should have been discovered by previous passes: "
                        + node.getName());
            }
            symbolTable.enterMethodScope(method);
            super.visit(node, arg);    //To change body of overridden methods use File | Settings | File Templates.
            symbolTable.leaveMethodScope();
        }

        private Method findMethodBySpan(ClassType clazz, Span span) {
            for (Method method : clazz.getMethodsList()) {
                if (Objects.equal(method.getJumpTarget().getSpan(), span)) {
                    return method;
                }
            }
            return null;
        }

        @Override
        public void visit(VariableDeclarationExpr node, Void arg) {
            Preconditions.checkNotNull(node);
            TypeHandle type = symbolTable.resolveTypeHandle(
                    dropGenericTypes(node.getType().toString()));
            ParserProtos.JumpTarget jumpTarget = nodeJumpTarget(file, node.getType());
            usages.add(TypeUtils.usageOf(TypeUsage.newBuilder()
                    .setType(type)
                    .setKind(TypeUsage.Kind.LOCAL_VARIABLE)
                    .build(), jumpTarget, snippetLine(lines, jumpTarget)));
            for (VariableDeclarator decl : node.getVars()) {
                String variableName = decl.getId().getName();
                if (type.getKind() == TypeKind.CLASS && !type.getClazz().getResolved()) {
                    LOG.debug("Skip unresolved class " + type.getClazz().getName()
                            + " when parsing " + file.getPath());
                    continue;
                }
                symbolTable.registerVariable(type ,variableName);
            }
            super.visit(node, arg);
        }

        @Override
        public void visit(MethodCallExpr node, Void arg) {
            Preconditions.checkNotNull(node);
            Expression scope = node.getScope();
            TypeHandle type = null;
            if (scope == null || scope instanceof ThisExpr) { // Call member methods
                type = handleOf(symbolTable.currentClass().getHandle());
            } else if (scope instanceof NameExpr) {
                String name = ((NameExpr) scope).getName();
                type = symbolTable.getVariableType(name);
                if (type == null) {
                    type = symbolTable.resolveTypeHandle(name);
                }
            } else {
                // TODO: Handle more complex expressions.
            }
            processMethodCall(
                    type,
                    node.getName(),
                    nullToEmptyCollection(node.getArgs()).size(),
                    MethodUsage.Kind.METHOD_CALL,
                    nodeJumpTarget(file, node.getNameExpr()));
            super.visit(node, arg);
        }

        @Override
        public void visit(ObjectCreationExpr node, Void arg) {
            Preconditions.checkNotNull(node);
            processMethodCall(
                    symbolTable.resolveTypeHandle(node.getType().getName()),
                    "<init>",
                    nullToEmptyCollection(node.getArgs()).size(),
                    MethodUsage.Kind.INSTANCE_CREATION,
                    nodeJumpTarget(file, node.getType()));
            super.visit(node, arg);
        }

        private void processMethodCall(TypeHandle type, String methodName, int argc,
                MethodUsage.Kind kind, JumpTarget jumpTarget) {
            if (type != null && type.getKind() == TypeKind.CLASS && type.getClazz().getResolved()) {
                ClassType clazz = symbolTable.getClassByHandle(type.getClazz());
                if (clazz != null) {
                    Method method = findMethodInClass(clazz, methodName, argc);
                    if (method != null) {
                        String snippet = snippetLine(lines, jumpTarget);
                        usages.add(usageOf(MethodUsage.newBuilder()
                                .setKind(kind)
                                .setMethod(method.getHandle())
                                .build(), jumpTarget, snippet));
                    }
                }
            }
        }

        private Method findMethodInClass(ClassType clazz, String methodName, int argumentCount) {
            List<Method> results = Lists.newArrayList();
            String prefix = clazz.getHandle().getName() + ".";
            for (Method method : clazz.getMethodsList()) {
                if (Objects.equal(method.getHandle().getName(), prefix + methodName) &&
                        method.getParametersCount() == argumentCount) {
                    results.add(method);
                }
            }
            if (results.size() == 1) {
                return Iterables.getOnlyElement(results);
            } else {
                return null; // TODO: Support overload.
            }
        }

        public List<Usage> getUsages() {
            return usages;
        }
    }

    public static List<Usage> extract(
            FileHandle file,
            String source,
            SymbolTable symbolTable,
            String pkg) throws IOException {
        Preconditions.checkNotNull(file);
        Preconditions.checkNotNull(source);
        Preconditions.checkNotNull(pkg);
        symbolTable.enterCompilationUnit(pkg);
        ThirdPassVisitor visitor = new ThirdPassVisitor(file, source, symbolTable);
        ParserUtils.safeVisit(source, visitor);
        symbolTable.leaveCompilationUnit();
        return visitor.getUsages();
    }
}
