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

    private static final String JSON_VERSION = "RHYTHMO_V5";

    public static class LoadedProject {
        public final File videoFile;
        public final int bandCount;
        public final double pixelsPerSecond;
        public final int zoomLevelIndex;

        public LoadedProject(File videoFile, int bandCount, double pixelsPerSecond, int zoomLevelIndex) {
            this.videoFile = videoFile;
            this.bandCount = bandCount;
            this.pixelsPerSecond = pixelsPerSecond;
            this.zoomLevelIndex = zoomLevelIndex;
        }

        public LoadedProject(File videoFile, int bandCount) {
            this(videoFile, bandCount, -1, -1);
        }
    }

    public static void save(File file, File videoFile, TextManager textManager, ArrayList<Role> roles, int bandCount, double pixelsPerSecond, int zoomLevelIndex) {
        try {
            if (file != null && file.getName().toLowerCase().endsWith(".detx")) {
                DetxManager.saveDetx(file, videoFile, textManager, roles, bandCount, pixelsPerSecond > 0 ? pixelsPerSecond : 80.0);
                return;
            }
            String json = toJson(videoFile, textManager, roles, bandCount, pixelsPerSecond, zoomLevelIndex);
            Files.writeString(file.toPath(), json, StandardCharsets.UTF_8);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void save(File file, File videoFile, TextManager textManager, ArrayList<Role> roles, int bandCount) {
        save(file, videoFile, textManager, roles, bandCount, -1, -1);
    }

    public static void saveDetx(File file, File videoFile, TextManager textManager, ArrayList<Role> roles, int bandCount, double pixelsPerSecond) {
        DetxManager.saveDetx(file, videoFile, textManager, roles, bandCount, pixelsPerSecond > 0 ? pixelsPerSecond : 80.0);
    }

    public static LoadedProject load(File file, TextManager textManager, ArrayList<Role> roles) {
        try {
            String nameLower = file.getName().toLowerCase();
            if (nameLower.endsWith(".detx") || nameLower.endsWith(".cappella")) {
                return DetxManager.loadDetx(file, textManager, roles, 80.0);
            }
            String raw = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            String trimmed = raw.trim();
            if (trimmed.startsWith("\uFEFF")) {
                trimmed = trimmed.substring(1).trim();
            }
            if (trimmed.startsWith("<?xml") || trimmed.startsWith("<detx")) {
                return DetxManager.loadDetx(file, textManager, roles, 80.0);
            }
            if (trimmed.startsWith("{")) {
                return loadFromJson(trimmed, textManager, roles);
            }
            return loadLegacy(raw, textManager, roles);
        } catch (Exception e) {
            e.printStackTrace();
            return new LoadedProject(null, 4);
        }
    }

    private static String toJson(File videoFile, TextManager textManager, ArrayList<Role> roles, int bandCount, double pixelsPerSecond, int zoomLevelIndex) {
        StringBuilder sb = new StringBuilder(4096);
        sb.append("{\n");
        sb.append("  \"version\": \"").append(JSON_VERSION).append("\",\n");
        if (videoFile != null) {
            sb.append("  \"video\": \"").append(escape(videoFile.getAbsolutePath())).append("\",\n");
        } else {
            sb.append("  \"video\": null,\n");
        }
        sb.append("  \"bandCount\": ").append(Math.max(1, bandCount)).append(",\n");
        sb.append("  \"pixelsPerSecond\": ").append(pixelsPerSecond > 0 ? pixelsPerSecond : 80.0).append(",\n");
        sb.append("  \"zoomLevelIndex\": ").append(Math.max(0, zoomLevelIndex)).append(",\n");

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
                String signName = (sep.signType != null) ? sep.signType.name() : SeparatorMark.SignType.DEFAULT.name();
                String rawTypeEscaped = (sep.rawDetxType != null) ? sep.rawDetxType.replace("\"", "\\\"") : "";
                separatorLines.add("    {\"band\": " + band + ", \"x\": " + sep.x
                        + ", \"type\": \"" + sep.type.name() + "\", \"splitIndex\": " + sep.splitIndex
                        + ", \"signType\": \"" + signName + "\", \"rawDetxType\": \"" + rawTypeEscaped + "\"}");
            }
        }
        for (int i = 0; i < separatorLines.size(); i++) {
            sb.append(separatorLines.get(i));
            if (i < separatorLines.size() - 1) sb.append(',');
            sb.append('\n');
        }
        sb.append("  ],\n");

        ArrayList<Integer> planMarkers = textManager.getPlanMarkers();
        sb.append("  \"planMarkers\": [");
        for (int i = 0; i < planMarkers.size(); i++) {
            sb.append(planMarkers.get(i));
            if (i < planMarkers.size() - 1) sb.append(", ");
        }
        sb.append("]\n");

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
                int x = asInt(m.get("x"), 0);
                TextItem item = new TextItem(asString(m.get("text")), x, band);
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
                String signTypeRaw = asString(m.get("signType"));
                SeparatorMark.SignType signType = SeparatorMark.SignType.DEFAULT;
                if (signTypeRaw != null) {
                    try {
                        signType = SeparatorMark.SignType.valueOf(signTypeRaw);
                    } catch (Exception ignored) {}
                }
                String rawDetxType = asString(m.get("rawDetxType"));
                maxBandIndex = Math.max(maxBandIndex, band);
                SeparatorMark sm = textManager.addSeparator(band, x, type, splitIndex, signType);
                if (rawDetxType != null && !rawDetxType.isEmpty()) {
                    sm.rawDetxType = rawDetxType;
                }
            }
        }

        Object planListObj = root.get("planMarkers");
        if (planListObj instanceof List) {
            for (Object pm : (List<?>) planListObj) {
                int pmX = asInt(pm, -1);
                if (pmX >= 0) textManager.addPlanMarker(pmX);
            }
        }

        double loadedPps = asDouble(root.get("pixelsPerSecond"), -1.0);
        int loadedZoomIndex = asInt(root.get("zoomLevelIndex"), -1);

        int inferredBandCount = (maxBandIndex >= 0) ? (maxBandIndex + 1) : 4;
        int finalBandCount = loadedBandCount > 0 ? loadedBandCount : inferredBandCount;
        return new LoadedProject(videoFile, finalBandCount, loadedPps, loadedZoomIndex);
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
                        int x = Integer.parseInt(parts[2]);
                        TextItem item = new TextItem(parts[1], x, band);
                        if (parts.length > 4 && !parts[4].isEmpty()) {
                            final String rName = parts[4];
                            roles.stream().filter(r -> r.name.equals(rName)).findFirst().ifPresent(r -> item.role = r);
                        }
                        textManager.addTextItem(item);
                    }
                    case "SEP" -> {
                        int band = Integer.parseInt(parts[2]);
                        maxBandIndex = Math.max(maxBandIndex, band);
                        int x = Integer.parseInt(parts[1]);
                        SeparatorMark.Type type;
                        try {
                            type = SeparatorMark.Type.valueOf(parts[3]);
                        } catch (Exception ignored) {
                            type = SeparatorMark.Type.LEGACY;
                        }
                        textManager.addSeparator(band, x, type);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
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

    private static double asDouble(Object value, double fallback) {
        if (value instanceof Number) return ((Number) value).doubleValue();
        if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
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