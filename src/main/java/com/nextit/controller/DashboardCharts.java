package com.nextit.controller;

import com.nextit.model.Attendance;
import com.nextit.model.Grade;
import com.nextit.util.Ui;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Builds the small set of dashboard charts that add real value: attendance
 * composition, grade trends over time, and average performance by activity
 * type. Every chart degrades to a friendly message when there is no data.
 */
final class DashboardCharts {

    private static final List<String> ATTENDANCE_STATUSES =
            List.of("Present", "Late", "Excused", "Absent");

    private static final String[] DONUT_PALETTE = {
            "#991B1B", // Deep crimson
            "#1E293B", // Dark slate
            "#B45309", // Warm amber
            "#15803D", // Forest green
            "#2563EB", // Royal blue
            "#7C3AED"  // Violet
    };

    private DashboardCharts() {}

    static Node attendanceSummary(List<Attendance> records) {
        return attendanceSummary(records, null);
    }

    static Node attendanceSummary(List<Attendance> records, Runnable onClick) {
        if (records.isEmpty()) {
            return placeholder("Attendance Summary", "No attendance records yet.", onClick);
        }
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String status : ATTENDANCE_STATUSES) {
            long count = records.stream()
                    .filter(a -> status.equalsIgnoreCase(a.getStatus()))
                    .count();
            if (count > 0) counts.put(status, count);
        }
        if (counts.isEmpty()) {
            return placeholder("Attendance Summary", "No attendance records yet.", onClick);
        }

