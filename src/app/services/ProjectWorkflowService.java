package app.services;

import app.ui.CreationProjetFrame;
import app.ui.Role;
import app.ui.RolePresets;
import app.ui.TimelinePanel;
import app.utils.FileUtils;

import javax.swing.JFrame;
import java.io.File;
import java.util.ArrayList;

public class ProjectWorkflowService {

    public static class NewProjectResult {
        public final File videoFile;
        public final int bandCount;
        public final String rolePreset;
        public final File projectFile;

        public NewProjectResult(File videoFile, int bandCount, String rolePreset, File projectFile) {
            this.videoFile = videoFile;
            this.bandCount = bandCount;
            this.rolePreset = rolePreset;
            this.projectFile = projectFile;
        }
    }

    public NewProjectResult askNewProject(JFrame parent, int initialBandCount) {
        // Utiliser le nouvel assistant CreationProjetFrame à la place du dialogue simple
        CreationProjetFrame creationFrame = new CreationProjetFrame(parent);
        creationFrame.setVisible(true);

        if (!creationFrame.isConfirmed()) {
            return null; // L'utilisateur a annulé
        }

        File videoFile = creationFrame.getVideoFile();
        File saveFile = creationFrame.getProjectFile();
        int bandsCount = creationFrame.getBandCount();
        return new NewProjectResult(videoFile, bandsCount, null, saveFile);
    }

    public File askProjectToOpen(JFrame parent) {
        return FileUtils.chooseOpenFile(parent, "Ouvrir projet", "rythmo", "json");
    }

    public File ensureProjectSavePath(JFrame parent, File currentProjectFile) {
        if (currentProjectFile != null) return currentProjectFile;
        return FileUtils.chooseSaveFile(parent, "Sauvegarder projet", "rythmo");
    }

    /** Apply a freshly created project: clear roles/timeline and set band count. */
    public void applyNewProject(TimelinePanel timeline,
                                ArrayList<Role> roles,
                                File videoFile,
                                int bandCount) {
        roles.clear();
        timeline.clearAll();
        timeline.setBandCount(bandCount);
    }

    /** Applique un projet avec les rôles prédéfinis. */
    public void applyNewProjectWithPreset(TimelinePanel timeline,
                                          ArrayList<Role> roles,
                                          File videoFile,
                                          int bandCount,
                                          String presetName) {
        roles.clear();
        timeline.clearAll();
        timeline.setBandCount(bandCount);
        
    }
}
