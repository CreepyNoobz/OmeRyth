package app.services;

import java.io.OutputStream;
import java.io.PrintStream;

/**
 * Installs a lightweight PrintStream filter to suppress noisy libVLC lines
 * such as "stale plugins cache" that are not actionable for the user.
 *
 * This replaces System.err / System.out with a line-buffering stream that
 * filters matching lines before delegating to the original streams.
 */
public class VlcLogFilter {
    private static boolean installed = false;

    public static void install() {
        if (installed) return;
        installed = true;

        final PrintStream originalErr = System.err;
        final PrintStream originalOut = System.out;

        System.setErr(new PrintStream(new OutputStream() {
            private final StringBuilder buffer = new StringBuilder();

            @Override
            // Append character to buffer; flush on newline to process the line.
            public void write(int b) {
                char ch = (char) b;
                if (ch == '\n') {
                    flushLine();
                } else if (ch != '\r') {
                    buffer.append(ch);
                }
            }

            @Override
            // Flush any partial buffered line and delegate flush to the original stream.
            public void flush() {
                if (buffer.length() > 0) {
                    flushLine();
                }
                originalErr.flush();
            }

            private void flushLine() {
                String line = buffer.toString();
                buffer.setLength(0);

                if (shouldSuppressVlcLine(line)) {
                    return;
                }
                originalErr.println(line);
            }
        }, true));

        System.setOut(new PrintStream(new OutputStream() {
            private final StringBuilder buffer = new StringBuilder();

            @Override
            // Append character to buffer; flush on newline to process the line.
            public void write(int b) {
                char ch = (char) b;
                if (ch == '\n') {
                    flushLine();
                } else if (ch != '\r') {
                    buffer.append(ch);
                }
            }

            @Override
            // Flush any partial buffered line and delegate flush to the original stream.
            public void flush() {
                if (buffer.length() > 0) {
                    flushLine();
                }
                originalOut.flush();
            }

            private void flushLine() {
                String line = buffer.toString();
                buffer.setLength(0);

                if (shouldSuppressVlcLine(line)) {
                    return;
                }
                originalOut.println(line);
            }
        }, true));
    }

    private static boolean shouldSuppressVlcLine(String line) {
        if (line == null || line.isBlank()) return false;
        String lower = line.toLowerCase();

        // Suppress all libvlc error/warning lines from the VLC log
        if (lower.contains("libvlc")) return true;
        // Suppress "Warning: option --xxx no longer exists" from VLC
        if (lower.startsWith("warning: option") && lower.contains("no longer exists")) return true;
        // Suppress stale plugins cache lines
        if (lower.contains("stale plugins cache")) return true;
        // Suppress plugin-path related warnings
        if (lower.contains("plugin-path")) return true;

        return false;
    }
}
