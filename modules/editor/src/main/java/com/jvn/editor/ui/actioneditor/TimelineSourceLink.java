package com.jvn.editor.ui.actioneditor;

/** Optimistic link to a script block. Refuses changed or ambiguous source text. */
public final class TimelineSourceLink {
    private String expected;

    public TimelineSourceLink(String source, int startLine) {
        int start = 0;
        for (int line = 0; line < startLine; line++) {
            int newline = source.indexOf('\n', start);
            if (newline < 0) throw new IllegalArgumentException("Timeline line no longer exists.");
            start = newline + 1;
        }
        int depth = 0;
        boolean opened = false, quoted = false, escaped = false, comment = false;
        for (int i = start; i < source.length(); i++) {
            char c = source.charAt(i);
            if (comment) { if (c == '\n') comment = false; continue; }
            if (quoted) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') quoted = false;
                continue;
            }
            if (c == '"') { quoted = true; continue; }
            if (c == '#' || (c == '/' && i + 1 < source.length() && source.charAt(i + 1) == '/')) {
                comment = true; continue;
            }
            if (c == '{') { depth++; opened = true; }
            if (c == '}' && opened && --depth == 0) {
                expected = source.substring(start, i + 1);
                return;
            }
        }
        throw new IllegalArgumentException("Timeline block is incomplete.");
    }

    public record Edit(int start, int end, String text) {}

    public int locate(String source) {
        int start = source.indexOf(expected);
        if (start < 0 || source.indexOf(expected, start + 1) >= 0)
            throw new IllegalStateException("The original timeline was changed, removed, or duplicated. Reopen it from the script before updating.");
        return start;
    }

    public Edit prepare(String source, String replacement) {
        int start = locate(source);
        String newline = expected.contains("\r\n") ? "\r\n" : "\n";
        String indent = expected.substring(0, expected.length() - expected.stripLeading().length());
        String text = indent + replacement.strip().replace("\r\n", "\n").replace("\n", newline + indent);
        return new Edit(start, start + expected.length(), text);
    }

    public void applied(Edit edit) { expected = edit.text(); }
}
