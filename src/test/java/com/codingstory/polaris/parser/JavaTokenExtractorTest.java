package com.codingstory.polaris.parser;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.io.NullOutputStream;
import org.apache.commons.io.IOUtils;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.Assert.*;

public class JavaTokenExtractorTest {

    @Test
    public void testEmpty() throws IOException {
        List<Token> tokens = extractTokensFromCode("");
        assertTrue(tokens.isEmpty());
    }

    @Test
    public void testPackage() throws IOException {
        String code = "package com.example.pkg;\n";
        List<Token> tokens = extractTokensFromCode(code);
        PackageDeclaration pkg = findUniqueTokenOfKind(tokens, Token.Kind.PACKAGE_DECLARATION);
        assertEquals(Token.Kind.PACKAGE_DECLARATION, pkg.getKind());
        assertEquals(Token.Span.of(0, 24), pkg.getSpan());
        assertEquals("com.example.pkg", pkg.getPackageName());
    }

    @Test
    public void testClass() throws IOException {
        String code = "package pkg;\npublic class MyClass { /* body */ }\n";
        List<Token> tokens = extractTokensFromCode(code);
        ClassDeclaration clazz = findUniqueTokenOfKind(tokens, Token.Kind.CLASS_DECLARATION);
        assertEquals(Token.Kind.CLASS_DECLARATION, clazz.getKind());
        assertEquals(Token.Span.of(13, 48), clazz.getSpan());
        assertEquals(FullyQualifiedName.of("pkg.MyClass"), clazz.getName());
    }

    @Test
    public void testClass_multiple() throws IOException {
        String code = "package pkg;\nclass A {}\nclass B {}\nclass C {}\n";
        List<Token> tokens = extractTokensFromCode(code);
        List<ClassDeclaration> classes = filterTokensOfKind(tokens, Token.Kind.CLASS_DECLARATION);
        assertEquals(ImmutableList.of("pkg.A", "pkg.B", "pkg.C"),
                Lists.transform(classes,
                        new Function<ClassDeclaration, String>() {
                            @Override
                            public String apply(ClassDeclaration clazz) {
                                Preconditions.checkNotNull(clazz);
                                return clazz.getName().toString();
                            }
                        }));
    }

    @Test
    public void testClass_noPackage() throws IOException {
        String code = "class A {}";
        List<Token> tokens = extractTokensFromCode(code);
        ClassDeclaration clazz = findUniqueTokenOfKind(tokens, Token.Kind.CLASS_DECLARATION);
        assertEquals(FullyQualifiedName.of("A"), clazz.getName());
    }

    // TODO: testClass_public
    // TODO: testClass_private
    // TODO: testClass_packagePrivate
    // TODO: testClass_abstract
    // TODO: testClass_final
    // TODO: testNestedClass
    // TODO: testNestedClass_static

    @Test
    public void testEnum() throws IOException {
        String code = "package pkg; public enum E { /* some values */ }";
        List<Token> tokens = extractTokensFromCode(code);
        EnumDeclaration e = findUniqueTokenOfKind(tokens, Token.Kind.ENUM_DECLARATION);
        assertNotNull(e);
        assertEquals(Token.Span.of(13, 48), e.getSpan());
        assertEquals(FullyQualifiedName.of("pkg.E"), e.getName());
    }

    // TODO: testEnum_public
    // TODO: testEnum_private
    // TODO: testEnum_packagePrivate
    // TODO: testEnumValues

    @Test
    public void testMethod() throws IOException {
        String code = "package pkg; class A { void func() {} }";
        List<Token> tokens = extractTokensFromCode(code);
        MethodDeclaration method = findUniqueTokenOfKind(tokens, Token.Kind.METHOD_DECLARATION);
        assertEquals(Token.Kind.METHOD_DECLARATION, method.getKind());
        assertEquals(Token.Span.of(23, 37), method.getSpan());
        assertEquals("pkg", method.getPackageName());
        assertEquals("A", method.getClassName());
        assertEquals("func", method.getMethodName());
    }

