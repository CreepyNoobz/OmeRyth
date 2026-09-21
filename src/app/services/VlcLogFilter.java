package app.services;

import java.io.OutputStream;
import java.io.PrintStream;

/**
 * Filtre applicatif interceptant les flux de sortie {@link System#out} et {@link System#err}
 * pour épurer la console des messages verbeux et non-bloquants émis par la bibliothèque C native LibVLC
 * (tels que les avertissements de cache de greffons obsolète ou d'options dépréciées).
 */
public class VlcLogFilter {
    private static boolean installed = false;

    /**
     * Installe un flux tamponné sur les sorties standard et d'erreur système.
     */
    public static void install() {
        if (installed) return;
        installed = true;

        final PrintStream originalErr = System.err;
        final PrintStream originalOut = System.out;

        System.setErr(new PrintStream(new OutputStream() {
            private final StringBuilder buffer = new StringBuilder();

            @Override
            // Concatène les octets reçus dans le tampon et traite la ligne à la rencontre de '\n'
            public void write(int b) {
                char ch = (char) b;
                if (ch == '\n') {
                    flushLine();
                } else if (ch != '\r') {
                    buffer.append(ch);
                }
            }

            @Override
            // Vide le tampon restant et délègue au flux d'erreur original
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
            // Concatène les octets reçus dans le tampon et traite la ligne à la rencontre de '\n'
            public void write(int b) {
                char ch = (char) b;
                if (ch == '\n') {
                    flushLine();
                } else if (ch != '\r') {
                    buffer.append(ch);
                }
            }

            @Override
            // Vide le tampon restant et délègue au flux standard original
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

    /**
     * Détermine si une ligne de log émise par LibVLC doit être filtrée pour garder une console épurée.
     */
    private static boolean shouldSuppressVlcLine(String line) {
        if (line == null || line.isBlank()) return false;
        String lower = line.toLowerCase();

        // Filtrer les messages verbeux internes LibVLC
        if (lower.contains("libvlc")) return true;
        // Filtrer les avertissements d'options dépréciées dans les nouvelles versions de VLC
        if (lower.startsWith("warning: option") && lower.contains("no longer exists")) return true;
        // Filtrer les avertissements bénins de cache de plugins
        if (lower.contains("stale plugins cache")) return true;
        // Filtrer les avertissements relatifs au chemin des greffons
        if (lower.contains("plugin-path")) return true;

        return false;
    }
}
