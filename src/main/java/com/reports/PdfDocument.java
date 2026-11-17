package com.reports;

import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class PdfDocument implements AutoCloseable {
    private static final float MARGIN = 44;
    private static final float BOTTOM = 58;
    private static final float LINE = 14;
    private static final Color INK = new Color(29, 53, 72);
    private static final Color BLUE = new Color(21, 91, 116);
    private final PDDocument document = new PDDocument();
    private final PDFont regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private final PDFont bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private final String organization;
    private final String title;
    private PDPageContentStream stream;
    private float y;

    public PdfDocument(String organization, String title, String issuedAt) throws IOException {
        this.organization = organization;
        this.title = title;
        document.getDocumentInformation().setTitle(title);
        document.getDocumentInformation().setAuthor(organization);
        newPage();
        paragraph("Emissão: " + issuedAt);
    }

    public void paragraph(String value) throws IOException {
        for (String line : wrap(value, regular, 10, width())) {
            ensure(LINE);
            text(line, MARGIN, y, regular, 10, INK);
            y -= LINE;
        }
        y -= 7;
    }

    public void field(String label, Object value) throws IOException {
        paragraph(label + ": " + (value == null || value.toString().isBlank() ? "Não informado" : value));
    }

    public void section(String value) throws IOException {
        ensure(60);
        y -= 10;
        text(clean(value, bold), MARGIN, y, bold, 12, BLUE);
        y -= 24;
    }

    public void table(List<String> headings, float[] weights, List<List<String>> rows) throws IOException {
        if (headings.size() != weights.length || rows.stream().anyMatch(row -> row.size() != weights.length)) {
            throw new IllegalArgumentException("Table column counts must match");
        }
        float total = 0;
        for (float weight : weights) total += weight;
        float[] widths = new float[weights.length];
        for (int i = 0; i < weights.length; i++) widths[i] = width() * weights[i] / total;
        List<List<String>> header = cells(headings, widths, bold);
        int headerLines = lineCount(header);
        float headerHeight = headerLines * LINE + 12;
        ensure(headerHeight + 26);
        drawCells(header, widths, 0, headerLines, true);
        for (List<String> row : rows) {
            List<List<String>> cells = cells(row, widths, regular);
            int lines = lineCount(cells);
            int offset = 0;
            while (offset < lines) {
                int available = (int) ((y - BOTTOM - 12) / LINE);
                // Move ordinary rows intact; exceptionally long cells continue on another page.
                if (available < 1 || (offset == 0 && lines > available
                        && lines * LINE + 12 <= PDRectangle.A4.getHeight() - 160 - BOTTOM - headerHeight)) {
                    newPage();
                    drawCells(header, widths, 0, headerLines, true);
                    available = (int) ((y - BOTTOM - 12) / LINE);
                }
                int count = Math.min(lines - offset, available);
                drawCells(cells, widths, offset, count, false);
                offset += count;
            }
        }
        if (rows.isEmpty()) paragraph("Nenhum registro encontrado para os filtros informados.");
        y -= 12;
    }

    public byte[] bytes() throws IOException {
        stream.close();
        stream = null;
        for (int i = 0; i < document.getNumberOfPages(); i++) {
            try (PDPageContentStream footer = new PDPageContentStream(document, document.getPage(i),
                    PDPageContentStream.AppendMode.APPEND, true, true)) {
                footer.beginText();
                footer.setFont(regular, 9);
                footer.setNonStrokingColor(INK);
                footer.newLineAtOffset(MARGIN, 30);
                footer.showText("Qualifica+ | " + (i + 1) + " / " + document.getNumberOfPages());
                footer.endText();
            }
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.save(output);
            return output.toByteArray();
        }
    }

    private void newPage() throws IOException {
        if (stream != null) stream.close();
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);
        stream = new PDPageContentStream(document, page);
        y = page.getMediaBox().getHeight() - MARGIN;
        for (String line : wrap(organization, bold, 12, width())) {
            text(line, MARGIN, y, bold, 12, BLUE);
            y -= 17;
        }
        y -= 10;
        for (String line : wrap(title, bold, 18, width())) {
            text(line, MARGIN, y, bold, 18, INK);
            y -= 24;
        }
        y -= 15;
    }

    private void ensure(float height) throws IOException {
        if (y - height < BOTTOM) newPage();
    }

    private List<List<String>> cells(List<String> values, float[] widths, PDFont font) throws IOException {
        List<List<String>> cells = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) cells.add(wrap(values.get(i), font, 9, widths[i] - 12));
        return cells;
    }

    private int lineCount(List<List<String>> cells) {
        return cells.stream().mapToInt(List::size).max().orElse(1);
    }

    private void drawCells(List<List<String>> cells, float[] widths, int offset, int count, boolean header)
            throws IOException {
        float height = count * LINE + 12;
        stream.setNonStrokingColor(header ? BLUE : new Color(244, 247, 249));
        stream.addRect(MARGIN, y - height + 3, width(), height);
        stream.fill();
        float x = MARGIN;
        for (int column = 0; column < widths.length; column++) {
            for (int line = 0; line < count; line++) {
                if (offset + line < cells.get(column).size()) {
                    text(cells.get(column).get(offset + line), x + 6, y - 10 - line * LINE,
                            header ? bold : regular, 9, header ? Color.WHITE : INK);
                }
            }
            x += widths[column];
        }
        y -= height + 2;
    }

    private List<String> wrap(String value, PDFont font, float size, float maxWidth) throws IOException {
        String valueClean = clean(value == null ? "Não informado" : value, font);
        List<String> result = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : valueClean.split("\\s+")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (font.getStringWidth(candidate) / 1000 * size <= maxWidth) {
                line = new StringBuilder(candidate);
                continue;
            }
            if (!line.isEmpty()) result.add(line.toString());
            line = new StringBuilder();
            for (char character : word.toCharArray()) {
                if (!line.isEmpty() && font.getStringWidth(line.toString() + character) / 1000 * size > maxWidth) {
                    result.add(line.toString());
                    line.setLength(0);
                }
                line.append(character);
            }
        }
        if (!line.isEmpty() || result.isEmpty()) result.add(line.toString());
        return result;
    }

    private String clean(String value, PDFont font) throws IOException {
        StringBuilder result = new StringBuilder();
        for (int codePoint : value.codePoints().toArray()) {
            String character = new String(Character.toChars(codePoint));
            if (Character.isWhitespace(codePoint) || Character.isISOControl(codePoint)) {
                result.append(' ');
            } else {
                try {
                    font.encode(character);
                    result.append(character);
                } catch (IllegalArgumentException unsupported) {
                    result.append('?');
                }
            }
        }
        return result.toString();
    }

    private void text(String value, float x, float baseline, PDFont font, float size, Color color) throws IOException {
        stream.beginText();
        stream.setFont(font, size);
        stream.setNonStrokingColor(color);
        stream.newLineAtOffset(x, baseline);
        stream.showText(value);
        stream.endText();
    }

    private float width() {
        return PDRectangle.A4.getWidth() - 2 * MARGIN;
    }

    @Override
    public void close() throws IOException {
        try {
            if (stream != null) stream.close();
        } finally {
            document.close();
        }
    }
}