    @Test
    public void testMethod_inEnum() throws IOException {
        String code = "enum E { SOME_VALUE; void f() {} }";
        List<Token> tokens = extractTokensFromCode(code);
        MethodDeclaration method = findUniqueTokenOfKind(tokens, Token.Kind.METHOD_DECLARATION);
        assertEquals("E", method.getClassName());
        assertEquals("f", method.getMethodName());
    }

    // TODO: testMethod_public
    // TODO: testMethod_private
    // TODO: testMethod_protected
    // TODO: testMethod_packagePrivate
    // TODO: testMethod_final
    // TODO: testMethod_static

    @Test
    public void testField() throws IOException {
        String code = "package pkg; class A { int n; }";
        List<Token> tokens = extractTokensFromCode(code);
        FieldDeclaration field = findUniqueTokenOfKind(tokens, Token.Kind.FIELD_DECLARATION);
        assertEquals(Token.Kind.FIELD_DECLARATION, field.getKind());
        assertEquals(Token.Span.of(27, 28), field.getSpan());
        assertEquals("pkg", field.getPackageName());
        assertEquals("A", field.getClassName());
        assertEquals("n", field.getVariableName());
        assertEquals(ResolvedTypeReference.INTEGER, field.getTypeReferenece());
    }

    @Test
    public void testField_fullyQualifiedType() throws IOException {
        String code = "class A { java.util.List l; }";
        List<Token> tokens = extractTokensFromCode(code);
        FieldDeclaration field = findUniqueTokenOfKind(tokens, Token.Kind.FIELD_DECLARATION);
        assertEquals(Token.Kind.FIELD_DECLARATION, field.getKind());
        assertEquals("l", field.getVariableName());
        assertEquals(new UnresolvedTypeReferenece(ImmutableList.of(FullyQualifiedName.of("java.util.List"))),
                field.getTypeReferenece());
    }

    // TODO: testField_multiple

    @Test
    public void testLineMonitorInputStream() throws IOException {
        String code = "a\nbcd\nef\ng\nhij\n";
        JavaTokenExtractor.LineMonitorInputStream in = new JavaTokenExtractor.LineMonitorInputStream(
                new ByteArrayInputStream(code.getBytes()));
        IOUtils.copy(in, new NullOutputStream());
        assertEquals(0, in.translateLineColumnToOffset(0, 0));
        assertEquals(2, in.translateLineColumnToOffset(1, 0));
        assertEquals(3, in.translateLineColumnToOffset(1, 1));
        assertEquals(4, in.translateLineColumnToOffset(1, 2));
        assertEquals(6, in.translateLineColumnToOffset(2, 0));
        assertEquals(7, in.translateLineColumnToOffset(2, 1));
        assertEquals(9, in.translateLineColumnToOffset(3, 0));
        assertEquals(11, in.translateLineColumnToOffset(4, 0));
        assertEquals(12, in.translateLineColumnToOffset(4, 1));
        assertEquals(13, in.translateLineColumnToOffset(4, 2));
    }

    private static List<Token> extractTokensFromCode(String code) throws IOException {
        return new JavaTokenExtractor()
                .setInputStream(new ByteArrayInputStream(code.getBytes()))
                .extractTokens();
    }

    private static <T extends Token> T findUniqueTokenOfKind(List<Token> tokens, final Token.Kind kind) {
        return (T) Iterables.getOnlyElement(Iterables.filter(tokens, new Predicate<Token>() {
            @Override
            public boolean apply(Token token) {
                return token.getKind() == kind;
            }
        }));
    }

    private static <T extends Token> List<T> filterTokensOfKind(List<Token> tokens, final Token.Kind kind) {
        Iterable<Token> filtered = Iterables.filter(tokens, new Predicate<Token>() {
            @Override
            public boolean apply(Token token) {
                Preconditions.checkNotNull(token);
                return token.getKind() == kind;
            }
        });
        Iterable<T> converted = Iterables.transform(filtered, new Function<Token, T>() {
            @Override
            public T apply(Token token) {
                Preconditions.checkNotNull(token);
                return (T) token;
            }
        });
        return ImmutableList.copyOf(converted);
    }


}
