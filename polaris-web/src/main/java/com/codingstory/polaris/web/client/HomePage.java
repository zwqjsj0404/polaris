package com.codingstory.polaris.web.client;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.TextBox;

public class HomePage extends Composite {
    interface MyUiBinder extends UiBinder<HTMLPanel, HomePage> {
    }
    private static final MyUiBinder UI_BINDER = GWT.create(MyUiBinder.class);

    @UiField
    TextBox searchBox;
    @UiField
    Button searchButton;

    public HomePage() {
        initWidget(UI_BINDER.createAndBindUi(this));
    }

    @UiHandler("searchButton")
    void onSearchButton(ClickEvent event) {
        String query = searchBox.getText();
        PageController.switchToSearchResult(query);
    }
}