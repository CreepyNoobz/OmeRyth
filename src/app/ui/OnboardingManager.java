package app.ui;

import app.MainFenetre;
import app.utils.FileUtils;
import javax.swing.*;
import java.util.Properties;

/**
 * Gestionnaire d'onboarding - gère l'expérience du premier démarrage.
 * Affiche un tutoriel ou la page d'accueil simplifié selon le contexte.
 */
public class OnboardingManager {

    private static final String ONBOARDING_KEY = "onboarding_done";
    private static final String ONBOARDING_VERSION = "onboarding_version";
    private static final String CURRENT_VERSION = "1.0";

    public static boolean shouldShowOnboarding() {
        Properties appState = FileUtils.loadAppState();
        String done = appState.getProperty(ONBOARDING_KEY, "false");
        String version = appState.getProperty(ONBOARDING_VERSION, "0.0");
        
        // Afficher le tutoriel si jamais fait ou si version a changé
        return !done.equalsIgnoreCase("true") || !version.equals(CURRENT_VERSION);
    }

    public static void markOnboardingDone() {
        Properties appState = FileUtils.loadAppState();
        appState.setProperty(ONBOARDING_KEY, "true");
        appState.setProperty(ONBOARDING_VERSION, CURRENT_VERSION);
        FileUtils.saveAppState(appState);
    }

    public static void showOnboardingIfNeeded(MainFenetre parent) {
        // 1. Si des composants essentiels sont manquants, afficher le dialogue d'installation
        if (!app.services.DependencyManagerService.hasAllEssentialComponents()) {
            DependencySetupDialog setupDialog = new DependencySetupDialog(parent);
            setupDialog.setVisible(true);
        }

        // 2. Afficher le tutoriel interactif si nécessaire
        if (shouldShowOnboarding()) {
            EnhancedTutorialDialog tutorial = new EnhancedTutorialDialog(parent);
            tutorial.setVisible(true);
            markOnboardingDone();
        }
    }

    public static void resetOnboarding() {
        markOnboardingDone();
        Properties appState = FileUtils.loadAppState();
        appState.remove(ONBOARDING_KEY);
        appState.remove(ONBOARDING_VERSION);
        FileUtils.saveAppState(appState);
    }
}
