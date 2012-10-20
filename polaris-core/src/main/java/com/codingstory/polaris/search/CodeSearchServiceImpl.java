package com.codingstory.polaris.search;

import com.codingstory.polaris.indexing.FileId;
import com.codingstory.polaris.indexing.TToken;
import com.codingstory.polaris.indexing.TTokenList;
import com.google.common.base.Preconditions;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.time.StopWatch;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;
import org.apache.thrift.TDeserializer;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.List;

import static com.codingstory.polaris.indexing.FieldName.*;

public class CodeSearchServiceImpl implements TCodeSearchService.Iface, Closeable {

    private static final Log LOG = LogFactory.getLog(CodeSearchServiceImpl.class);
    private final IndexReader reader;
    private final IndexSearcher searcher;
    private final SrcSearcher srcSearcher;

    public CodeSearchServiceImpl(File indexDirectory) throws IOException {
        Preconditions.checkNotNull(indexDirectory);
        Preconditions.checkArgument(indexDirectory.isDirectory());
        reader = IndexReader.open(FSDirectory.open(indexDirectory));
        searcher = new IndexSearcher(reader);
        srcSearcher = new SrcSearcher(reader);
    }

    @Override
    public TSearchResponse search(TSearchRequest req) throws TException {
        Preconditions.checkNotNull(req);
        TSearchResponse resp = new TSearchResponse();
        try {
            StopWatch stopWatch = new StopWatch();
            stopWatch.start();
            resp.setStatus(TStatusCode.OK);
            resp.setEntries(srcSearcher.search(req.getQuery(), 100));
            resp.setLatency(stopWatch.getTime());
        } catch (Exception e) {
            LOG.warn("Caught exception", e);
            resp.setStatus(TStatusCode.UNKNOWN_ERROR);
        }
        return resp;
    }

    @Override
    public TSourceResponse source(TSourceRequest req) throws TException {
        Preconditions.checkNotNull(req);
        TSourceResponse resp = new TSourceResponse();
        try {
            Query query;
            if (req.isSetFileId()) {
                FileId fileId = new FileId(req.getFileId());
                query = new TermQuery(new Term(FILE_ID, fileId.getValueAsString()));
            } else if (req.isSetProjectName() && req.isSetFileName()) {
                BooleanQuery booleanQuery = new BooleanQuery();
                booleanQuery.add(new TermQuery(new Term(PROJECT_NAME, req.getProjectName())), BooleanClause.Occur.MUST);
                booleanQuery.add(new TermQuery(new Term(FILE_NAME, req.getFileName())), BooleanClause.Occur.MUST);
                query = booleanQuery;
            } else {
                resp.setStatus(TStatusCode.MISSING_FIELDS);
                return resp;
            }
            ScoreDoc[] scoreDocs = searcher.search(query, 1).scoreDocs;
            if (scoreDocs.length > 1) {
                // TODO: log filename
                LOG.error("Found more than one source files matching");
                resp.setStatus(TStatusCode.UNKNOWN_ERROR);
                return resp;
            }
            if (scoreDocs.length == 0) {
                resp.setStatus(TStatusCode.FILE_NOT_FOUND);
                return resp;
            }

            int docId = scoreDocs[0].doc;
            Document doc = reader.document(docId);
            resp.setStatus(TStatusCode.OK);
            resp.setProjectName(doc.get(PROJECT_NAME));
            resp.setFileName(doc.get(FILE_NAME));
            resp.setContent(new String(doc.getBinaryValue(FILE_CONTENT)));
            resp.setTokens(deserializeTokens(doc.getBinaryValue(TOKENS)));
            resp.setAnnotations(doc.get(SOURCE_ANNOTATIONS));
            resp.setFileId(doc.getBinaryValue(FILE_ID));
        } catch (Exception e) {
            LOG.warn("Caught exception", e);
            resp.setStatus(TStatusCode.UNKNOWN_ERROR);
        }
        return resp;
    }

    @Override
    public TCompleteResponse complete(TCompleteRequest req) throws TException {
        Preconditions.checkNotNull(req);
        TCompleteResponse resp = new TCompleteResponse();
        try {
            resp.setStatus(TStatusCode.OK);
            resp.setEntries(srcSearcher.completeQuery(req.getQuery(), req.getLimit()));
        } catch (Exception e) {
            LOG.warn("Caught exception", e);
            resp.setStatus(TStatusCode.UNKNOWN_ERROR);
        }
        return resp;
    }

    private List<TToken> deserializeTokens(byte[] bytes) throws TException {
       TDeserializer deserializer = new TDeserializer(new TBinaryProtocol.Factory());
       TTokenList tokens = new TTokenList();
       deserializer.deserialize(tokens, bytes);
       return tokens.getTokens();
    }

    @Override
    public void close() throws IOException {
        IOUtils.closeQuietly(reader);
        IOUtils.closeQuietly(searcher);
        IOUtils.closeQuietly(srcSearcher);
    }
}
