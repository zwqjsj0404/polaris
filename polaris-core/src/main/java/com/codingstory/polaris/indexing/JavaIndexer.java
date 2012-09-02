package com.codingstory.polaris.indexing;

import com.codingstory.polaris.indexing.analysis.JavaSrcAnalyzer;
import com.codingstory.polaris.parser.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Fieldable;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.EnumSet;

import static com.codingstory.polaris.indexing.FieldName.*;

/**
 * Created with IntelliJ IDEA.
 * User: Zhou Yunqing
 * Date: 12-8-17
 * Time: 下午7:21
 * To change this template use File | Settings | File Templates.
 */
public class JavaIndexer implements Closeable {

    private static Log LOG = LogFactory.getLog(JavaIndexer.class);

    private final Directory indexDir;
    private final IndexWriterConfig config;
    private final IndexWriter writer;

    public JavaIndexer(File indexDir) throws IOException {
        this.indexDir = FSDirectory.open(indexDir);
        config = new IndexWriterConfig(Version.LUCENE_36, new JavaSrcAnalyzer());
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
        writer = new IndexWriter(this.indexDir, config);
    }

    private void addIndexFieldToDocument(Document document, String fieldName, String content) {
        Fieldable f = new Field(
                fieldName,
                content,
                Field.Store.YES,
                Field.Index.ANALYZED,
                Field.TermVector.WITH_POSITIONS_OFFSETS);
        f.setBoost(2.0f);
        document.add(f);
    }

    /**
     * Indexes file content.
     */
    public void indexFile(File file) throws IOException {
        LOG.debug("Indexing file content: " + file);
        String fileContent = FileUtils.readFileToString(file);
        Document contentDocument = new Document();
        contentDocument.add(new Field(FILE_CONTENT, fileContent, Field.Store.YES, Field.Index.NO));
        contentDocument.add(new Field(FILE_NAME, file.getName(), Field.Store.YES, Field.Index.NOT_ANALYZED));
        writer.addDocument(contentDocument);
    }

    public void indexToken(File file, Token t) throws IOException {
        Document document = new Document();
        document.add(new Field(FILE_NAME, file.getName(), Field.Store.YES, Field.Index.NO));
        String offset = Long.toString(t.getSpan().getFrom());
        document.add(new Field(OFFSET, offset, Field.Store.YES, Field.Index.NO));
        document.add(new Field(KIND, t.getKind().toString(), Field.Store.YES, Field.Index.NO));
        if (EnumSet.of(Token.Kind.CLASS_DECLARATION,
                Token.Kind.INTERFACE_DECLARATION,
                Token.Kind.ENUM_DECLARATION,
                Token.Kind.ANNOTATION_DECLARATION).contains(t.getKind())) {
            TypeDeclaration declaration = (TypeDeclaration) t;
            processType(document, declaration);
        } else if (t.getKind() == Token.Kind.METHOD_DECLARATION) {
            MethodDeclaration declaration = (MethodDeclaration) t;
            processMethod(document, declaration);
        } else if (t.getKind() == Token.Kind.PACKAGE_DECLARATION) {
            PackageDeclaration declaration = (PackageDeclaration) t;
            addIndexFieldToDocument(document, PACKAGE_NAME, declaration.getPackageName());
        } else if (t.getKind() == Token.Kind.FIELD_DECLARATION) {
            FieldDeclaration declaration = (FieldDeclaration) t;
            processField(document, declaration);
        }
        writer.addDocument(document);
    }

    private void processField(Document document, FieldDeclaration declaration) {
        addIndexFieldToDocument(document, FIELD_NAME, declaration.getVariableName());
        String fullName = declaration.getPackageName() + "." + declaration.getClassName() + "."
                + declaration.getVariableName();
        addIndexFieldToDocument(document, FIELD_NAME, fullName);
        TypeReference type = declaration.getTypeReferenece();
        addIndexFieldToDocument(document, FIELD_TYPE_NAME, type.getUnqualifiedName());
        if (type.isResoleved()) {
            ResolvedTypeReference resolved = (ResolvedTypeReference) type;
            addIndexFieldToDocument(document, FIELD_TYPE_NAME, resolved.getName().toString());
        } else {
            // TODO: Bug? The following lines result in bad ranking.
//            UnresolvedTypeReferenece unresolved = (UnresolvedTypeReferenece) type;
//            for (FullyQualifiedName candidate : unresolved.getCandidates()) {
//                addIndexFieldToDocument(document, FIELD_TYPE_NAME, candidate.toString());
//            }
        }
    }

    private void processMethod(Document document, MethodDeclaration declaration) {
        String name = declaration.getMethodName();
        addIndexFieldToDocument(document, METHOD_NAME, name);
        String fullName = declaration.getPackageName() + "." + declaration.getClassName() + "." + name;
        addIndexFieldToDocument(document, METHOD_NAME, fullName);
    }

    private void processType(Document document, TypeDeclaration declaration) {
        FullyQualifiedName name = declaration.getName();
        addIndexFieldToDocument(document, TYPE_NAME, name.getTypeName());
        String fullName = name.getTypeName();
        if (name.hasPackageName())
            fullName = name.getPackageName() + "." + fullName;
        addIndexFieldToDocument(document, TYPE_NAME, fullName);
        if (declaration.hasJavaDocComment()) {
            addIndexFieldToDocument(document, JAVA_DOC, declaration.getJavaDocComment());
        }
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }
}
