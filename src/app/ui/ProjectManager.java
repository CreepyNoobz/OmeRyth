package app.ui;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProjectManager {

    private static final String JSON_VERSION = "RHYTHMO_V4";

    public static class LoadedProject {
        public final File videoFile;
        public final int bandCount;

        public LoadedProject(File videoFile, int bandCount) {
            this.videoFile = videoFile;
            this.bandCount = bandCount;
        }
    }

    public static void save(File file, File videoFile, TextManager textManager, ArrayList<Role> roles, int bandCount) {
        try {
            String json = toJson(videoFile, textManager, roles, bandCount);
            Files.writeString(file.toPath(), json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static LoadedProject load(File file, TextManager textManager, ArrayList<Role> roles) {
        try {
            String raw = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            String trimmed = raw.trim();
            if (trimmed.startsWith("{")) {
                return loadFromJson(trimmed, textManager, roles);
            }
            return loadLegacy(raw, textManager, roles);
        } catch (Exception e) {
            e.printStackTrace();
            return new LoadedProject(null, 4);
        }
    }

    private static String toJson(File videoFile, TextManager textManager, ArrayList<Role> roles, int bandCount) {
        StringBuilder sb = new StringBuilder(4096);
        sb.append("{\n");
        sb.append("  \"version\": \"").append(JSON_VERSION).append("\",\n");
        if (videoFile != null) {
            sb.append("  \"video\": \"").append(escape(videoFile.getAbsolutePath())).append("\",\n");
        } else {
            sb.append("  \"video\": null,\n");
        }
        sb.append("  \"bandCount\": ").append(Math.max(1, bandCount)).append(",\n");

        sb.append("  \"roles\": [\n");
        for (int i = 0; i < roles.size(); i++) {
            Role r = roles.get(i);
            sb.append("    {\"name\": \"").append(escape(r.name)).append("\", \"color\": ").append(r.color.getRGB()).append("}");
            if (i < roles.size() - 1) sb.append(',');
            sb.append('\n');
        }
        sb.append("  ],\n");

        ArrayList<TextItem> texts = textManager.getTexts();
        sb.append("  \"texts\": [\n");
        for (int i = 0; i < texts.size(); i++) {
            TextItem t = texts.get(i);
            String roleName = t.role == null ? "" : t.role.name;
            sb.append("    {\"text\": \"").append(escape(t.text)).append("\", \"x\": ").append(t.x)
                    .append(", \"band\": ").append(t.band)
                    .append(", \"role\": \"").append(escape(roleName)).append("\"}");
            if (i < texts.size() - 1) sb.append(',');
            sb.append('\n');
        }
        sb.append("  ],\n");

        sb.append("  \"separators\": [\n");
        ArrayList<String> separatorLines = new ArrayList<>();
        for (Map.Entry<Integer, ArrayList<SeparatorMark>> entry : textManager.getBandSeparators().entrySet()) {
            int band = entry.getKey();
            for (SeparatorMark sep : entry.getValue()) {
                separatorLines.add("    {\"band\": " + band + ", \"x\": " + sep.x
                        + ", \"type\": \"" + sep.type.name() + "\", \"splitIndex\": " + sep.splitIndex + "}");
            }
        }
        for (int i = 0; i < separatorLines.size(); i++) {
            sb.append(separatorLines.get(i));
            if (i < separatorLines.size() - 1) sb.append(',');
            sb.append('\n');
        }
        sb.append("  ]\n");
        sb.append("}\n");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static LoadedProject loadFromJson(String json, TextManager textManager, ArrayList<Role> roles) {
        Object parsed = new JsonParser(json).parseValue();
        if (!(parsed instanceof Map)) {
            throw new IllegalArgumentException("Format JSON invalide");
        }

        Map<String, Object> root = (Map<String, Object>) parsed;
        textManager.clearAll();
        roles.clear();

        Object videoRaw = root.get("video");
        File videoFile = (videoRaw instanceof String && !((String) videoRaw).isBlank()) ? new File((String) videoRaw) : null;

        int loadedBandCount = asInt(root.get("bandCount"), -1);
        int maxBandIndex = -1;

        Object roleListObj = root.get("roles");
        if (roleListObj instanceof List) {
            for (Object roleObj : (List<?>) roleListObj) {
                if (!(roleObj instanceof Map)) continue;
                Map<String, Object> m = (Map<String, Object>) roleObj;
                String name = asString(m.get("name"));
                int rgb = asInt(m.get("color"), Color.WHITE.getRGB());
                roles.add(new Role(name, new Color(rgb, true)));
            }
        }

        Object textListObj = root.get("texts");
        if (textListObj instanceof List) {
            for (Object textObj : (List<?>) textListObj) {
                if (!(textObj instanceof Map)) continue;
                Map<String, Object> m = (Map<String, Object>) textObj;
                int band = asInt(m.get("band"), 0);
                maxBandIndex = Math.max(maxBandIndex, band);
                TextItem item = new TextItem(asString(m.get("text")), asInt(m.get("x"), 0), band);
                String roleName = asString(m.get("role"));
                if (!roleName.isEmpty()) {
                    roles.stream().filter(r -> roleName.equals(r.name)).findFirst().ifPresent(r -> item.role = r);
                }
                textManager.addTextItem(item);
            }
        }

        Object sepListObj = root.get("separators");
        if (sepListObj instanceof List) {
            for (Object sepObj : (List<?>) sepListObj) {
                if (!(sepObj instanceof Map)) continue;
                Map<String, Object> m = (Map<String, Object>) sepObj;
                int band = asInt(m.get("band"), 0);
                int x = asInt(m.get("x"), 0);
                int splitIndex = asInt(m.get("splitIndex"), -1);
                String typeRaw = asString(m.get("type"));
                SeparatorMark.Type type;
                try {
                    type = SeparatorMark.Type.valueOf(typeRaw);
                } catch (Exception ignored) {
                    type = SeparatorMark.Type.LEGACY;
                }
                maxBandIndex = Math.max(maxBandIndex, band);
                textManager.addSeparator(band, x, type, splitIndex);
            }
        }

        int inferredBandCount = (maxBandIndex >= 0) ? (maxBandIndex + 1) : 4;
        int finalBandCount = loadedBandCount > 0 ? loadedBandCount : inferredBandCount;
        return new LoadedProject(videoFile, finalBandCount);
    }

    private static LoadedProject loadLegacy(String raw, TextManager textManager, ArrayList<Role> roles) {
        File videoFile = null;
        int loadedBandCount = -1;
        int maxBandIndex = -1;
        try (BufferedReader reader = new BufferedReader(new java.io.StringReader(raw))) {
            textManager.clearAll();
            roles.clear();
            reader.readLine(); // version
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\\|", -1);
                switch (parts[0]) {
                    case "VIDEO" -> videoFile = new File(parts[1]);
                    case "BANDS" -> loadedBandCount = Integer.parseInt(parts[1]);
                    case "ROLE" -> {
                        Color c = new Color(Integer.parseInt(parts[2]));
                        roles.add(new Role(parts[1], c));
                    }
                    case "TEXT" -> {
                        int band = Integer.parseInt(parts[3]);
                        maxBandIndex = Math.max(maxBandIndex, band);
                        TextItem item = new TextItem(parts[1], Integer.parseInt(parts[2]), band);
                        if (parts.length > 4 && !parts[4].isEmpty()) {
                            final String rName = parts[4];
                            roles.stream().filter(r -> r.name.equals(rName)).findFirst().ifPresent(r -> item.role = r);
                        }
                        textManager.addTextItem(item);
                    }
                    case "SEP" -> {
                        int band = Integer.parseInt(parts[1]);
                        maxBandIndex = Math.max(maxBandIndex, band);
                        int x = Integer.parseInt(parts[2]);
                        if (parts.length > 3) {
                            SeparatorMark.Type type = SeparatorMark.Type.valueOf(parts[3]);
                            int splitIndex = (parts.length > 4) ? Integer.parseInt(parts[4]) : -1;
                            textManager.addSeparator(band, x, type, splitIndex);
                        } else {
                            // Backward compatibility with old projects
                            textManager.addSeparator(band, x, SeparatorMark.Type.LEGACY);
                        }
                    }
                }
            }
        } catch (Exception e) { e.printStackTrace(); }

        int inferredBandCount = (maxBandIndex >= 0) ? (maxBandIndex + 1) : 4;
        int finalBandCount = loadedBandCount > 0 ? loadedBandCount : inferredBandCount;
        return new LoadedProject(videoFile, finalBandCount);
    }

    private static String escape(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    private static int asInt(Object value, int fallback) {
        if (value instanceof Number) return ((Number) value).intValue();
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (Exception ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static final class JsonParser {
        private final String src;
        private int i;

        private JsonParser(String src) {
            this.src = src;
            this.i = 0;
        }

        private Object parseValue() {
            skipSpaces();
            if (i >= src.length()) throw new IllegalArgumentException("JSON vide");
            char c = src.charAt(i);
            if (c == '{') return parseObject();
            if (c == '[') return parseArray();
            if (c == '"') return parseString();
            if (c == 'n') return parseNull();
            if (c == 't' || c == 'f') return parseBoolean();
            return parseNumber();
        }

        private Map<String, Object> parseObject() {
            expect('{');
            Map<String, Object> obj = new HashMap<>();
            skipSpaces();
            if (peek('}')) {
                i++;
                return obj;
            }
            while (true) {
                String key = parseString();
                skipSpaces();
                expect(':');
                Object value = parseValue();
                obj.put(key, value);
                skipSpaces();
                if (peek('}')) {
                    i++;
                    break;
                }
                expect(',');
            }
            return obj;
        }

        private List<Object> parseArray() {
            expect('[');
            ArrayList<Object> arr = new ArrayList<>();
            skipSpaces();
            if (peek(']')) {
                i++;
                return arr;
            }
            while (true) {
                arr.add(parseValue());
                skipSpaces();
                if (peek(']')) {
                    i++;
                    break;
                }
                expect(',');
            }
            return arr;
        }

        private String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (i < src.length()) {
                char c = src.charAt(i++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    if (i >= src.length()) break;
                    char esc = src.charAt(i++);
                    switch (esc) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            if (i + 4 > src.length()) throw new IllegalArgumentException("Unicode JSON invalide");
                            String hex = src.substring(i, i + 4);
                            sb.append((char) Integer.parseInt(hex, 16));
                            i += 4;
                        }
                        default -> sb.append(esc);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw new IllegalArgumentException("String JSON invalide");
        }

        private Object parseNull() {
            if (src.startsWith("null", i)) {
                i += 4;
                return null;
            }
            throw new IllegalArgumentException("Valeur JSON invalide");
        }

        private Boolean parseBoolean() {
            if (src.startsWith("true", i)) {
                i += 4;
                return Boolean.TRUE;
            }
            if (src.startsWith("false", i)) {
                i += 5;
                return Boolean.FALSE;
            }
            throw new IllegalArgumentException("Boolean JSON invalide");
        }

        private Number parseNumber() {
            int start = i;
            if (peek('-')) i++;
            while (i < src.length() && Character.isDigit(src.charAt(i))) i++;
            boolean isDouble = false;
            if (i < src.length() && src.charAt(i) == '.') {
                isDouble = true;
                i++;
                while (i < src.length() && Character.isDigit(src.charAt(i))) i++;
            }
            String token = src.substring(start, i);
            if (token.isEmpty() || "-".equals(token)) {
                throw new IllegalArgumentException("Nombre JSON invalide");
            }
            return isDouble ? Double.parseDouble(token) : Long.parseLong(token);
        }

        private void expect(char c) {
            skipSpaces();
            if (i >= src.length() || src.charAt(i) != c) {
                throw new IllegalArgumentException("JSON invalide, attendu '" + c + "'");
            }
            i++;
        }

        private boolean peek(char c) {
            return i < src.length() && src.charAt(i) == c;
        }

        private void skipSpaces() {
            while (i < src.length() && Character.isWhitespace(src.charAt(i))) i++;
        }
    }
}