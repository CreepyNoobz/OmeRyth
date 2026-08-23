package app.services;

import app.MainFenetre;
import app.ui.EnhancedTutorialDialog;
import app.ui.OnboardingManager;
import app.utils.FileUtils;

import javax.swing.*;

/**
 * Gestionnaire du premier lancement de l'application.
 * Affiche le tutoriel amélioré et marque l'onboarding comme complété.
 */
public class FirstRunHandler {

    /**
     * Affiche les écrans du premier lancement (tutoriel amélioré) si nécessaire.
     */
    public static void handleFirstRun(MainFenetre window) {
        try {
            // Utiliser le nouvel OnboardingManager pour gérer l'expérience du premier lancement
            OnboardingManager.showOnboardingIfNeeded(window);
        } catch (Throwable ignored) {
            // En cas d'erreur, continuer silencieusement
        }
    }
}
