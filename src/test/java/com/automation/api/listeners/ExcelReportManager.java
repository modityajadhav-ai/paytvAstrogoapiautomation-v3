package com.automation.api.listeners;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Collects test results during a suite run and writes a formatted .xlsx report.
 *
 * Report path: target/excel-reports/TestReport_<timestamp>.xlsx
 *
 * Sheet 1 — Summary  : pass/fail/skip counts + pass percentage
 * Sheet 2 — Results  : one row per test with class, method, status, duration, error
 */
public class ExcelReportManager {

    private static final Logger LOG = LoggerFactory.getLogger(ExcelReportManager.class);
    private static final String REPORT_DIR = "target/excel-reports";
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    /** Immutable snapshot of one test's outcome. */
    public record TestRecord(
            String className,
            String methodName,
            String status,
            long durationMs,
            String errorMessage
    ) {}

    private static final List<TestRecord> RESULTS = new ArrayList<>();

    private ExcelReportManager() {}

    public static synchronized void addResult(TestRecord record) {
        RESULTS.add(record);
    }

    /** Called from {@link TestListener#onFinish} — writes the workbook to disk. */
    public static synchronized void flush() {
        try {
            File dir = new File(REPORT_DIR);
            dir.mkdirs();
            String fileName = "TestReport_" + LocalDateTime.now().format(TS_FMT) + ".xlsx";
            File outFile = new File(dir, fileName);

            try (XSSFWorkbook wb = new XSSFWorkbook();
                 FileOutputStream fos = new FileOutputStream(outFile)) {

                buildSummarySheet(wb);
                buildResultsSheet(wb);
                wb.write(fos);
            }

            LOG.info("Excel report written → {}", outFile.getPath());
        } catch (Exception e) {
            LOG.error("Failed to write Excel report", e);
        }
    }

    // ── Sheet 1: Summary ──────────────────────────────────────────────────────

    private static void buildSummarySheet(XSSFWorkbook wb) {
        Sheet sheet = wb.createSheet("Summary");
        sheet.setColumnWidth(0, 6000);
        sheet.setColumnWidth(1, 4000);

        CellStyle titleStyle  = titleStyle(wb);
        CellStyle headerStyle = headerStyle(wb);
        CellStyle passStyle   = statusStyle(wb, new byte[]{(byte) 0x2E, (byte) 0x86, (byte) 0x3A}); // green
        CellStyle failStyle   = statusStyle(wb, new byte[]{(byte) 0xC0, (byte) 0x39, (byte) 0x2B}); // red
        CellStyle skipStyle   = statusStyle(wb, new byte[]{(byte) 0xF3, (byte) 0x9C, (byte) 0x12}); // orange
        CellStyle dataStyle   = dataStyle(wb);

        long passed  = RESULTS.stream().filter(r -> "PASS".equals(r.status())).count();
        long failed  = RESULTS.stream().filter(r -> "FAIL".equals(r.status())).count();
        long skipped = RESULTS.stream().filter(r -> "SKIP".equals(r.status())).count();
        long total   = RESULTS.size();
        double passPercent = total == 0 ? 0 : (passed * 100.0 / total);

        int row = 0;

        // Title
        Row titleRow = sheet.createRow(row++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("API Automation — Test Execution Summary");
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 1));

        row++; // blank row

        // Header row
        Row hdr = sheet.createRow(row++);
        createCell(hdr, 0, "Metric",   headerStyle);
        createCell(hdr, 1, "Value",    headerStyle);

        // Data rows
        createDataRow(sheet, row++, "Total Tests",  String.valueOf(total),   dataStyle);
        createDataRow(sheet, row++, "Passed",        String.valueOf(passed),  passStyle);
        createDataRow(sheet, row++, "Failed",        String.valueOf(failed),  failStyle);
        createDataRow(sheet, row++, "Skipped",       String.valueOf(skipped), skipStyle);
        createDataRow(sheet, row,   "Pass %",        String.format("%.1f%%", passPercent), dataStyle);
    }

    // ── Sheet 2: Results ──────────────────────────────────────────────────────

    private static void buildResultsSheet(XSSFWorkbook wb) {
        Sheet sheet = wb.createSheet("Test Results");
        sheet.setColumnWidth(0, 1500);   // Sr No
        sheet.setColumnWidth(1, 7000);   // Class Name
        sheet.setColumnWidth(2, 9000);   // Test Method
        sheet.setColumnWidth(3, 3000);   // Status
        sheet.setColumnWidth(4, 3500);   // Duration
        sheet.setColumnWidth(5, 18000);  // Error Message

        CellStyle headerStyle = headerStyle(wb);
        CellStyle passStyle   = statusStyle(wb, new byte[]{(byte) 0x2E, (byte) 0x86, (byte) 0x3A});
        CellStyle failStyle   = statusStyle(wb, new byte[]{(byte) 0xC0, (byte) 0x39, (byte) 0x2B});
        CellStyle skipStyle   = statusStyle(wb, new byte[]{(byte) 0xF3, (byte) 0x9C, (byte) 0x12});
        CellStyle dataStyle   = dataStyle(wb);
        CellStyle wrapStyle   = wrapStyle(wb);

        // Header
        Row hdr = sheet.createRow(0);
        sheet.createFreezePane(0, 1);
        String[] headers = {"#", "Class", "Test Method", "Status", "Duration (ms)", "Error Message"};
        for (int i = 0; i < headers.length; i++) {
            createCell(hdr, i, headers[i], headerStyle);
        }

        // Auto-filter on header row
        sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, headers.length - 1));

        // Data rows
        int rowNum = 1;
        for (TestRecord r : RESULTS) {
            Row row = sheet.createRow(rowNum);
            CellStyle statusStyle = switch (r.status()) {
                case "PASS" -> passStyle;
                case "FAIL" -> failStyle;
                default     -> skipStyle;
            };

            createCell(row, 0, String.valueOf(rowNum), dataStyle);
            createCell(row, 1, r.className(),           dataStyle);
            createCell(row, 2, r.methodName(),          dataStyle);
            createCell(row, 3, r.status(),              statusStyle);
            createCell(row, 4, String.valueOf(r.durationMs()), dataStyle);
            createCell(row, 5, r.errorMessage() != null ? r.errorMessage() : "", wrapStyle);
            rowNum++;
        }
    }

    // ── Style helpers ─────────────────────────────────────────────────────────

    private static CellStyle titleStyle(XSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 14);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private static CellStyle headerStyle(XSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        setBorder(style);
        return style;
    }

    private static CellStyle statusStyle(XSSFWorkbook wb, byte[] rgb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        org.apache.poi.xssf.usermodel.XSSFCellStyle xStyle = (org.apache.poi.xssf.usermodel.XSSFCellStyle) style;
        xStyle.setFillForegroundColor(new org.apache.poi.xssf.usermodel.XSSFColor(rgb, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        setBorder(style);
        return style;
    }

    private static CellStyle dataStyle(XSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        setBorder(style);
        return style;
    }

    private static CellStyle wrapStyle(XSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setWrapText(true);
        setBorder(style);
        return style;
    }

    private static void setBorder(CellStyle style) {
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }

    private static void createCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private static void createDataRow(Sheet sheet, int rowNum, String label, String value, CellStyle style) {
        Row row = sheet.createRow(rowNum);
        CellStyle labelStyle = dataStyle((XSSFWorkbook) sheet.getWorkbook());
        Font f = sheet.getWorkbook().createFont();
        f.setBold(true);
        labelStyle.setFont(f);
        setBorder(labelStyle);
        createCell(row, 0, label, labelStyle);
        createCell(row, 1, value, style);
    }
}
