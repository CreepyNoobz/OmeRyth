package app.ui;

import app.ui.ProjectManager.LoadedProject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.awt.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class DetxManager {
    private static final double FPS = 25.0;
    private static final String BASE_TC = "01:00:00:00";

    public static double timecodeToSeconds(String timecode) {
        return timecodeToSeconds(timecode, BASE_TC);
    }

    public static double timecodeToSeconds(String timecode, String baseTimecode) {
        return parseTimecode(timecode) - parseTimecode(baseTimecode != null ? baseTimecode : BASE_TC);
    }

    public static String secondsToTimecode(double seconds) {
        return secondsToTimecode(seconds, BASE_TC);
    }

    public static String secondsToTimecode(double seconds, String baseTimecode) {
        double totalSeconds = seconds + parseTimecode(baseTimecode != null ? baseTimecode : BASE_TC);
        int hours = (int) (totalSeconds / 3600);
        int minutes = (int) ((totalSeconds % 3600) / 60);
        int secs = (int) (totalSeconds % 60);
        int frames = (int) Math.round((totalSeconds - Math.floor(totalSeconds)) * FPS);
        if (frames >= 25) {
            secs += 1;
            frames = 0;
        }
        return String.format("%02d:%02d:%02d:%02d", hours, minutes, secs, frames);
    }

    private static double parseTimecode(String timecode) {
        if (timecode == null) return 0.0;
        String[] parts = timecode.trim().split(":");
        if (parts.length != 4) return 0.0;
        try {
            int hours = Integer.parseInt(parts[0]);
            int minutes = Integer.parseInt(parts[1]);
            int secs = Integer.parseInt(parts[2]);
            int frames = Integer.parseInt(parts[3]);
            return hours * 3600 + minutes * 60 + secs + frames / FPS;
        } catch (Exception e) {
            return 0.0;
        }
    }

    public static LoadedProject loadDetx(File file, TextManager textManager, ArrayList<Role> roles, double pixelsPerSecond) {
        File videoFile = null;
        int maxBand = 0;

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc;

            // Gérer le BOM UTF-8 éventuel (\uFEFF)
            try (java.io.BufferedInputStream bis = new java.io.BufferedInputStream(new java.io.FileInputStream(file))) {
                bis.mark(3);
                byte[] bom = new byte[3];
                int read = bis.read(bom);
                if (read < 3 || bom[0] != (byte) 0xEF || bom[1] != (byte) 0xBB || bom[2] != (byte) 0xBF) {
                    bis.reset();
                }
                doc = builder.parse(new org.xml.sax.InputSource(bis));
            }
            doc.getDocumentElement().normalize();

            // Clear existing
            textManager.getTexts().clear();
            textManager.getBandSeparators().clear();
            textManager.getPlanMarkers().clear();
            roles.clear();

            String baseTimecode = null;

            // Header
            NodeList headerList = doc.getElementsByTagName("header");
            if (headerList.getLength() > 0) {
                Element header = (Element) headerList.item(0);
                NodeList videoNodes = header.getElementsByTagName("videofile");
                if (videoNodes.getLength() > 0) {
                    Element vnode = (Element) videoNodes.item(0);
                    String ts = vnode.getAttribute("timestamp");
                    if (ts != null && !ts.trim().isEmpty() && ts.contains(":")) {
                        baseTimecode = ts.trim();
                    }
                    String vpath = vnode.getTextContent();
                    if (vpath != null && !vpath.trim().isEmpty()) {
                        videoFile = new File(vpath.trim());
                    }
                }
            }

            // Détection automatique du timecode de base si non spécifié
            if (baseTimecode == null) {
                NodeList lipsyncs = doc.getElementsByTagName("lipsync");
                boolean hasHour0 = false;
                boolean hasHour1OrMore = false;
                for (int i = 0; i < lipsyncs.getLength(); i++) {
                    Element el = (Element) lipsyncs.item(i);
                    String tc = el.getAttribute("timecode");
                    if (tc != null && tc.length() >= 2) {
                        if (tc.startsWith("00:")) hasHour0 = true;
                        else if (!tc.startsWith("00:")) hasHour1OrMore = true;
                    }
                }
                if (hasHour0 && !hasHour1OrMore) {
                    baseTimecode = "00:00:00:00";
                } else {
                    baseTimecode = BASE_TC;
                }
            }

            // Résolution locale du fichier vidéo si non trouvé au chemin d'origine
            if (videoFile != null && !videoFile.exists() && file.getParentFile() != null) {
                File localVideo = new File(file.getParentFile(), videoFile.getName());
                if (localVideo.exists()) {
                    videoFile = localVideo;
                }
            }
            if ((videoFile == null || !videoFile.exists()) && file.getParentFile() != null) {
                String baseName = file.getName();
                int dot = baseName.lastIndexOf('.');
                if (dot > 0) baseName = baseName.substring(0, dot);
                for (String ext : new String[]{".mp4", ".mkv", ".mov", ".avi", ".mp3", ".wav"}) {
                    File candidate = new File(file.getParentFile(), baseName + ext);
                    if (candidate.exists()) {
                        videoFile = candidate;
                        break;
                    }
                }
            }

            // Roles (mapping à la fois par id et par nom)
            Map<String, Role> roleMap = new java.util.HashMap<>();
            NodeList rolesList = doc.getElementsByTagName("roles");
            if (rolesList.getLength() > 0) {
                Element rolesElement = (Element) rolesList.item(0);
                NodeList roleNodes = rolesElement.getElementsByTagName("role");
                for (int i = 0; i < roleNodes.getLength(); i++) {
                    Element r = (Element) roleNodes.item(i);
                    String id = r.getAttribute("id");
                    String name = r.getAttribute("name");
                    if (name == null || name.trim().isEmpty()) {
                        name = (id != null && !id.trim().isEmpty()) ? id : "Rôle " + (i + 1);
                    }
                    String colorHex = r.getAttribute("color");
                    Color c = Color.BLACK;
                    if (colorHex != null && colorHex.startsWith("#") && colorHex.length() == 7) {
                        try {
                            c = Color.decode(colorHex);
                        } catch (Exception ignored) {}
                    }
                    Role role = new Role(name, c);
                    roles.add(role);
                    if (id != null && !id.trim().isEmpty()) {
                        roleMap.put(id.trim().toLowerCase(), role);
                    }
                    roleMap.put(name.trim().toLowerCase(), role);
                }
            }

            // Body
            NodeList bodyList = doc.getElementsByTagName("body");
            if (bodyList.getLength() > 0) {
                Element body = (Element) bodyList.item(0);
                
                // Lines
                NodeList lineNodes = body.getElementsByTagName("line");
                for (int i = 0; i < lineNodes.getLength(); i++) {
                    Element line = (Element) lineNodes.item(i);
                    String roleId = line.getAttribute("role");
                    int track = 0;
                    try {
                        track = Integer.parseInt(line.getAttribute("track"));
                    } catch (Exception ignored) {}
                    maxBand = Math.max(maxBand, track);

                    Role currentRole = null;
                    if (roleId != null && !roleId.trim().isEmpty()) {
                        currentRole = roleMap.get(roleId.trim().toLowerCase());
                    }

                    NodeList children = line.getChildNodes();
                    StringBuilder currentText = new StringBuilder();
                    int phraseStartX = -1;
                    
                    for (int j = 0; j < children.getLength(); j++) {
                        Node node = children.item(j);
                        if (node.getNodeType() == Node.ELEMENT_NODE) {
                            Element el = (Element) node;
                            if (el.getTagName().equals("lipsync")) {
                                String type = el.getAttribute("type");
                                String tc = el.getAttribute("timecode");
                                double secs = timecodeToSeconds(tc, baseTimecode);
                                int x = (int) Math.round(secs * pixelsPerSecond);

                                String t = (type != null) ? type.toLowerCase().trim() : "";
                                boolean isStart = t.startsWith("in_") || t.equals("in") || t.equals("open") || t.equals("start");
                                boolean isEnd = t.startsWith("out_") || t.equals("out") || t.equals("close") || t.equals("closed") || t.equals("end");

                                if (isStart) {
                                    if (currentText.length() > 0 && phraseStartX != -1) {
                                        TextItem ti = new TextItem(currentText.toString(), phraseStartX, track);
                                        ti.role = currentRole;
                                        textManager.addTextItem(ti);
                                        currentText.setLength(0);
                                    }
                                    SeparatorMark sm = textManager.addSeparator(track, x, SeparatorMark.Type.START, -1, SeparatorMark.SignType.DEFAULT);
                                    sm.rawDetxType = (type != null && !type.isEmpty()) ? type : "in_open";
                                    phraseStartX = x;
                                } else if (isEnd) {
                                    if (phraseStartX == -1) {
                                        phraseStartX = x;
                                    }
                                    if (currentText.length() > 0) {
                                        TextItem ti = new TextItem(currentText.toString(), phraseStartX, track);
                                        ti.role = currentRole;
                                        textManager.addTextItem(ti);
                                        currentText.setLength(0);
                                    }
                                    SeparatorMark sm = textManager.addSeparator(track, x, SeparatorMark.Type.END, -1, SeparatorMark.SignType.DEFAULT);
                                    sm.rawDetxType = (type != null && !type.isEmpty()) ? type : "out_open";
                                    phraseStartX = -1;
                                } else {
                                    // Séparateur interne (mpb, fvr, neutral, a, etc.)
                                    if (phraseStartX == -1) {
                                        phraseStartX = x;
                                        SeparatorMark startSm = textManager.addSeparator(track, x, SeparatorMark.Type.START, -1, SeparatorMark.SignType.DEFAULT);
                                        startSm.rawDetxType = "in_open";
                                    }
                                    int splitIdx = currentText.length();
                                    SeparatorMark.SignType signType = SeparatorMark.SignType.DEFAULT;
                                    if (t.equals("fvr")) signType = SeparatorMark.SignType.FVR;
                                    else if (t.equals("neutral")) signType = SeparatorMark.SignType.NEUTRAL;
                                    else if (t.equals("a")) signType = SeparatorMark.SignType.OPEN_A;
                                    else if (t.equals("mpb")) signType = SeparatorMark.SignType.MPB;

                                    SeparatorMark sm = textManager.addSeparator(track, x, SeparatorMark.Type.INNER, splitIdx, signType);
                                    sm.rawDetxType = type;
                                }
                            } else if (el.getTagName().equals("text")) {
                                String rawText = el.getTextContent();
                                if (rawText != null) {
                                    // Nettoyer les sauts de ligne \r et \n
                                    String cleaned = rawText.replace('\r', ' ').replace('\n', ' ');
                                    currentText.append(cleaned);
                                }
                            }
                        }
                    }

                    // Sécurité : sauvegarder le texte si la phrase n'a pas été formellement fermée par une balise de fin
                    if (currentText.length() > 0) {
                        int actualStart = (phraseStartX != -1) ? phraseStartX : 0;
                        TextItem ti = new TextItem(currentText.toString(), actualStart, track);
                        ti.role = currentRole;
                        textManager.addTextItem(ti);
                        currentText.setLength(0);
                    }
                }

                // Shots
                NodeList shotNodes = body.getElementsByTagName("shot");
                for (int i = 0; i < shotNodes.getLength(); i++) {
                    Element shot = (Element) shotNodes.item(i);
                    String tc = shot.getAttribute("timecode");
                    double secs = timecodeToSeconds(tc, baseTimecode);
                    int x = (int) Math.round(secs * pixelsPerSecond);
                    textManager.getPlanMarkers().add(x);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return new LoadedProject(videoFile, maxBand + 1, pixelsPerSecond, -1);
    }

    public static void saveDetx(File file, File videoFile, TextManager textManager, ArrayList<Role> roles, int bandCount, double pixelsPerSecond) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.newDocument();
            doc.setXmlStandalone(true);

            Element root = doc.createElement("detx");
            root.setAttribute("copyright", "Chinkel S.A., 2007-2025");
            doc.appendChild(root);

            // Header
            Element header = doc.createElement("header");
            root.appendChild(header);

            Element cappella = doc.createElement("cappella");
            cappella.setAttribute("version", "3.7.0");
            header.appendChild(cappella);

            Element vfile = doc.createElement("videofile");
            vfile.setAttribute("timestamp", BASE_TC);
            if (videoFile != null) {
                vfile.setTextContent(videoFile.getAbsolutePath());
            }
            header.appendChild(vfile);

            // Roles
            Element rolesEl = doc.createElement("roles");
            root.appendChild(rolesEl);
            for (Role r : roles) {
                Element roleEl = doc.createElement("role");
                String hex = String.format("#%02x%02x%02x", r.color.getRed(), r.color.getGreen(), r.color.getBlue());
                roleEl.setAttribute("color", hex);
                roleEl.setAttribute("description", "");
                roleEl.setAttribute("id", r.name.toLowerCase());
                roleEl.setAttribute("name", r.name);
                rolesEl.appendChild(roleEl);
            }

            // Body
            Element body = doc.createElement("body");
            root.appendChild(body);

            Map<Integer, ArrayList<SeparatorMark>> bandSeps = textManager.getBandSeparators();
            for (int b = 0; b < bandCount; b++) {
                List<SeparatorMark> seps = bandSeps.get(b);
                if (seps == null || seps.isEmpty()) continue;

                List<SeparatorMark> sortedSeps = new ArrayList<>(seps);
                sortedSeps.sort(Comparator.comparingInt(s -> s.x));

                List<TextItem> texts = new ArrayList<>();
                for (TextItem ti : textManager.getTexts()) {
                    if (ti.band == b) texts.add(ti);
                }
                texts.sort(Comparator.comparingInt(t -> t.x));

                Element currentLine = null;
                String currentRoleId = null;
                boolean inPhrase = false;
                
                int i = 0;
                while (i < sortedSeps.size()) {
                    SeparatorMark mark = sortedSeps.get(i);
                    
                    if (mark.type == SeparatorMark.Type.START) {
                        TextItem phraseText = null;
                        for (TextItem ti : texts) {
                            if (ti.x == mark.x) {
                                phraseText = ti;
                                break;
                            }
                        }
                        
                        String roleId = (phraseText != null && phraseText.role != null) ? phraseText.role.name.toLowerCase() : "";
                        
                        if (currentLine == null || !roleId.equals(currentRoleId)) {
                            if (currentLine != null) {
                                // Need to close previous line, but this should have been handled by END
                            }
                            currentLine = doc.createElement("line");
                            currentLine.setAttribute("role", roleId);
                            currentLine.setAttribute("track", String.valueOf(b));
                            body.appendChild(currentLine);
                            currentRoleId = roleId;
                            
                            Element inOpen = doc.createElement("lipsync");
                            inOpen.setAttribute("timecode", secondsToTimecode(mark.x / pixelsPerSecond));
                            String startType = (mark.rawDetxType != null && !mark.rawDetxType.isEmpty()) ? mark.rawDetxType : "in_open";
                            inOpen.setAttribute("type", startType);
                            currentLine.appendChild(inOpen);
                        } else {
                            // Continuation in the same line
                            Element mpb = doc.createElement("lipsync");
                            mpb.setAttribute("timecode", secondsToTimecode(mark.x / pixelsPerSecond));
                            String contType = (mark.rawDetxType != null && !mark.rawDetxType.isEmpty()) ? mark.rawDetxType : "mpb";
                            mpb.setAttribute("type", contType);
                            currentLine.appendChild(mpb);
                        }
                        
                        // Find matching END
                        int endIdx = -1;
                        for (int j = i + 1; j < sortedSeps.size(); j++) {
                            if (sortedSeps.get(j).type == SeparatorMark.Type.END) {
                                endIdx = j;
                                break;
                            }
                        }
                        
                        if (endIdx != -1) {
                            List<SeparatorMark> innerSeps = new ArrayList<>();
                            for (int k = i + 1; k < endIdx; k++) {
                                if (sortedSeps.get(k).type == SeparatorMark.Type.INNER) {
                                    innerSeps.add(sortedSeps.get(k));
                                }
                            }
                            
                            String fullText = phraseText != null ? phraseText.text : "";
                            int lastSplit = 0;
                            
                            for (SeparatorMark inner : innerSeps) {
                                Element textEl = doc.createElement("text");
                                if (inner.splitIndex > lastSplit && inner.splitIndex <= fullText.length()) {
                                    textEl.setTextContent(fullText.substring(lastSplit, inner.splitIndex));
                                    lastSplit = inner.splitIndex;
                                } else {
                                    textEl.setTextContent("");
                                }
                                currentLine.appendChild(textEl);
                                
                                Element innerEl = doc.createElement("lipsync");
                                innerEl.setAttribute("timecode", secondsToTimecode(inner.x / pixelsPerSecond));
                                String innerType = (inner.rawDetxType != null && !inner.rawDetxType.isEmpty()) ? inner.rawDetxType :
                                    (inner.signType == SeparatorMark.SignType.FVR ? "fvr" :
                                     inner.signType == SeparatorMark.SignType.NEUTRAL ? "neutral" :
                                     inner.signType == SeparatorMark.SignType.OPEN_A ? "a" :
                                     inner.signType == SeparatorMark.SignType.MPB ? "mpb" : "mpb");
                                innerEl.setAttribute("type", innerType);
                                currentLine.appendChild(innerEl);
                            }
                            
                            Element lastText = doc.createElement("text");
                            if (lastSplit < fullText.length()) {
                                lastText.setTextContent(fullText.substring(lastSplit));
                            } else {
                                lastText.setTextContent("");
                            }
                            currentLine.appendChild(lastText);
                            
                            SeparatorMark endMark = sortedSeps.get(endIdx);
                            
                            // Check if next phrase is same role
                            boolean nextSameRole = false;
                            if (endIdx + 1 < sortedSeps.size() && sortedSeps.get(endIdx + 1).type == SeparatorMark.Type.START) {
                                SeparatorMark nextStart = sortedSeps.get(endIdx + 1);
                                TextItem nextText = null;
                                for (TextItem ti : texts) {
                                    if (ti.x == nextStart.x) {
                                        nextText = ti;
                                        break;
                                    }
                                }
                                String nextRoleId = (nextText != null && nextText.role != null) ? nextText.role.name.toLowerCase() : "";
                                if (nextRoleId.equals(currentRoleId)) {
                                    nextSameRole = true;
                                }
                            }
                            
                            if (nextSameRole) {
                                Element mpb = doc.createElement("lipsync");
                                mpb.setAttribute("timecode", secondsToTimecode(endMark.x / pixelsPerSecond));
                                String endType = (endMark.rawDetxType != null && !endMark.rawDetxType.isEmpty()) ? endMark.rawDetxType : "mpb";
                                mpb.setAttribute("type", endType);
                                currentLine.appendChild(mpb);
                            } else {
                                Element outOpen = doc.createElement("lipsync");
                                outOpen.setAttribute("timecode", secondsToTimecode(endMark.x / pixelsPerSecond));
                                String endType = (endMark.rawDetxType != null && !endMark.rawDetxType.isEmpty()) ? endMark.rawDetxType : "out_open";
                                outOpen.setAttribute("type", endType);
                                currentLine.appendChild(outOpen);
                                currentLine = null;
                                currentRoleId = null;
                            }
                            
                            i = endIdx;
                        }
                    }
                    i++;
                }
            }

            // Shots
            List<Integer> plans = new ArrayList<>(textManager.getPlanMarkers());
            Collections.sort(plans);
            for (Integer p : plans) {
                Element shot = doc.createElement("shot");
                shot.setAttribute("timecode", secondsToTimecode(p / pixelsPerSecond));
                body.appendChild(shot);
            }

            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            
            DOMSource source = new DOMSource(doc);
            StreamResult result = new StreamResult(new FileOutputStream(file));
            transformer.transform(source, result);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
