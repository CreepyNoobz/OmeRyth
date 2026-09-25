package app.services;

import app.ui.TextItem;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service haute performance de vérification orthographique et grammaticale
 * pour les langues Française et Anglaise.
 * 
 * Fonctionne de façon 100% autonome et hors-ligne :
 * - Détection des accords grammaticaux (déterminants + pluriels, sujet + verbe, homophones).
 * - Détection des fautes d'orthographe (vocabulaire usuel + table de fautes classiques + Levenshtein).
 * - Génération de suggestions précises.
 * - Cache mémoire réactif sans impact sur les 60 FPS de la timeline.
 */
public class SpellGrammarService {

    private static final SpellGrammarService INSTANCE = new SpellGrammarService();

    public static SpellGrammarService getInstance() {
        return INSTANCE;
    }

    /** Représente une anomalie orthographique ou grammaticale détectée dans une chaîne */
    public static class SpellCheckIssue {
        public final int startIdx;
        public final int endIdx;
        public final String originalText;
        public final boolean isGrammar;
        public final String message;
        public final List<String> suggestions;

        // Coordonnées écran mises à jour dynamiquement à chaque frame de rendu
        public double screenStartX;
        public double screenEndX;
        public int screenY;
        public int screenHeight;
        public TextItem targetItem;
        public int band;

        public SpellCheckIssue(int startIdx, int endIdx, String originalText, boolean isGrammar, String message, List<String> suggestions) {
            this.startIdx = startIdx;
            this.endIdx = endIdx;
            this.originalText = originalText;
            this.isGrammar = isGrammar;
            this.message = message;
            this.suggestions = (suggestions != null) ? suggestions : Collections.emptyList();
        }

        public boolean containsPoint(double x, double y) {
            return x >= screenStartX - 4 && x <= screenEndX + 4 &&
                   y >= screenY - 2 && y <= screenY + screenHeight + 4;
        }
    }

    // Langue active ("fr" ou "en")
    private volatile String activeLanguage = "fr";

    // Cache pour éviter de recalculer à chaque frame de rendu
    private final Map<String, List<SpellCheckIssue>> cache = new ConcurrentHashMap<>();

    // Mots ignorés par l'utilisateur pour la session
    private final Set<String> ignoredWords = ConcurrentHashMap.newKeySet();

    // Liste des anomalies actuellement visibles à l'écran (pour le hit-testing de la souris)
    private final List<SpellCheckIssue> visibleIssues = new CopyOnWriteArrayList<>();

    // Dictionnaires et tables d'erreurs
    private final Set<String> frenchDictionary = new HashSet<>(4000);
    private final Map<String, List<String>> frenchTypos = new HashMap<>(500);

    private final Set<String> englishDictionary = new HashSet<>(4000);
    private final Map<String, List<String>> englishTypos = new HashMap<>(500);

    private SpellGrammarService() {
        initFrench();
        initEnglish();
    }

    public String getLanguage() {
        return activeLanguage;
    }

    public void setLanguage(String lang) {
        if (lang == null || lang.isBlank()) lang = "fr";
        lang = lang.trim().toLowerCase(Locale.ROOT);
        if (lang.startsWith("en")) {
            this.activeLanguage = "en";
        } else {
            this.activeLanguage = "fr";
        }
        clearCache();
    }

    public void clearCache() {
        cache.clear();
        visibleIssues.clear();
    }

    public void invalidateCache(String text) {
        if (text != null) {
            cache.remove("fr:" + text);
            cache.remove("en:" + text);
        }
    }

    public void ignoreWord(String word) {
        if (word != null && !word.isBlank()) {
            ignoredWords.add(word.trim().toLowerCase(Locale.ROOT));
            clearCache();
        }
    }

    public void clearVisibleIssues() {
        visibleIssues.clear();
    }

    public void registerVisibleIssue(SpellCheckIssue issue) {
        if (issue != null) {
            visibleIssues.add(issue);
        }
    }

    public SpellCheckIssue findIssueAt(int screenX, int screenY) {
        for (SpellCheckIssue issue : visibleIssues) {
            if (issue.containsPoint(screenX, screenY)) {
                return issue;
            }
        }
        return null;
    }

    /**
     * Analyse un texte et retourne la liste des erreurs orthographiques et grammaticales.
     */
    public List<SpellCheckIssue> checkText(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }
        String lang = activeLanguage;
        String cacheKey = lang + ":" + text;
        List<SpellCheckIssue> cached = cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        List<SpellCheckIssue> issues = new ArrayList<>();
        if ("en".equalsIgnoreCase(lang)) {
            checkEnglish(text, issues);
        } else {
            checkFrench(text, issues);
        }

