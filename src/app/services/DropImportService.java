package app.services;

import javax.swing.TransferHandler;
import java.awt.datatransfer.DataFlavor;
import java.io.File;
import java.util.List;
import java.util.function.Consumer;

/**
 * Service gérant le glisser-déposer (Drag & Drop) de fichiers depuis l'explorateur système vers la fenêtre OmeRyth.
 * <p>
 * Aiguille automatiquement les fichiers déposés :
 * <ul>
 *   <li><b>Fichiers projets :</b> Extensions {@code .rythmo}, {@code .detx}, {@code .xml}, {@code .cappella}, {@code .json}.</li>
 *   <li><b>Médias audio / vidéo :</b> Extensions {@code .mp4}, {@code .mkv}, {@code .mov}, {@code .avi},
 *       {@code .mp3}, {@code .wav}, {@code .ogg}, {@code .m4a}, {@code .flac}.</li>
 * </ul>
 * </p>
 */
public class DropImportService {

    private final Consumer<File> onProjectFile;
    private final Consumer<File> onMediaFile;
    private final Consumer<String> onUnsupported;

    public DropImportService(Consumer<File> onProjectFile,
                             Consumer<File> onMediaFile,
                             Consumer<String> onUnsupported) {
        this.onProjectFile = onProjectFile;
        this.onMediaFile = onMediaFile;
        this.onUnsupported = onUnsupported;
    }

    public TransferHandler createTransferHandler() {
        return new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
                return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }

            @Override
            @SuppressWarnings("unchecked")
            public boolean importData(TransferSupport support) {
                if (!canImport(support)) return false;
                try {
                    List<File> files = (List<File>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    if (files == null || files.isEmpty()) return false;
                    handle(files.get(0));
                    return true;
                } catch (Exception ex) {
                    onUnsupported.accept("Import impossible : " + ex.getMessage());
                    return false;
                }
            }
        };
    }

    private void handle(File file) {
        if (file == null) return;
        String name = file.getName().toLowerCase();
        if (name.endsWith(".rythmo") || name.endsWith(".json") || name.endsWith(".detx") || name.endsWith(".xml") || name.endsWith(".cappella")) {
            onProjectFile.accept(file);
            return;
        }
        if (name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".mov") || name.endsWith(".avi")
                || name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".ogg") || name.endsWith(".m4a") || name.endsWith(".flac")) {
            onMediaFile.accept(file);
            return;
        }
        onUnsupported.accept("Fichier non supporté. Déposez un projet (.rythmo, .detx, .json) ou un média (mp4/mkv/mov/mp3/wav).");
    }
}