        PieChart chart = new PieChart();
        chart.setLegendSide(javafx.geometry.Side.BOTTOM);
        chart.setLabelsVisible(false);
        chart.setLegendVisible(true);
        chart.setAnimated(false);
        chart.setPrefHeight(190);
        chart.setMaxHeight(205);
        chart.setMinHeight(160);

        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        int idx = 0;
        for (Map.Entry<String, Long> entry : counts.entrySet()) {
            String status = entry.getKey();
            long count = entry.getValue();
            PieChart.Data d = new PieChart.Data(status + " (" + count + ")", count);
            chart.getData().add(d);
            final String color = DONUT_PALETTE[idx % DONUT_PALETTE.length];
            final double pct = (count * 100.0) / Math.max(1, total);
            java.util.function.Consumer<Node> setupNode = node -> {
                if (node != null) {
                    node.setStyle("-fx-pie-color: " + color + "; -fx-border-color: #FFFFFF; -fx-border-width: 2px; -fx-stroke: #FFFFFF; -fx-stroke-width: 2px;");
                    Tooltip tip = new Tooltip(String.format("%s\nCount: %d (%.1f%%)", status, count, pct));
                    tip.setShowDelay(javafx.util.Duration.millis(80));
                    Tooltip.install(node, tip);
                }
            };
            if (d.getNode() != null) {
                setupNode.accept(d.getNode());
            } else {
                d.nodeProperty().addListener((obs, oldN, newN) -> {
                    if (newN != null) setupNode.accept(newN);
                });
            }
            idx++;
        }
        return chartCard("Attendance Summary", chart, onClick);
    }

    static Node gradeTrend(List<Grade> grades) {
        return gradeTrend(grades, null);
    }

    static Node gradeTrend(List<Grade> grades, Runnable onClick) {
        Map<LocalDate, double[]> perDay = new TreeMap<>();
        for (Grade grade : grades) {
            LocalDateTime recordedAt = grade.getRecordedAt();
            Double value = grade.getGrade();
            if (recordedAt == null || value == null) continue;
            LocalDate day = recordedAt.toLocalDate();
            double[] totals = perDay.computeIfAbsent(day, ignored -> new double[2]);
            totals[0] += value;
            totals[1] += 1;
        }
        if (perDay.isEmpty()) {
            return placeholder("Grade Trend", "No graded activities yet.", onClick);
        }

        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Date recorded");
        NumberAxis yAxis = new NumberAxis(0, 100, 10);
        yAxis.setLabel("Grade (%)");
        LineChart<String, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setLegendVisible(false);
        chart.setCreateSymbols(true);
        var series = new LineChart.Series<String, Number>();
        series.setName("Average grade");
        perDay.forEach((day, totals) -> series.getData().add(
                new LineChart.Data<>(day.toString(), Math.round(totals[0] / totals[1] * 100.0) / 100.0)));
        chart.getData().add(series);
        return chartCard("Grade Trend", chart, onClick);
    }

    static Node averageByActivityType(List<Grade> grades) {
        return averageByActivityType(grades, null);
    }

    static Node averageByActivityType(List<Grade> grades, Runnable onClick) {
        Map<String, double[]> perType = new TreeMap<>();
        for (Grade grade : grades) {
            Double value = grade.getGrade();
            if (value == null) continue;
            String type = grade.getActivityType() == null || grade.getActivityType().isBlank()
                    ? "Unspecified" : grade.getActivityType();
            double[] totals = perType.computeIfAbsent(type, ignored -> new double[2]);
            totals[0] += value;
            totals[1] += 1;
        }
        if (perType.isEmpty()) {
            return placeholder("Average Grade by Activity Type", "No graded activities yet.", onClick);
        }

        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis(0, 100, 10);
        yAxis.setLabel("Average grade (%)");
        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
        chart.setLegendVisible(false);
        var series = new BarChart.Series<String, Number>();
        series.setName("Average grade");
        perType.forEach((type, totals) -> series.getData().add(
                new BarChart.Data<>(type, Math.round(totals[0] / totals[1] * 100.0) / 100.0)));
        chart.getData().add(series);
        return chartCard("Average Grade by Activity Type", chart, onClick);
    }

    static Node statusSummary(String title, Map<String, Long> statuses) {
        return donutSummary(title, null, statuses, null);
    }

    static Node statusSummary(String title, Map<String, Long> statuses, Runnable onClick) {
        return donutSummary(title, null, statuses, onClick);
    }

    static Node donutSummary(String title, String subtitle, Map<String, Long> statuses) {
        return donutSummary(title, subtitle, statuses, null);
    }

    static Node donutSummary(String title, String subtitle, Map<String, Long> statuses, Runnable onClick) {
        long total = statuses.values().stream().mapToLong(Long::longValue).sum();
        if (statuses.isEmpty() || total == 0) {
            return placeholder(title, subtitle, "No records yet.", onClick);
        }

        PieChart chart = new PieChart();
        chart.setLabelsVisible(false);
        chart.setLegendVisible(true);
        chart.setLegendSide(javafx.geometry.Side.BOTTOM);
        chart.setAnimated(false);
        chart.setPrefHeight(185);
        chart.setMaxHeight(195);
        chart.setMinHeight(155);

        int index = 0;
        for (Map.Entry<String, Long> entry : statuses.entrySet()) {
            String label = entry.getKey();
            long count = entry.getValue();
            PieChart.Data data = new PieChart.Data(label + " (" + count + ")", count);
            chart.getData().add(data);

            final String color = DONUT_PALETTE[index % DONUT_PALETTE.length];
            final double pct = (count * 100.0) / Math.max(1, total);

            java.util.function.Consumer<Node> setupNode = node -> {
                if (node != null) {
                    node.setStyle("-fx-pie-color: " + color + "; -fx-border-color: #FFFFFF; -fx-border-width: 2px; -fx-stroke: #FFFFFF; -fx-stroke-width: 2px;");
                    Tooltip tip = new Tooltip(String.format("%s\nCount: %d (%.1f%%)", label, count, pct));
                    tip.setShowDelay(javafx.util.Duration.millis(80));
                    tip.setStyle("-fx-font-size: 12px; -fx-font-weight: bold;");
                    Tooltip.install(node, tip);
                }
            };
            if (data.getNode() != null) {
                setupNode.accept(data.getNode());
            } else {
                data.nodeProperty().addListener((obs, oldN, newN) -> {
                    if (newN != null) setupNode.accept(newN);
                });
            }
            index++;
        }

        // Modern donut center badge
        StackPane donutHole = new StackPane();
        donutHole.setMouseTransparent(true);
        donutHole.setPrefSize(60, 60);
        donutHole.setMinSize(60, 60);
        donutHole.setMaxSize(60, 60);
        donutHole.setStyle(
                "-fx-background-color: #FAF7F2; " +
                "-fx-background-radius: 999; " +
                "-fx-border-color: rgba(74, 90, 106, 0.22); " +
                "-fx-border-radius: 999; " +
                "-fx-border-width: 1.5; " +
                "-fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.08), 6, 0, 0, 2);"
        );

        VBox badgeText = new VBox(0);
        badgeText.setAlignment(Pos.CENTER);
        Label totalNum = new Label(String.valueOf(total));
        totalNum.setStyle("-fx-font-size: 17px; -fx-font-weight: 900; -fx-text-fill: #1E293B;");
        Label totalLbl = new Label("TOTAL");
        totalLbl.setStyle("-fx-font-size: 9px; -fx-font-weight: 800; -fx-text-fill: #64748B; -fx-padding: -2 0 0 0;");
        badgeText.getChildren().addAll(totalNum, totalLbl);
        donutHole.getChildren().add(badgeText);

        // Compensate for bottom legend to center on the circle
        donutHole.setTranslateY(-14);

        StackPane chartStack = new StackPane(chart, donutHole);
        chartStack.setAlignment(Pos.CENTER);
        chartStack.setPrefHeight(185);
        chartStack.setMaxHeight(195);
        VBox.setVgrow(chartStack, Priority.NEVER);

        return chartCardWithSubtitle(title, subtitle, chartStack, onClick);
    }

    /** Case-insensitive grouping helper shared by the dashboards. */
    static Map<String, Long> countByStatus(List<String> statuses, List<String> known) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String status : known) {
            long count = statuses.stream().filter(status::equalsIgnoreCase).count();
            if (count > 0) counts.put(status, count);
        }
        statuses.stream()
                .filter(s -> s != null && known.stream().noneMatch(k -> k.equalsIgnoreCase(s)))
                .collect(Collectors.groupingBy(s -> s.toLowerCase(Locale.ROOT), Collectors.counting()))
                .forEach((key, count) -> counts.put(key, count));
        return counts;
    }

    static HBox row(Node... charts) {
        HBox row = new HBox(12, charts);
        row.setAlignment(Pos.TOP_LEFT);
        for (Node chart : charts) {
            HBox.setHgrow(chart, Priority.ALWAYS);
        }
        return row;
    }

    private static Node chartCardWithSubtitle(String title, String subtitle, Node chart, Runnable onClick) {
        VBox titles = new VBox(2);
        Label titleLabel = Ui.section(title);
        titles.getChildren().add(titleLabel);
        if (subtitle != null && !subtitle.isBlank()) {
            Label subLabel = new Label(subtitle);
            subLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: 500; -fx-text-fill: #64748B;");
            titles.getChildren().add(subLabel);
        }

        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        header.getChildren().addAll(titles, sp);

        Label arrow = null;
        if (onClick != null) {
            arrow = new Label("→");
            arrow.getStyleClass().add("card-arrow-hint");
            arrow.setStyle("-fx-font-size: 15px; -fx-font-weight: 900; -fx-text-fill: #991B1B; -fx-opacity: 0.35;");
            header.getChildren().add(arrow);
        }

        VBox box = new VBox(6, header, chart);
        box.getStyleClass().add("chart-card");
        box.setMaxWidth(Double.MAX_VALUE);
        box.setPrefHeight(275);
        box.setMaxHeight(295);
        box.setMinHeight(255);
        box.setAlignment(Pos.TOP_LEFT);

        if (onClick != null) {
            Ui.makeInteractive(box, arrow, onClick);
        }
        return box;
    }

    private static Node chartCard(String title, Node chart) {
        return chartCard(title, chart, null);
    }

    private static Node chartCard(String title, Node chart, Runnable onClick) {
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        Label titleLabel = Ui.section(title);
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        header.getChildren().addAll(titleLabel, sp);

        Label arrow = null;
        if (onClick != null) {
            arrow = new Label("→");
            arrow.getStyleClass().add("card-arrow-hint");
            arrow.setStyle("-fx-font-size: 15px; -fx-font-weight: 900; -fx-text-fill: #9E0B0F; -fx-opacity: 0.35;");
            header.getChildren().add(arrow);
        }

        VBox box = new VBox(6, header, chart);
        box.getStyleClass().add("chart-card");
        box.setMaxWidth(Double.MAX_VALUE);
        box.setPrefHeight(275);
        box.setMaxHeight(295);
        box.setMinHeight(255);
        box.setAlignment(Pos.TOP_LEFT);
        VBox.setVgrow(chart, Priority.ALWAYS);
        if (chart instanceof javafx.scene.chart.Chart fxChart) {
            fxChart.setPrefHeight(190);
            fxChart.setMaxHeight(205);
            fxChart.setMinHeight(160);
            fxChart.setAnimated(false);
        }

        if (onClick != null) {
            Ui.makeInteractive(box, arrow, onClick);
        }
        return box;
    }

    private static Node placeholder(String title, String message) {
        return placeholder(title, null, message, null);
    }

    private static Node placeholder(String title, String message, Runnable onClick) {
        return placeholder(title, null, message, onClick);
    }

    private static Node placeholder(String title, String subtitle, String message, Runnable onClick) {
        VBox titles = new VBox(2);
        Label titleLabel = Ui.section(title);
        titles.getChildren().add(titleLabel);
        if (subtitle != null && !subtitle.isBlank()) {
            Label subLabel = new Label(subtitle);
            subLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: 500; -fx-text-fill: #64748B;");
            titles.getChildren().add(subLabel);
        }

        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        header.getChildren().addAll(titles, sp);

        Label arrow = null;
        if (onClick != null) {
            arrow = new Label("→");
            arrow.getStyleClass().add("card-arrow-hint");
            arrow.setStyle("-fx-font-size: 15px; -fx-font-weight: 900; -fx-text-fill: #9E0B0F; -fx-opacity: 0.35;");
            header.getChildren().add(arrow);
        }

        Label label = new Label(message);
        label.getStyleClass().add("empty-state");
        VBox centerBox = new VBox(label);
        centerBox.setAlignment(Pos.CENTER);
        centerBox.setPrefHeight(185);

        VBox box = new VBox(6, header, centerBox);
        box.getStyleClass().add("chart-card");
        box.setMaxWidth(Double.MAX_VALUE);
        box.setPrefHeight(275);
        box.setMaxHeight(295);
        box.setMinHeight(255);

        if (onClick != null) {
            Ui.makeInteractive(box, arrow, onClick);
        }
        return box;
    }
}
