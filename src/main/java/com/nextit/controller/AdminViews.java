package com.nextit.controller;

import com.nextit.model.User;
import com.nextit.security.Session;
import com.nextit.service.ProfileService;
import com.nextit.util.Ui;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.*;

public final class AdminViews {

    private static final ProfileService profiles = new ProfileService();

    private AdminViews() {}

    public static Node dashboard() {
        return profile();
    }

    public static Node profile() {
        User account = profiles.account(Session.currentUser().getUserId());

        HBox hero = new HBox(20);
        hero.setAlignment(Pos.CENTER_LEFT);
        hero.getStyleClass().add("profile-hero-card");

        StackPane avatar = Ui.avatar(Ui.initials(account.getFullName()), "avatar-pink");

        VBox identityDetails = new VBox(6);
        Label nameLabel = new Label(value(account.getFullName()));
        nameLabel.getStyleClass().add("profile-hero-name");

        HBox badges = new HBox(8);
        badges.setAlignment(Pos.CENTER_LEFT);
        badges.getChildren().addAll(
                Ui.badge("@" + account.getUsername(), "badge-pill-cream"),
                Ui.badge(String.valueOf(account.getRole()), "badge-pill-pink")
        );

        Region goldSeparator = new Region();
        goldSeparator.getStyleClass().add("profile-gold-separator");
        VBox.setMargin(goldSeparator, new Insets(4, 0, 0, 0));

        identityDetails.getChildren().addAll(nameLabel, badges, goldSeparator);

        Region heroSpacer = new Region();
        HBox.setHgrow(heroSpacer, Priority.ALWAYS);

        VBox heroRight = new VBox(6);
        heroRight.setAlignment(Pos.CENTER_RIGHT);
        Label roleHighlight = new Label("Administrator Access");
        roleHighlight.getStyleClass().add("profile-hero-highlight-title");
        Label statusHighlight = new Label("Full System Clearance");
        statusHighlight.getStyleClass().add("profile-hero-highlight-subtitle");
        heroRight.getChildren().addAll(roleHighlight, statusHighlight);

        hero.getChildren().addAll(avatar, identityDetails, heroSpacer, heroRight);

        GridPane accountGrid = new GridPane();
        accountGrid.setHgap(16);
        accountGrid.setVgap(14);
        ColumnConstraints col1 = new ColumnConstraints();
        col1.setPercentWidth(50);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setPercentWidth(50);
        accountGrid.getColumnConstraints().addAll(col1, col2);

        accountGrid.add(Ui.propertyTile("Username", value(account.getUsername())), 0, 0);
        accountGrid.add(Ui.propertyTile("Email Address", value(account.getEmail())), 1, 0);
        accountGrid.add(Ui.propertyTile("Full Name", value(account.getFullName())), 0, 1);
        accountGrid.add(Ui.propertyTile("Account Role", Ui.badge(String.valueOf(account.getRole()), "badge-pill-pink")), 1, 1);

        VBox accountTray = Ui.tray("Administrator Credentials", "System authentication details and security clearance", accountGrid);
        HBox.setHgrow(accountTray, Priority.ALWAYS);

        return Ui.page("My Profile", "Your administrator account information.", hero, accountTray);
    }

    private static String value(String text) {
        return text == null ? "" : text;
    }
}
