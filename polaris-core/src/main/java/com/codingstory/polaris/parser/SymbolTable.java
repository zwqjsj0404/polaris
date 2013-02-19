package com.codingstory.polaris.parser;

import com.codingstory.polaris.parser.ParserProtos.ClassType;
import com.codingstory.polaris.parser.ParserProtos.ClassTypeHandle;
import com.codingstory.polaris.parser.ParserProtos.Field;
import com.codingstory.polaris.parser.ParserProtos.Method;
import com.codingstory.polaris.parser.ParserProtos.FileHandle;
import com.codingstory.polaris.parser.ParserProtos.JumpTarget;
import com.codingstory.polaris.parser.ParserProtos.PrimitiveType;
import com.codingstory.polaris.parser.ParserProtos.Span;
import com.codingstory.polaris.parser.ParserProtos.Type;
import com.codingstory.polaris.parser.ParserProtos.TypeHandle;
import com.codingstory.polaris.parser.ParserProtos.TypeKind;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.LinkedList;
import java.util.Map;

import static com.codingstory.polaris.parser.TypeUtils.handleOf;

/**
 * Symbol table for types.
 */
public class SymbolTable {

    private static final Log LOG = LogFactory.getLog(SymbolTable.class);

    private final Map<String, ClassType> fullyNamedTypes = Maps.newHashMap();
    private final Map<ClassTypeHandle, ClassType> classesByHandle = Maps.newHashMap();
    private final Map<JumpTarget, ClassType> classesByJumpTarget = Maps.newHashMap();
    private final LinkedList<ClassType> classStack = Lists.newLinkedList();
    private String packageScope;
    private Map<String, String> imports;

    public static class Frame {
        private final Map<String, ClassType> shortlyNamedTypes = Maps.newHashMap();
        private final Map<String, TypeHandle> variableTypes = Maps.newHashMap();

        public void registerClassType(ClassType classType) {
            Preconditions.checkNotNull(classType);
            shortlyNamedTypes.put(classType.getHandle().getName(), classType);
        }

        public void registerVariable(TypeHandle type, String variable) {
            Preconditions.checkNotNull(type);
            Preconditions.checkNotNull(variable);
            variableTypes.put(variable, type);
        }

        public Iterable<ClassType> getClasses() {
            return shortlyNamedTypes.values();
        }

        public TypeHandle getVariableType(String variable) {
            return variableTypes.get(Preconditions.checkNotNull(variable));
        }
    }

    private final LinkedList<Frame> frames = Lists.newLinkedList();

    public SymbolTable() {
        frames.add(new Frame());
    }

    /**
     * Resolves primitive type or class.
     *
     * @return the resolved type on success, otherwise {@code null} */
    public Type resolveType(String name) {
        Preconditions.checkNotNull(name);
        PrimitiveType primitive = PrimitiveTypes.parse(name);
        if (primitive != null) {
            return Type.newBuilder()
                    .setKind(TypeKind.PRIMITIVE)
                    .setPrimitive(primitive)
                    .build();
        }
        ClassType clazz = resolveClass(name);
        if (clazz != null) {
            return Type.newBuilder()
                    .setKind(TypeKind.CLASS)
                    .setClazz(clazz)
                    .build();
        }
        return null;
    }

    /**
     * Resolves primitive or class, returning its handle.
     *
     * @return the hanlde of the resolved type on success, otherwise an unresolved type
     */
    public TypeHandle resolveTypeHandle(String name) {
        PrimitiveType primitive = resolvePrimitive(name);
        if (primitive != null) {
            return handleOf(primitive);
        }
        return handleOf(resolveClassHandle(name));
    }

    public PrimitiveType resolvePrimitive(String name) {
        return PrimitiveTypes.parse(name);
    }

    public ClassTypeHandle resolveClassHandle(String name) {
        ClassType clazz = resolveClass(name);
        if (clazz != null) {
            return clazz.getHandle();
        }

        String fullName = resolveImportAlias(name);
        if (fullName != null) {
            return ClassTypeHandle.newBuilder()
                    .setResolved(false)
                    .setName(fullName)
                    .build();
        }
        return ClassTypeHandle.newBuilder()
                .setResolved(false)
                .setName(name)
                .build();
    }

