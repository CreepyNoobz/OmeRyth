package app.services;

import java.awt.Frame;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.swing.SwingUtilities;
import app.MainFenetre;

/**
 * Service assurant qu'une seule instance d'OmeRyth est active à la fois.
 * Si une seconde instance démarre, elle notifie la première (en lui passant
 * le fichier à ouvrir si présent), ramène la fenêtre au premier plan, puis se termine.
 */
public class SingleInstanceService {

    private static final int PORT = 49257;
    private static final String HOST = "127.0.0.1";
    private static final String PREFIX_OPEN = "OMERYTH_OPEN:";
    private static final String CMD_ACTIVATE = "OMERYTH_ACTIVATE";
    private static final String RESP_OK = "OMERYTH_OK";

    private static volatile ServerSocket serverSocket;
    private static volatile MainFenetre mainWindow;
    private static final Queue<String> pendingFiles = new ConcurrentLinkedQueue<>();

    /**
     * Appelé dès le lancement du programme (avant AWT/Swing).
     * @param fileToOpen Fichier passé en paramètre (ou null)
     * @return true si cette instance est l'instance principale, false si une instance existante a été notifiée.
     */
    public static boolean registerOrNotify(String fileToOpen) {
        try {
            ServerSocket ss = new ServerSocket();
            ss.setReuseAddress(true);
            ss.bind(new InetSocketAddress(InetAddress.getByName(HOST), PORT));
            serverSocket = ss;

            startServerListener(ss);
            return true;
        } catch (IOException e) {
            // Port déjà occupé -> tenter de contacter l'instance existante
            boolean notified = notifyExistingInstance(fileToOpen);
            if (notified) {
                return false;
            }

            // Si la notification a échoué (ex: fermeture brutale de l'instance précédente),
            // attendre brièvement et retenter le bind
            try {
                Thread.sleep(250);
                ServerSocket ssRetry = new ServerSocket();
                ssRetry.setReuseAddress(true);
                ssRetry.bind(new InetSocketAddress(InetAddress.getByName(HOST), PORT));
                serverSocket = ssRetry;
                startServerListener(ssRetry);
                return true;
            } catch (Exception retryEx) {
                System.err.println("[SingleInstance] Avertissement: impossible de lier le port 49257 (" + retryEx.getMessage() + "). Lancement direct.");
                return true;
            }
        }
    }

    public static boolean notifyExistingInstance(String fileToOpen) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(InetAddress.getByName(HOST), PORT), 2000);
            socket.setSoTimeout(2000);

            PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            if (fileToOpen != null && !fileToOpen.trim().isEmpty()) {
                out.println(PREFIX_OPEN + fileToOpen.trim());
            } else {
                out.println(CMD_ACTIVATE);
            }
            out.flush();

            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            String resp = in.readLine();
            return RESP_OK.equals(resp) || resp != null;
        } catch (IOException e) {
            return false;
        }
    }

    private static void startServerListener(final ServerSocket ss) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                if (ss != null && !ss.isClosed()) {
                    ss.close();
                }
            } catch (Exception ignored) {}
        }));

        Thread listenerThread = new Thread(() -> {
            while (ss != null && !ss.isClosed()) {
                try {
                    Socket client = ss.accept();
                    client.setSoTimeout(2000);
                    BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
                    String line = in.readLine();

                    PrintWriter out = new PrintWriter(new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8), true);
                    out.println(RESP_OK);
                    out.flush();
                    client.close();

                    if (line != null) {
                        String file = null;
                        if (line.startsWith(PREFIX_OPEN)) {
                            file = line.substring(PREFIX_OPEN.length()).trim();
                        }
                        handleIncomingRequest(file);
                    }
                } catch (SocketException se) {
                    break;
                } catch (Exception e) {
                    // Ignorer erreurs temporaires sur requêtes
                }
            }
        }, "OmeRyth-SingleInstance-Listener");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    /**
     * Enregistre la fenêtre principale pour pouvoir lui transmettre les requêtes.
     */
    public static void setMainWindow(MainFenetre window) {
        mainWindow = window;
        // Si des fichiers ont été reçus avant que la fenêtre ne soit prête
        String file;
        while ((file = pendingFiles.poll()) != null) {
            handleFileInEdt(file);
        }
    }

    private static void handleIncomingRequest(String file) {
        if (mainWindow == null) {
            if (file != null && !file.isEmpty()) {
                pendingFiles.add(file);
            }
            return;
        }
        SwingUtilities.invokeLater(() -> {
            mainWindow.bringToFront();
            if (file != null && !file.isEmpty()) {
                handleFileInEdt(file);
            }
        });
    }

    private static void handleFileInEdt(String filePath) {
        if (mainWindow == null || filePath == null || filePath.trim().isEmpty()) return;
        File f = new File(filePath.trim());
        if (f.exists() && f.isFile()) {
            mainWindow.ouvrirFichierProjet(f);
        }
    }

    public static void stopListenerForTesting() {
        try {
            if (serverSocket != null) {
                serverSocket.close();
                serverSocket = null;
            }
        } catch (Exception ignored) {}
    }
}
