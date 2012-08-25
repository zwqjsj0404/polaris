package com.codingstory.polaris.web.client;

import com.google.common.base.Objects;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.http.client.URL;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.Map;

public class PageController {
    static {
        History.addValueChangeHandler(new ValueChangeHandler<String>() {
            @Override
            public void onValueChange(ValueChangeEvent<String> event) {
                handleHistoryToken(event.getValue());
            }
        });
    }

    public static void handleHistoryToken(String token) {
        Map<String, String> parameters = buildParameters(token);
        String page = parameters.get("p");
        if (Objects.equal(page, "search")) {
            doSwitchToSearchPage(parameters.get("q"));
        } else if (Objects.equal(page, "source")) {
            doSwitchToSourcePage(parameters.get("file"));
        } else {
            doSwitchToHomePage();
        }
    }

    private static Map<String, String> buildParameters(String token) {
        ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
        for (String part : Splitter.on("&").omitEmptyStrings().split(token)) {
            int eq = part.indexOf('=');
            if (eq >= part.length()) {
                builder.put(part, "");
            } else {
                String key = part.substring(0, eq);
                String value = part.substring(eq + 1);
                builder.put(key, value);
            }
        }
        return builder.build();
    }

    public static void switchToErrorPage(Throwable e) {
    }

    public static void switchToSearchResult(String query) {
        History.newItem("p=search&q=" + URL.encode(query));
    }

    public static void switchToViewSource(String fileName) {
        History.newItem("p=source&file=" + URL.encode(fileName));
    }

    private static void doSwitchToHomePage() {
        attachWidgetToRootPanel(new HomePage());
    }

    private static void doSwitchToSearchPage(String query) {
        attachWidgetToRootPanel(new SearchResultPage(query));
    }

    private static void doSwitchToSourcePage(String fileName) {
        ViewSourcePage viewSourcePage = new ViewSourcePage(fileName);
        attachWidgetToRootPanel(new ViewSourcePage(fileName));
    }

    private static void attachWidgetToRootPanel(Widget widget) {
        RootPanel rootPanel = RootPanel.get();
        rootPanel.clear();
        rootPanel.add(widget);
    }
}