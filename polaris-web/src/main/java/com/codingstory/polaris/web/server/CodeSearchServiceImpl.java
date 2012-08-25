package com.codingstory.polaris.web.server;

import com.codingstory.polaris.search.Result;
import com.codingstory.polaris.search.SrcSearcher;
import com.codingstory.polaris.web.client.CodeSearchService;
import com.codingstory.polaris.web.shared.SearchResultDto;
import com.google.common.base.Function;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.gwt.user.server.rpc.RemoteServiceServlet;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.List;

public class CodeSearchServiceImpl extends RemoteServiceServlet implements CodeSearchService {

    private static final String CODE = "main() {\n    printf(\"Hello, world!\\n\");\n}";
    private static final Log LOG = LogFactory.getLog(CodeSearchServiceImpl.class);

    @Override
    public SearchResultDto search(String query) {
        try {
            Stopwatch stopwatch = new Stopwatch().start();
            SrcSearcher search = new SrcSearcher("index");
            List<Result> results = search.search(query, 100);
            SearchResultDto searchResultDto = new SearchResultDto();
            searchResultDto.setEntries(ImmutableList.copyOf(
                    Lists.transform(results, new Function<Result, SearchResultDto.Entry>() {
                        @Override
                        public SearchResultDto.Entry apply(Result result) {
                            return convertSearchResultToDtoEntry(result);
                        }
                    })));
            searchResultDto.setLatency(stopwatch.elapsedMillis());
            return searchResultDto;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String readFile(String fileName) {
        return CODE;
    }

    private SearchResultDto.Entry convertSearchResultToDtoEntry(Result result) {
        SearchResultDto.Entry e = new SearchResultDto.Entry();
        e.setFileName(result.getFilename());
        e.setSummary(result.getSummary());
        return e;
    }
}