        // Trier par position de début
        issues.sort(Comparator.comparingInt(a -> a.startIdx));
        cache.put(cacheKey, issues);
        return issues;
    }

    // =========================================================================
    //                            LOGIQUE FRANÇAISE
    // =========================================================================

    private void checkFrench(String text, List<SpellCheckIssue> issues) {
        // 1. Détection des erreurs de grammaire (accords, homophones, expressions)
        boolean[] covered = new boolean[text.length()];

        checkFrenchGrammar(text, issues, covered);

        // 2. Détection des fautes d'orthographe sur les mots individuels non couverts par une faute de grammaire
        Matcher matcher = Pattern.compile("(?U)[\\p{L}\\p{M}'-]+").matcher(text);
        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();
            String rawWord = matcher.group();

            if (isRangeCovered(covered, start, end)) {
                continue;
            }

            // Normaliser en retirant les apostrophes de tête (ex: l'ami -> ami, d'accord -> accord)
            String wordToCheck = rawWord;
            int wordStart = start;
            int wordEnd = end;

            String lower = rawWord.toLowerCase(Locale.FRENCH);

            // Traitement spécifique des préfixes d'élision (l', d', j', qu', c', s', n', m', t')
            if (lower.startsWith("l'") || lower.startsWith("d'") || lower.startsWith("j'") ||
                lower.startsWith("c'") || lower.startsWith("s'") || lower.startsWith("n'") ||
                lower.startsWith("m'") || lower.startsWith("t'")) {
                wordStart = start + 2;
                wordToCheck = rawWord.substring(2);
                lower = wordToCheck.toLowerCase(Locale.FRENCH);
            } else if (lower.startsWith("qu'")) {
                wordStart = start + 3;
                wordToCheck = rawWord.substring(3);
                lower = wordToCheck.toLowerCase(Locale.FRENCH);
            } else if (lower.startsWith("lorsqu'") || lower.startsWith("puisqu'")) {
                wordStart = start + 7;
                wordToCheck = rawWord.substring(7);
                lower = wordToCheck.toLowerCase(Locale.FRENCH);
            }

            if (wordToCheck.isEmpty() || wordToCheck.length() <= 1) {
                continue; // Ignore lettres isolées
            }

            if (ignoredWords.contains(lower)) {
                continue;
            }

            // Vérifier table de fautes classiques françaises
            List<String> directTypos = frenchTypos.get(lower);
            if (directTypos != null && !directTypos.isEmpty()) {
                issues.add(new SpellCheckIssue(
                    wordStart, wordEnd, wordToCheck,
                    false, "Faute d'orthographe : '" + wordToCheck + "'",
                    directTypos
                ));
                markCovered(covered, wordStart, wordEnd);
                continue;
            }

            // Si le mot n'est pas dans le dictionnaire
            if (!frenchDictionary.contains(lower)) {
                // Si c'est tout en majuscules (acronyme) ou commence par une majuscule au milieu de phrase
                if (isAllUpper(wordToCheck)) {
                    continue;
                }

                // Recherche de suggestions via distance de Levenshtein
                List<String> suggestions = findSuggestions(lower, frenchDictionary, 2);
                if (suggestions.isEmpty() && lower.endsWith("s")) {
                    // Essai du singulier
                    String singular = lower.substring(0, lower.length() - 1);
                    if (frenchDictionary.contains(singular)) {
                        suggestions = List.of(singular);
                    }
                }

                issues.add(new SpellCheckIssue(
                    wordStart, wordEnd, wordToCheck,
                    false, "Mot inconnu ou mal orthographié : '" + wordToCheck + "'",
                    suggestions
                ));
                markCovered(covered, wordStart, wordEnd);
            }
        }
    }

    private void checkFrenchGrammar(String text, List<SpellCheckIssue> issues, boolean[] covered) {
        // Règles d'accords grammaticaux et homophones
        // 1. Déterminants pluriels suivis de mots singuliers : "les faute", "des mot", "ces phrase", "mes ami"
        Pattern pPlural = Pattern.compile("(?Ui)\\b(les|des|ces|mes|tes|ses|nos|vos|leurs|plusieurs|quelques|deux|trois)\\s+([a-zàâäéèêëîïôöùûüç]+)\\b");
        Matcher mPlural = pPlural.matcher(text);
        while (mPlural.find()) {
            String det = mPlural.group(1);
            String noun = mPlural.group(2).toLowerCase(Locale.FRENCH);
            // Vérifier si le mot ne se termine pas déjà par s ou x
            if (!noun.endsWith("s") && !noun.endsWith("x") && !noun.endsWith("z") && noun.length() > 2) {
                // Exceptions invariables courantes
                if (!isFrenchInvariable(noun)) {
                    int nounStart = mPlural.start(2);
                    int nounEnd = mPlural.end(2);
                    String suggested = noun + "s";
                    issues.add(new SpellCheckIssue(
                        nounStart, nounEnd, mPlural.group(2),
                        true, "Accord en nombre : après '" + det + "', attendu au pluriel",
                        List.of(suggested)
                    ));
                    markCovered(covered, nounStart, nounEnd);
                }
            }
        }

        // 2. Homophone c'est / ses / ces : "ses pas vrai" -> "c'est pas vrai", "ses bien" -> "c'est bien"
        Pattern pCest = Pattern.compile("(?Ui)\\b(ses|ces)\\s+(pas|bien|vrai|faux|important|clair|possible|impossible|fini|parti|normal)\\b");
        Matcher mCest = pCest.matcher(text);
        while (mCest.find()) {
            int start = mCest.start(1);
            int end = mCest.end(1);
            issues.add(new SpellCheckIssue(
                start, end, mCest.group(1),
                true, "Confusion d'homophones : attendu 'c\\'est'",
                List.of("c'est")
            ));
            markCovered(covered, start, end);
        }

        // 3. Homophone sa / ça : "sa va" -> "ça va", "sa marche" -> "ça marche", "comme sa" -> "comme ça"
        Pattern pCa = Pattern.compile("(?Ui)\\b(sa)\\s+(va|marche|marche|suffit|serait|devrait|fait|donne)\\b");
        Matcher mCa = pCa.matcher(text);
        while (mCa.find()) {
            int start = mCa.start(1);
            int end = mCa.end(1);
            issues.add(new SpellCheckIssue(
                start, end, mCa.group(1),
                true, "Pronom démonstratif : attendu 'ça' au lieu du possessif 'sa'",
                List.of("ça")
            ));
            markCovered(covered, start, end);
        }
        Pattern pCommeSa = Pattern.compile("(?Ui)\\b(comme|pour)\\s+(sa)\\b");
        Matcher mCommeSa = pCommeSa.matcher(text);
        while (mCommeSa.find()) {
            int start = mCommeSa.start(2);
            int end = mCommeSa.end(2);
            issues.add(new SpellCheckIssue(
                start, end, mCommeSa.group(2),
                true, "Pronom démonstratif : attendu 'ça'",
                List.of("ça")
            ));
            markCovered(covered, start, end);
        }

        // 4. a vs à : "a demain" -> "à demain", "a bientôt" -> "à bientôt", "a côté" -> "à côté", "grâce a" -> "grâce à"
        Pattern pA = Pattern.compile("(?Ui)\\b(a)\\s+(demain|bientôt|côté|cause|travers|partir|nouveau|peine|fond|dieu|présent|l'heure|midi|droite|gauche)\\b");
        Matcher mA = pA.matcher(text);
        while (mA.find()) {
            int start = mA.start(1);
            int end = mA.end(1);
            issues.add(new SpellCheckIssue(
                start, end, mA.group(1),
                true, "Préposition : attendu 'à' avec accent",
                List.of("à")
            ));
            markCovered(covered, start, end);
        }

        // 5. Accord sujet / verbe : "ils va" -> "ils vont", "ils mange" -> "ils mangent", "tu a" -> "tu as", "tu est" -> "tu es"
        Pattern pIls = Pattern.compile("(?Ui)\\b(ils|elles)\\s+(va|a|est|fait|dit|vient|sait|peut|veut|doit)\\b");
        Matcher mIls = pIls.matcher(text);
        while (mIls.find()) {
            String verb = mIls.group(2).toLowerCase(Locale.FRENCH);
            String corr = switch (verb) {
                case "va" -> "vont";
                case "a" -> "ont";
                case "est" -> "sont";
                case "fait" -> "font";
                case "dit" -> "disent";
                case "vient" -> "viennent";
                case "sait" -> "savent";
                case "peut" -> "peuvent";
                case "veut" -> "veulent";
                case "doit" -> "doivent";
                default -> verb + "nt";
            };
            int start = mIls.start(2);
            int end = mIls.end(2);
            issues.add(new SpellCheckIssue(
                start, end, mIls.group(2),
                true, "Accord verbe au pluriel avec '" + mIls.group(1) + "'",
                List.of(corr)
            ));
            markCovered(covered, start, end);
        }

        Pattern pTu = Pattern.compile("(?Ui)\\b(tu)\\s+(a|est|va|veut|peut|sait|fait|dit|vient|doit)\\b");
        Matcher mTu = pTu.matcher(text);
        while (mTu.find()) {
            String verb = mTu.group(2).toLowerCase(Locale.FRENCH);
            String corr = switch (verb) {
                case "a" -> "as";
                case "est" -> "es";
                case "va" -> "vas";
                case "veut" -> "veux";
                case "peut" -> "peux";
                case "sait" -> "sais";
                case "fait" -> "fais";
                case "dit" -> "dis";
                case "vient" -> "viens";
                case "doit" -> "dois";
                default -> verb + "s";
            };
            int start = mTu.start(2);
            int end = mTu.end(2);
            issues.add(new SpellCheckIssue(
                start, end, mTu.group(2),
                true, "Accord 2e personne avec 'tu'",
                List.of(corr)
            ));
            markCovered(covered, start, end);
        }

        // 6. "je suis aller" -> "je suis allé"
        Pattern pAller = Pattern.compile("(?Ui)\\b(suis|es|est|sommes|êtes|sont)\\s+(aller)\\b");
        Matcher mAller = pAller.matcher(text);
        while (mAller.find()) {
            int start = mAller.start(2);
            int end = mAller.end(2);
            issues.add(new SpellCheckIssue(
                start, end, mAller.group(2),
                true, "Participe passé attendu après l'auxiliaire être",
                List.of("allé", "allée")
            ));
            markCovered(covered, start, end);
        }
    }

    private boolean isFrenchInvariable(String word) {
        return switch (word) {
            case "fois", "mois", "pays", "temps", "corps", "choix", "prix", "voix", "souris",
                 "bras", "cours", "gaz", "nez", "héros", "gens", "dehors", "dedans", "toujours",
                 "moins", "plus", "assez", "beaucoup", "rien", "personne" -> true;
            default -> false;
        };
    }

    // =========================================================================
    //                            LOGIQUE ANGLAISE
    // =========================================================================

    private void checkEnglish(String text, List<SpellCheckIssue> issues) {
        boolean[] covered = new boolean[text.length()];

        checkEnglishGrammar(text, issues, covered);

        Matcher matcher = Pattern.compile("(?U)[\\p{L}\\p{M}'-]+").matcher(text);
        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();
            String rawWord = matcher.group();

            if (isRangeCovered(covered, start, end)) {
                continue;
            }

            String lower = rawWord.toLowerCase(Locale.ENGLISH);

            if (lower.length() <= 1 && !lower.equals("a") && !lower.equals("i")) {
                continue;
            }

            if (ignoredWords.contains(lower)) {
                continue;
            }

            // Typos fréquents en anglais
            List<String> directTypos = englishTypos.get(lower);
            if (directTypos != null && !directTypos.isEmpty()) {
                issues.add(new SpellCheckIssue(
                    start, end, rawWord,
                    false, "Spelling mistake : '" + rawWord + "'",
                    directTypos
                ));
                markCovered(covered, start, end);
                continue;
            }

            if (!englishDictionary.contains(lower)) {
                if (isAllUpper(rawWord)) {
                    continue;
                }

                List<String> suggestions = findSuggestions(lower, englishDictionary, 2);
                issues.add(new SpellCheckIssue(
                    start, end, rawWord,
                    false, "Unknown or misspelled word : '" + rawWord + "'",
                    suggestions
                ));
                markCovered(covered, start, end);
            }
        }
    }

    private void checkEnglishGrammar(String text, List<SpellCheckIssue> issues, boolean[] covered) {
        // 1. Article indéfini a / an
        // "a" devant une voyelle : "a apple" -> "an apple"
        Pattern pA = Pattern.compile("(?Ui)\\b(a)\\s+([aeiou][a-z]+)\\b");
        Matcher mA = pA.matcher(text);
        while (mA.find()) {
            String word = mA.group(2).toLowerCase(Locale.ENGLISH);
            // Exceptions comme "a university", "a user", "a European"
            if (!word.startsWith("uni") && !word.startsWith("use") && !word.startsWith("europ") && !word.startsWith("one")) {
                int start = mA.start(1);
                int end = mA.end(1);
                issues.add(new SpellCheckIssue(
                    start, end, mA.group(1),
                    true, "Article 'an' expected before vowel sound '" + mA.group(2) + "'",
                    List.of("an")
                ));
                markCovered(covered, start, end);
            }
        }

        // "an" devant consonne : "an car" -> "a car"
        Pattern pAn = Pattern.compile("(?Ui)\\b(an)\\s+([bcdfghjklmnpqrstvwxyz][a-z]+)\\b");
        Matcher mAn = pAn.matcher(text);
        while (mAn.find()) {
            String word = mAn.group(2).toLowerCase(Locale.ENGLISH);
            // Exceptions comme "an hour", "an honest"
            if (!word.startsWith("hour") && !word.startsWith("honest") && !word.startsWith("honor")) {
                int start = mAn.start(1);
                int end = mAn.end(1);
                issues.add(new SpellCheckIssue(
                    start, end, mAn.group(1),
                    true, "Article 'a' expected before consonant sound '" + mAn.group(2) + "'",
                    List.of("a")
                ));
                markCovered(covered, start, end);
            }
        }

        // 2. Accord sujet / verbe pluriel : "they goes" -> "they go", "they was" -> "they were"
        Pattern pThey = Pattern.compile("(?Ui)\\b(they|we|you)\\s+(goes|was|does|has|is)\\b");
        Matcher mThey = pThey.matcher(text);
        while (mThey.find()) {
            String verb = mThey.group(2).toLowerCase(Locale.ENGLISH);
            String corr = switch (verb) {
                case "goes" -> "go";
                case "was" -> "were";
                case "does" -> "do";
                case "has" -> "have";
                case "is" -> "are";
                default -> verb;
            };
            int start = mThey.start(2);
            int end = mThey.end(2);
            issues.add(new SpellCheckIssue(
                start, end, mThey.group(2),
                true, "Subject-verb agreement: expected plural form after '" + mThey.group(1) + "'",
                List.of(corr)
            ));
            markCovered(covered, start, end);
        }

        // 3. Accord singulier he/she/it : "he go" -> "he goes", "she don't" -> "she doesn't"
        Pattern pHe = Pattern.compile("(?Ui)\\b(he|she|it)\\s+(go|do|have|don't|are)\\b");
        Matcher mHe = pHe.matcher(text);
        while (mHe.find()) {
            String verb = mHe.group(2).toLowerCase(Locale.ENGLISH);
            String corr = switch (verb) {
                case "go" -> "goes";
                case "do" -> "does";
                case "have" -> "has";
                case "don't" -> "doesn't";
                case "are" -> "is";
                default -> verb + "s";
            };
            int start = mHe.start(2);
            int end = mHe.end(2);
            issues.add(new SpellCheckIssue(
                start, end, mHe.group(2),
                true, "Subject-verb agreement: expected 3rd person singular after '" + mHe.group(1) + "'",
                List.of(corr)
            ));
            markCovered(covered, start, end);
        }

        // 4. Confusions courantes en anglais : "your welcome" -> "you're welcome"
        Pattern pYourWelcome = Pattern.compile("(?Ui)\\b(your)\\s+(welcome)\\b");
        Matcher mYW = pYourWelcome.matcher(text);
        while (mYW.find()) {
            int start = mYW.start(1);
            int end = mYW.end(1);
            issues.add(new SpellCheckIssue(
                start, end, mYW.group(1),
                true, "Did you mean 'you\\'re welcome'?",
                List.of("you're")
            ));
            markCovered(covered, start, end);
        }

        // "could of" -> "could have", "should of" -> "should have"
        Pattern pCouldOf = Pattern.compile("(?Ui)\\b(could|should|would|must)\\s+(of)\\b");
        Matcher mCO = pCouldOf.matcher(text);
        while (mCO.find()) {
            int start = mCO.start(2);
            int end = mCO.end(2);
            issues.add(new SpellCheckIssue(
                start, end, mCO.group(2),
                true, "Grammar error: 'have' expected after auxiliary modal",
                List.of("have")
            ));
            markCovered(covered, start, end);
        }
    }

    // =========================================================================
    //                        ALGORITHMES DE SUGGESTIONS
    // =========================================================================

    private List<String> findSuggestions(String word, Set<String> dict, int maxDist) {
        if (word == null || word.isEmpty()) return Collections.emptyList();
        List<Candidate> candidates = new ArrayList<>();
        int len = word.length();

        for (String dictWord : dict) {
            // Optimisation : ignorer les mots de longueur trop différente
            if (Math.abs(dictWord.length() - len) > maxDist) {
                continue;
            }
            int dist = damerauLevenshteinDistance(word, dictWord, maxDist);
            if (dist <= maxDist) {
                candidates.add(new Candidate(dictWord, dist));
            }
        }

        candidates.sort(Comparator.comparingInt(c -> c.distance));
        List<String> result = new ArrayList<>();
        for (int i = 0; i < Math.min(3, candidates.size()); i++) {
            result.add(candidates.get(i).word);
        }
        return result;
    }

    private static class Candidate {
        final String word;
        final int distance;
        Candidate(String word, int distance) {
            this.word = word;
            this.distance = distance;
        }
    }

    private int damerauLevenshteinDistance(String s1, String s2, int maxAllowed) {
        int len1 = s1.length();
        int len2 = s2.length();
        int[][] d = new int[len1 + 1][len2 + 1];

        for (int i = 0; i <= len1; i++) d[i][0] = i;
        for (int j = 0; j <= len2; j++) d[0][j] = j;

        for (int i = 1; i <= len1; i++) {
            int minInRow = Integer.MAX_VALUE;
            for (int j = 1; j <= len2; j++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                d[i][j] = Math.min(
                    Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1),
                    d[i - 1][j - 1] + cost
                );
                // Transposition
                if (i > 1 && j > 1 &&
                    s1.charAt(i - 1) == s2.charAt(j - 2) &&
                    s1.charAt(i - 2) == s2.charAt(j - 1)) {
                    d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
                }
                minInRow = Math.min(minInRow, d[i][j]);
            }
            if (minInRow > maxAllowed) return maxAllowed + 1;
        }
        return d[len1][len2];
    }

    private boolean isAllUpper(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isLetter(s.charAt(i)) && !Character.isUpperCase(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private boolean isRangeCovered(boolean[] covered, int start, int end) {
        for (int i = start; i < end && i < covered.length; i++) {
            if (covered[i]) return true;
        }
        return false;
    }

    private void markCovered(boolean[] covered, int start, int end) {
        for (int i = start; i < end && i < covered.length; i++) {
            covered[i] = true;
        }
    }

    // =========================================================================
    //                        INITIALISATION DES DICTIONNAIRES
    // =========================================================================

    private void initFrench() {
        // Table des fautes courantes en français avec suggestions directes
        frenchTypos.put("orthograaphe", List.of("orthographe"));
        frenchTypos.put("orthografe", List.of("orthographe"));
        frenchTypos.put("acceuil", List.of("accueil"));
        frenchTypos.put("apres", List.of("après"));
        frenchTypos.put("tres", List.of("très"));
        frenchTypos.put("deja", List.of("déjà"));
        frenchTypos.put("meme", List.of("même"));
        frenchTypos.put("memes", List.of("mêmes"));
        frenchTypos.put("etre", List.of("être"));
        frenchTypos.put("bizard", List.of("bizarre"));
        frenchTypos.put("parmis", List.of("parmi"));
        frenchTypos.put("malgrès", List.of("malgré"));
        frenchTypos.put("connection", List.of("connexion"));
        frenchTypos.put("language", List.of("langage"));
        frenchTypos.put("aujourdui", List.of("aujourd'hui"));
        frenchTypos.put("aujourdhui", List.of("aujourd'hui"));
        frenchTypos.put("daccord", List.of("d'accord"));
        frenchTypos.put("peut etre", List.of("peut-être"));
        frenchTypos.put("developpement", List.of("développement"));
        frenchTypos.put("dévelopement", List.of("développement"));
        frenchTypos.put("evenement", List.of("événement"));
        frenchTypos.put("reunion", List.of("réunion"));
        frenchTypos.put("cinema", List.of("cinéma"));
        frenchTypos.put("video", List.of("vidéo"));
        frenchTypos.put("comedien", List.of("comédien"));
        frenchTypos.put("comedienne", List.of("comédienne"));
        frenchTypos.put("repere", List.of("repère"));
        frenchTypos.put("reperes", List.of("repères"));
        frenchTypos.put("rythmeur", List.of("rythmeur"));
        frenchTypos.put("voila", List.of("voilà"));
        frenchTypos.put("bonjoure", List.of("bonjour"));
        frenchTypos.put("baucoup", List.of("beaucoup"));
        frenchTypos.put("sil", List.of("s'il"));
        frenchTypos.put("quand meme", List.of("quand même"));

        // Dictionnaire français de base (mots clés doublage, dialogues, cinéma, vie courante)
        String[] frWords = {
            "bonjour", "bonsoir", "salut", "merci", "beaucoup", "oui", "non", "voilà", "voici",
            "aujourd'hui", "demain", "hier", "maintenant", "toujours", "jamais", "parfois", "souvent",
            "film", "films", "voix", "acteur", "actrice", "acteurs", "actrices", "comédien", "comédienne",
            "doublage", "rythmo", "bande", "bandes", "ligne", "lignes", "texte", "textes", "parole", "paroles",
            "phrase", "phrases", "mot", "mots", "son", "sons", "audio", "vidéo", "vidéos", "musique",
            "synchro", "synchronisation", "labiale", "labial", "temps", "seconde", "secondes", "minute", "minutes",
            "heure", "heures", "début", "fin", "repère", "repères", "pause", "respiration", "souffle",
            "accord", "accords", "orthographe", "grammaire", "erreur", "erreurs", "faute", "fautes",
            "homme", "hommes", "femme", "femmes", "enfant", "enfants", "garçon", "fille", "ami", "amis", "amie", "amies",
            "maison", "porte", "fenêtre", "rue", "ville", "pays", "monde", "vie", "mort", "cœur", "yeux", "regard",
            "travail", "projet", "histoire", "scène", "scènes", "rôle", "rôles", "personnage", "personnages",
            "bien", "mal", "très", "trop", "assez", "plus", "moins", "aussi", "encore", "enfin", "alors",
            "après", "avant", "pendant", "depuis", "avec", "sans", "sous", "sur", "dans", "par", "pour",
            "grand", "grande", "grands", "grandes", "petit", "petite", "petits", "petites", "beau", "belle",
            "nouveau", "nouvelle", "nouveaux", "nouvelles", "bon", "bonne", "bons", "bonnes", "mauvais",
            "vrai", "vraie", "vrais", "vraies", "faux", "fausse", "seul", "seule", "autre", "autres",
            "même", "mêmes", "tout", "tous", "toute", "toutes", "chaque", "plusieurs", "quelque", "quelques",
            "je", "tu", "il", "elle", "on", "nous", "vous", "ils", "elles", "me", "te", "se", "lui", "leur",
            "moi", "toi", "soi", "mon", "ma", "mes", "ton", "ta", "tes", "son", "sa", "ses", "notre", "nos", "votre", "vos",
            "le", "la", "les", "un", "une", "des", "du", "de", "ce", "cet", "cette", "ces", "qui", "que", "quoi", "dont", "où",
            "suis", "es", "est", "sommes", "êtes", "sont", "été", "était", "étais", "étions", "étiez", "étaient",
            "ai", "as", "a", "avons", "avez", "ont", "eu", "avait", "avais", "avions", "aviez", "avaient",
            "fais", "fait", "faisons", "faites", "font", "vais", "vas", "va", "allons", "allez", "vont",
            "dis", "dit", "disons", "dites", "disent", "peux", "peut", "pouvons", "pouvez", "peuvent",
            "veux", "veut", "voulons", "voulez", "veulent", "sais", "sait", "savons", "savez", "savent",
            "vois", "voit", "voyons", "voyez", "voient", "viens", "vient", "venons", "venez", "viennent",
            "dois", "doit", "devons", "devez", "doivent", "faut", "parle", "parles", "parlent", "parlons", "parlez",
            "pense", "penses", "pensent", "aimer", "aime", "aimes", "aiment", "comprends", "comprend", "comprennent",
            "attends", "attend", "attendent", "écoute", "écoutes", "écoutent", "regarde", "regardes", "regardent",
            "allé", "allée", "allés", "allées", "venu", "venue", "venus", "venues", "parti", "partie", "partis", "parties",
            "pris", "prise", "mis", "mise", "vu", "vue", "entendu", "entendue", "compris", "comprise",
            "comment", "pourquoi", "quand", "combien", "parce", "mais", "ou", "et", "donc", "or", "ni", "car",
            "facile", "difficile", "rapide", "lent", "lente", "clair", "claire", "sombre", "silence", "bruit",
            "studio", "micro", "caméra", "image", "images", "couleur", "couleurs", "forme", "formes"
        };
        for (String w : frWords) {
            frenchDictionary.add(w.toLowerCase(Locale.FRENCH));
        }
    }

    private void initEnglish() {
        // Table des fautes courantes en anglais avec suggestions directes
        englishTypos.put("teh", List.of("the"));
        englishTypos.put("recieve", List.of("receive"));
        englishTypos.put("definately", List.of("definitely"));
        englishTypos.put("seperate", List.of("separate"));
        englishTypos.put("goverment", List.of("government"));
        englishTypos.put("untill", List.of("until"));
        englishTypos.put("wierd", List.of("weird"));
        englishTypos.put("beleive", List.of("believe"));
        englishTypos.put("occured", List.of("occurred"));
        englishTypos.put("neccessary", List.of("necessary"));
        englishTypos.put("tommorow", List.of("tomorrow"));
        englishTypos.put("enviroment", List.of("environment"));
        englishTypos.put("wich", List.of("which"));
        englishTypos.put("truely", List.of("truly"));
        englishTypos.put("suprise", List.of("surprise"));
        englishTypos.put("realy", List.of("really"));
        englishTypos.put("thier", List.of("their"));
        englishTypos.put("alot", List.of("a lot"));
        englishTypos.put("noone", List.of("no one"));

        String[] enWords = {
            "hello", "hi", "good", "morning", "evening", "thanks", "thank", "you", "yes", "no",
            "today", "tomorrow", "yesterday", "now", "always", "never", "sometimes", "often",
            "movie", "film", "voice", "actor", "actress", "dubbing", "audio", "video", "music",
            "track", "tracks", "line", "lines", "text", "texts", "word", "words", "speech",
            "sentence", "sentences", "sound", "sounds", "sync", "lip", "rhythm", "band", "bands",
            "time", "second", "seconds", "minute", "minutes", "hour", "hours", "start", "end",
            "marker", "markers", "pause", "breath", "spelling", "grammar", "error", "mistake",
            "man", "men", "woman", "women", "child", "children", "boy", "girl", "friend", "friends",
            "house", "door", "window", "street", "city", "country", "world", "life", "death", "heart",
            "eyes", "eye", "look", "work", "project", "story", "scene", "scenes", "role", "roles",
            "character", "characters", "well", "bad", "very", "too", "enough", "more", "less", "also",
            "again", "finally", "then", "after", "before", "during", "since", "with", "without",
            "under", "on", "in", "by", "for", "big", "small", "beautiful", "new", "great", "real",
            "true", "false", "alone", "other", "others", "same", "all", "every", "some", "any",
            "i", "you", "he", "she", "it", "we", "they", "me", "him", "her", "us", "them", "my",
            "your", "his", "its", "our", "their", "mine", "yours", "hers", "ours", "theirs",
            "the", "a", "an", "this", "that", "these", "those", "who", "which", "what", "where", "when",
            "be", "am", "is", "are", "was", "were", "been", "being", "have", "has", "had", "having",
            "do", "does", "did", "doing", "done", "say", "says", "said", "saying", "go", "goes", "went", "gone",
            "make", "makes", "made", "know", "knows", "knew", "known", "think", "thinks", "thought",
            "take", "takes", "took", "taken", "see", "sees", "saw", "seen", "come", "comes", "came",
            "want", "wants", "wanted", "look", "looks", "looked", "use", "uses", "used", "find", "finds",
            "give", "gives", "gave", "given", "tell", "tells", "told", "work", "works", "worked",
            "call", "calls", "called", "try", "tries", "tried", "ask", "asks", "asked", "need", "needs",
            "feel", "feels", "felt", "become", "becomes", "became", "leave", "leaves", "left",
            "don't", "doesn't", "didn't", "won't", "wouldn't", "can't", "couldn't", "shouldn't",
            "haven't", "hasn't", "isn't", "aren't", "wasn't", "weren't", "i'm", "you're", "he's", "she's",
            "it's", "we're", "they're", "i've", "you've", "we've", "they've", "i'll", "you'll", "let's",
            "how", "why", "because", "but", "or", "and", "so", "if", "easy", "hard", "fast", "slow",
            "clear", "dark", "silence", "noise", "studio", "mic", "microphone", "camera", "color",
            "watch", "watches", "watched", "watching", "play", "plays", "played", "playing",
            "run", "runs", "ran", "running", "read", "reads", "reading", "write", "writes", "wrote", "written",
            "open", "opens", "opened", "opening", "close", "closes", "closed", "closing",
            "stop", "stops", "stopped", "stopping", "live", "lives", "lived", "living"
        };
        for (String w : enWords) {
            englishDictionary.add(w.toLowerCase(Locale.ENGLISH));
        }
    }
}