    public ClassType resolveClass(String name) {
        Preconditions.checkNotNull(name);
        ClassType clazz = null;
        String reason = null;
        do {
            if ((clazz = fullyNamedTypes.get(name)) != null) {
                reason = "fully qualified";
                break;
            }

            // search in imported packages
            String imported = resolveImportAlias(name);
            if (imported != null && (clazz = fullyNamedTypes.get(imported)) != null) {
                reason = "imported";
                break;
            }

            // search in same package
            if (!Strings.isNullOrEmpty(packageScope) &&
                    (clazz = fullyNamedTypes.get(packageScope + "." + name)) != null) {
                reason = "same package";
                break;
            }

            // search in default package
            clazz = fullyNamedTypes.get(name);
            if (clazz != null) {
                reason = "default package";
                break;
            }
        } while (false);

        if (clazz != null) {
            LOG.debug("Resolved " + name + " to " + clazz.getHandle() + " (" + reason + ")");
        } else {
            LOG.debug("Failed to resolve " + name);
        }
        return clazz;
    }

    public void registerClassType(ClassType classType) {
        if (frames.isEmpty()) {
            throw new IllegalStateException("No frames");
        }
        Preconditions.checkNotNull(classType);
        fullyNamedTypes.put(classType.getHandle().getName(), classType);
        classesByHandle.put(classType.getHandle(), classType);
        classesByJumpTarget.put(classType.getJumpTarget(), classType);
        currentFrame().registerClassType(classType);
    }

    public Frame currentFrame() {
        return frames.getFirst();
    }

    public ClassType currentClass() {
        return classStack.getFirst();
    }

    public void enterCompilationUnit(String pkg) {
        Preconditions.checkState(packageScope == null, "Cannot enterCompilationUnit twice");
        packageScope = pkg;
        imports = Maps.newHashMap();
        enterScope();
    }

    public void leaveCompilationUnit() {
        packageScope = null;
        imports = null;
        leaveScope();
    }

    public void enterScope() {
        frames.addFirst(new Frame());
    }

    /** {@code enterScope()} and register fields. */
    public void enterClassScope(ClassType clazz) {
        Preconditions.checkNotNull(clazz);
        enterScope();
        String prefix = clazz.getHandle().getName() + ".";
        for (Field field : clazz.getFieldsList()) {
            registerVariable(
                    field.getType(),
                    StringUtils.removeStart(field.getHandle().getName(), prefix));
        }
        classStack.push(clazz);
    }

    public void leaveClassScope() {
        leaveScope();
        classStack.pop();
    }

    /** {@code enterScope()} and registers parameters. */
    public void enterMethodScope(Method method) {
        Preconditions.checkNotNull(method);
        enterScope();
        for (Method.Parameter parameter : method.getParametersList()) {
            registerVariable(parameter.getType(), parameter.getName());
        }
    }

    public void leaveMethodScope() {
        leaveScope();
    }

    public void leaveScope() {
        frames.removeFirst();
    }

    public void registerImportPackage(String pkg) {
        Preconditions.checkNotNull(pkg);
        for (Frame frame : frames) {
            for (ClassType clazz : frame.getClasses()) {
                if (Objects.equal(TypeUtils.getPackage(clazz.getHandle().getName()), pkg)) {
                    registerImportClass(clazz.getHandle());
                }
            }
        }
    }

    public void registerImportClass(ClassTypeHandle handle) {
        Preconditions.checkNotNull(handle);
        String fullName = handle.getName();
        imports.put(TypeUtils.getSimpleName(fullName), fullName);
    }

    private String resolveImportAlias(String alias) {
        String result = imports.get(alias);
        if (result == null) {
            LOG.debug("Cannot resolve import alias " + alias);
            return null;
        } else {
            LOG.debug("Resolved import alias " + alias + " to " + result);
        }
        return result;
    }

    public void registerVariable(TypeHandle type, String variable) {
        currentFrame().registerVariable(type, variable);
    }

    public TypeHandle getVariableType(String variable) {
        Preconditions.checkNotNull(variable);
        for (Frame frame : frames) {
            TypeHandle type = frame.getVariableType(variable);
            if (type != null) {
                return type;
            }
        }
        return null;
    }

    public ClassType getClassTypeByLocation(FileHandle file, Span span) {
        Preconditions.checkNotNull(file);
        Preconditions.checkNotNull(span);
        JumpTarget jumpTarget = JumpTarget.newBuilder()
                .setFile(file)
                .setSpan(span)
                .build();
        return classesByJumpTarget.get(jumpTarget);
    }
    /** @return the class object uniquely determined by its handle. */
    public ClassType getClassByHandle(ClassTypeHandle handle) {
        Preconditions.checkNotNull(handle);
        return classesByHandle.get(handle);
    }

}
