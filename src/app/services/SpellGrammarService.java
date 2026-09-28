package app.services;

import app.ui.TextItem;

import java.io.*;
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

            // Si le mot est valide en français (lexique riche, pluriels -s/-es/-x, conjugaisons régulières)
            if (isFrenchWordValid(lower)) {
                continue;
            }

            // Si c'est tout en majuscules (acronyme) ou commence par une majuscule (nom propre ou début de phrase)
            if (isAllUpper(wordToCheck) || (Character.isUpperCase(wordToCheck.charAt(0)) && wordStart > 0)) {
                continue;
            }

            // Recherche de suggestions via distance de Levenshtein
            List<String> suggestions = findSuggestions(lower, frenchDictionary, 2);
            issues.add(new SpellCheckIssue(
                wordStart, wordEnd, wordToCheck,
                false, "Mot inconnu ou mal orthographié : '" + wordToCheck + "'",
                suggestions
            ));
            markCovered(covered, wordStart, wordEnd);
        }
    }

    private void checkFrenchGrammar(String text, List<SpellCheckIssue> issues, boolean[] covered) {
        // Règles d'accords grammaticaux et homophones
        // 1. Déterminants pluriels suivis de mots singuliers : "les faute", "des mot", "ces phrase", "mes ami"
        Pattern pPlural = Pattern.compile("(?Ui)(?:^|\\s)(les|des|ces|mes|tes|ses|nos|vos|leurs|plusieurs|quelques|deux|trois)\\s+([a-zàâäéèêëîïôöùûüç]+)\\b");
        Matcher mPlural = pPlural.matcher(text);
        while (mPlural.find()) {
            String det = mPlural.group(1).toLowerCase(Locale.FRENCH);
            String noun = mPlural.group(2).toLowerCase(Locale.FRENCH);
            int matchStart = mPlural.start();

            // Verbes à l'infinitif (ex: les aimer, les voir, les prendre) : jamais d'accord avec un 's'
            if (noun.endsWith("er") || noun.endsWith("ir") || noun.endsWith("re")) {
                continue;
            }

            // Dans "je les demande", "il les aime", "pour les aider" : 'les' est pronom objet direct
            if (det.equals("les")) {
                String before = text.substring(0, mPlural.start(1)).trim().toLowerCase(Locale.FRENCH);
                if (before.endsWith("je") || before.endsWith("tu") || before.endsWith("il") ||
                    before.endsWith("elle") || before.endsWith("on") || before.endsWith("nous") ||
                    before.endsWith("vous") || before.endsWith("ils") || before.endsWith("elles") ||
                    before.endsWith("qui") || before.endsWith("que") || before.endsWith("qu'") ||
                    before.endsWith("ne") || before.endsWith("n'") || before.endsWith("pour") ||
                    before.endsWith("sans") || before.endsWith("de") || before.endsWith("d'")) {
                    continue;
                }
            }

            // Vérifier si le mot ne se termine pas déjà par s ou x ou z
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
        // Note : "ses pas" seul est valide en français ("le bruit de ses pas"), on ne cible donc "ses pas" que suivi d'un adjectif ("ses pas vrai")
        Pattern pCest = Pattern.compile("(?Ui)\\b(ses|ces)\\s+(?:pas\\s+(vrai|bien|faux|fini|clair|possible|impossible|normal|grave)|(bien|vrai|faux|important|clair|possible|impossible|fini|parti|normal))\\b");
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

            if (isEnglishWordValid(lower)) {
                continue;
            }

            if (isAllUpper(rawWord) || (Character.isUpperCase(rawWord.charAt(0)) && start > 0)) {
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
        String lower = word.toLowerCase(Locale.ROOT);

        for (String dictWord : dict) {
            // Optimisation : ignorer les mots de longueur trop différente
            if (Math.abs(dictWord.length() - len) > maxDist) {
                continue;
            }
            int dist = damerauLevenshteinDistance(lower, dictWord, maxDist);
            if (dist <= maxDist) {
                int commonPrefix = 0;
                int minL = Math.min(lower.length(), dictWord.length());
                while (commonPrefix < minL && lower.charAt(commonPrefix) == dictWord.charAt(commonPrefix)) {
                    commonPrefix++;
                }
                candidates.add(new Candidate(dictWord, dist, commonPrefix, Math.abs(dictWord.length() - len)));
            }
        }

        candidates.sort(Comparator
            .comparingInt((Candidate c) -> c.distance)
            .thenComparing(Comparator.comparingInt((Candidate c) -> c.commonPrefix).reversed())
            .thenComparingInt(c -> c.lenDiff)
            .thenComparing(c -> c.word));

        List<String> result = new ArrayList<>();
        for (int i = 0; i < Math.min(3, candidates.size()); i++) {
            result.add(candidates.get(i).word);
        }
        return result;
    }

    private static class Candidate {
        final String word;
        final int distance;
        final int commonPrefix;
        final int lenDiff;

        Candidate(String word, int distance, int commonPrefix, int lenDiff) {
            this.word = word;
            this.distance = distance;
            this.commonPrefix = commonPrefix;
            this.lenDiff = lenDiff;
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
        frenchTypos.put("guarde", List.of("garde"));
        frenchTypos.put("guardes", List.of("gardes"));
        frenchTypos.put("guarder", List.of("garder"));
        frenchTypos.put("guardien", List.of("gardien"));
        frenchTypos.put("guardiens", List.of("gardiens"));
        frenchTypos.put("guardienne", List.of("gardienne"));
        frenchTypos.put("guardiennes", List.of("gardiennes"));

        // Dictionnaire français enrichi (mots clés doublage, dialogues, cinéma, vie courante)
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
            "maison", "porte", "portes", "fenêtre", "fenêtres", "rue", "rues", "ville", "villes", "pays", "monde",
            "vie", "mort", "cœur", "yeux", "regard", "regards", "corps", "visage", "visages", "main", "mains",
            "garde", "gardes", "garder", "gardé", "gardée", "gardés", "gardées", "gardez", "gardons", "gardait", "gardaient", "gardera", "garderont",
            "gardien", "gardiens", "gardienne", "gardiennes",
            "travail", "projet", "histoire", "histoires", "scène", "scènes", "rôle", "rôles", "personnage", "personnages",
            "bien", "mal", "très", "trop", "assez", "plus", "moins", "aussi", "encore", "enfin", "alors",
            "après", "avant", "pendant", "depuis", "avec", "sans", "sous", "sur", "dans", "par", "pour",
            "grand", "grande", "grands", "grandes", "petit", "petite", "petits", "petites", "beau", "belle", "beaux", "belles",
            "nouveau", "nouvelle", "nouveaux", "nouvelles", "bon", "bonne", "bons", "bonnes", "mauvais", "mauvaise", "mauvaises",
            "vrai", "vraie", "vrais", "vraies", "faux", "fausse", "fausses", "seul", "seule", "seuls", "seules", "autre", "autres",
            "même", "mêmes", "tout", "tous", "toute", "toutes", "chaque", "plusieurs", "quelque", "quelques",
            "je", "tu", "il", "elle", "on", "nous", "vous", "ils", "elles", "me", "te", "se", "lui", "leur", "leurs",
            "moi", "toi", "soi", "mon", "ma", "mes", "ton", "ta", "tes", "son", "sa", "ses", "notre", "nos", "votre", "vos",
            "le", "la", "les", "un", "une", "des", "du", "de", "ce", "cet", "cette", "ces", "ceci", "cela", "ça", "qui", "que", "quoi", "dont", "où",
            "suis", "es", "est", "sommes", "êtes", "sont", "été", "était", "étais", "étions", "étiez", "étaient", "serai", "seras", "sera", "serons", "serez", "seront", "serait", "seraient",
            "ai", "as", "a", "avons", "avez", "ont", "eu", "avait", "avais", "avions", "aviez", "avaient", "aurai", "auras", "aura", "aurons", "aurez", "auront", "aurait", "auraient",
            "fais", "fait", "faite", "faits", "faites", "faisons", "font", "faisait", "faisaient", "ferai", "fera", "ferons", "feront", "ferait", "faire",
            "vais", "vas", "va", "allons", "allez", "vont", "allé", "allée", "allés", "allées", "allait", "allaient", "irai", "iras", "ira", "irons", "irez", "iront", "aller",
            "dis", "dit", "dite", "dits", "dites", "disons", "disent", "disait", "disaient", "dirai", "dira", "dire",
            "peux", "peut", "pouvons", "pouvez", "peuvent", "pu", "pouvait", "pouvaient", "pourrai", "pourra", "pourrait", "pourraient", "pouvoir",
            "veux", "veut", "voulons", "voulez", "veulent", "voulu", "voulait", "voulaient", "voudrai", "voudra", "voudrait", "voudraient", "vouloir",
            "sais", "sait", "savons", "savez", "savent", "su", "savait", "savaient", "saurai", "saura", "saurait", "savoir",
            "vois", "voit", "voyons", "voyez", "voient", "vu", "vue", "vus", "vues", "voyait", "voyaient", "verrai", "verra", "verrait", "voir",
            "viens", "vient", "venons", "venez", "viennent", "venu", "venue", "venus", "venues", "venait", "venaient", "viendrai", "viendra", "venir",
            "dois", "doit", "devons", "devez", "doivent", "dû", "due", "dus", "dues", "devait", "devaient", "devrai", "devra", "devrait", "devoir", "falloir", "faut", "fallait", "faudra", "faudrait",
            "parle", "parles", "parlent", "parlons", "parlez", "parlé", "parlait", "parlaient", "parler",
            "pense", "penses", "pensent", "pensons", "pensez", "pensé", "pensait", "pensaient", "penser",
            "crois", "croit", "croyons", "croyez", "croient", "cru", "croyait", "croire",
            "aime", "aimes", "aiment", "aimons", "aimez", "aimé", "aimait", "aimer",
            "comprends", "comprend", "comprenons", "comprenez", "comprennent", "compris", "comprise", "comprendre",
            "attends", "attend", "attendons", "attendez", "attendent", "attendu", "attendue", "attendre",
            "écoute", "écoutes", "écoutent", "écoutons", "écoutez", "écouté", "écouter",
            "regarde", "regardes", "regardent", "regardons", "regardez", "regardé", "regarder",
            "cherche", "cherches", "cherchent", "cherchons", "cherchez", "cherché", "chercher",
            "trouve", "trouves", "trouvent", "trouvons", "trouvez", "trouvé", "trouver",
            "donne", "donnes", "donnent", "donnons", "donnez", "donné", "donner",
            "laisse", "laisses", "laissent", "laissons", "laissez", "laissé", "laisser",
            "prends", "prend", "prenons", "prenez", "prennent", "pris", "prise", "prendre",
            "mets", "met", "mettons", "mettez", "mettent", "mis", "mise", "mettre",
            "tiens", "tient", "tenons", "tenez", "tiennent", "tenu", "tenir",
            "passe", "passes", "passent", "passons", "passez", "passé", "passer",
            "reste", "restes", "restent", "restons", "restez", "resté", "rester",
            "pars", "part", "partons", "partez", "partent", "parti", "partie", "partir",
            "sors", "sort", "sortons", "sortez", "sortent", "sorti", "sortie", "sortir",
            "entre", "entres", "entrent", "entrons", "entrez", "entré", "entrer",
            "aide", "aides", "aident", "aidons", "aidez", "aidé", "aider",
            "appelle", "appelles", "appellent", "appelons", "appelez", "appelé", "appeler",
            "joue", "joues", "jouent", "jouons", "jouez", "joué", "jouer",
            "ouvre", "ouvres", "ouvrent", "ouvrons", "ouvrez", "ouvert", "ouverte", "ouvrir",
            "ferme", "fermes", "ferment", "fermons", "fermez", "fermé", "fermer",
            "arrête", "arrêtes", "arrêtent", "arrêtons", "arrêtez", "arrêté", "arrêter",
            "continue", "continues", "continuent", "continuons", "continuez", "continué", "continuer",
            "essaie", "essaies", "essaient", "essayons", "essayez", "essayé", "essayer",
            "oublie", "oublies", "oublient", "oublions", "oubliez", "oublié", "oublier",
            "perds", "perd", "perdons", "perdez", "perdent", "perdu", "perdue", "perdre",
            "gagne", "gagnes", "gagnent", "gagnons", "gagnez", "gagné", "gagner",
            "marche", "marches", "marchent", "marchons", "marchez", "marché", "marcher",
            "cours", "court", "courons", "courez", "courent", "couru", "courir",
            "sens", "sent", "sentons", "sentez", "sentent", "senti", "sentir",
            "vis", "vit", "vivons", "vivez", "vivent", "vécu", "vivre",
            "meurs", "meurt", "mourons", "mourez", "meurent", "mort", "morte", "mourir",
            "arrive", "arrives", "arrivent", "arrivons", "arrivez", "arrivé", "arriver",
            "tombe", "tombes", "tombent", "tombons", "tombez", "tombé", "tomber",
            "semble", "sembles", "semblent", "semblé", "sembler",
            "comment", "pourquoi", "quand", "combien", "parce", "mais", "ou", "et", "donc", "or", "ni", "car",
            "facile", "difficile", "rapide", "rapides", "lent", "lente", "lents", "lentes", "clair", "claire", "clairs", "claires",
            "sombre", "sombres", "silence", "bruit", "bruits", "calme", "secret", "secrets", "vérité", "mensonge", "mensonges",
            "studio", "micro", "micros", "caméra", "caméras", "image", "images", "couleur", "couleurs", "forme", "formes",
            "pardon", "désolé", "désolée", "plaît", "merci", "bravo", "adieu", "bientôt", "ensemble", "seuls",
            "absolument", "vraiment", "certainement", "sûrement", "peut-être", "exactement", "parfaitement", "naturellement",
            "chose", "choses", "rien", "personne", "quelqu'un", "quelque", "n'importe", "partout", "ailleurs",
            "ici", "là", "là-bas", "haut", "bas", "devant", "derrière", "dedans", "dehors", "près", "loin",
            "capitaine", "lieutenant", "chef", "maître", "docteur", "professeur", "inspecteur", "agent", "soldat",
            "porte", "chambre", "salle", "bureau", "table", "chaise", "lit", "voiture", "train", "avion", "bateau"
        };
        for (String w : frWords) {
            frenchDictionary.add(w.toLowerCase(Locale.FRENCH));
        }

        // Chargement du dictionnaire français complet (84 000+ mots)
        loadDictionaryFile("/dictionaries/fr.dic", frenchDictionary, "src/dictionaries/fr.dic");
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

        // Chargement du dictionnaire anglais complet (50 000+ mots)
        loadDictionaryFile("/dictionaries/en.dic", englishDictionary, "src/dictionaries/en.dic");
    }

    private void loadDictionaryFile(String resourcePath, Set<String> targetDict, String fallbackPath) {
        // 1. Tenter via ClassLoader
        try (InputStream is = SpellGrammarService.class.getResourceAsStream(resourcePath)) {
            if (is != null) {
                readDictionaryStream(is, targetDict);
                return;
            }
        } catch (Exception ignored) {}

        // 2. Tenter via fichiers locaux
        File[] candidates = {
            new File("bin" + resourcePath),
            new File("src" + resourcePath),
            new File(fallbackPath),
            new File("dictionaries/" + new File(resourcePath).getName()),
            new File("test_dict.txt")
        };
        for (File f : candidates) {
            if (f.exists() && f.isFile()) {
                try (InputStream is = new FileInputStream(f)) {
                    readDictionaryStream(is, targetDict);
                    return;
                } catch (Exception ignored) {}
            }
        }
    }

    private void readDictionaryStream(InputStream is, Set<String> targetDict) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#") || line.matches("^\\d+$")) continue;
                String word = line.split("[/\\s]")[0].trim().toLowerCase(Locale.ROOT);
                if (!word.isEmpty()) {
                    word = java.text.Normalizer.normalize(word, java.text.Normalizer.Form.NFC);
                    targetDict.add(word);
                    if (line.contains("/S") || line.contains("po:nom") || line.contains("po:adj")) {
                        targetDict.add(word + "s");
                    }
                }
            }
        }
    }

    public boolean isFrenchWordValid(String lower) {
        if (lower == null || lower.isEmpty()) return true;
        lower = java.text.Normalizer.normalize(lower, java.text.Normalizer.Form.NFC);
        // 1. Mot exact dans le dictionnaire
        if (frenchDictionary.contains(lower)) return true;

        // 2. Gestion des pluriels en -s (ex: gestions -> gestion, problèmes -> problème, gardes -> garde)
        if (lower.endsWith("s")) {
            String stem = lower.substring(0, lower.length() - 1);
            if (frenchDictionary.contains(stem)) return true;
        }

        // 3. Gestion des pluriels féminins en -es (ex: grandes -> grand, grandes -> grande)
        if (lower.endsWith("es")) {
            String stem1 = lower.substring(0, lower.length() - 1);
            if (frenchDictionary.contains(stem1)) return true;
            String stem2 = lower.substring(0, lower.length() - 2);
            if (frenchDictionary.contains(stem2)) return true;
        }

        // 4. Gestion des pluriels en -x (ex: bijoux -> bijou, chevaux -> cheval, yeux -> œil)
        if (lower.endsWith("x")) {
            if (frenchDictionary.contains(lower.substring(0, lower.length() - 1))) return true;
            if (lower.endsWith("aux") && frenchDictionary.contains(lower.substring(0, lower.length() - 3) + "al")) return true;
            if (lower.endsWith("eaux") && frenchDictionary.contains(lower.substring(0, lower.length() - 1))) return true;
        }

        // 5. Verbes du 1er groupe (-er) : plus de 90% des verbes français (ex: créer, parler, aimer)
        String[] erEndings = {
            "aient", "erions", "eriez", "eront", "erons",
            "erait", "erais", "assent", "assiez", "assions",
            "erez", "eras", "era", "ions", "iez", "âmes", "âtes",
            "ent", "ons", "ait", "ais", "iez", "ant",
            "ez", "es", "ai", "as", "ât", "ée", "és", "ées", "é", "e"
        };
        for (String ending : erEndings) {
            if (lower.endsWith(ending) && lower.length() > ending.length() + 1) {
                String root = lower.substring(0, lower.length() - ending.length());
                String inf = root + "er";
                if (frenchDictionary.contains(inf)) {
                    return true;
                }
            }
        }

        // 6. Verbes du 2e groupe (-ir) (ex: finir -> finis, finit, finissent, finissait)
        String[] irEndings = {
            "issaient", "issions", "issiez", "issent", "issant", "issais", "issait",
            "issons", "issez", "iront", "irons", "irez", "iras", "ira",
            "irait", "irais", "ies", "ie", "is", "it", "i"
        };
        for (String ending : irEndings) {
            if (lower.endsWith(ending) && lower.length() > ending.length() + 1) {
                String root = lower.substring(0, lower.length() - ending.length());
                String inf = root + "ir";
                if (frenchDictionary.contains(inf)) {
                    return true;
                }
            }
        }

        // 7. Adverbes en -ment / -ement (ex: vraiment, rapidement, exactement, simplement)
        if (lower.endsWith("ment") && lower.length() > 6) {
            String stem = lower.substring(0, lower.length() - 4);
            if (frenchDictionary.contains(stem) || frenchDictionary.contains(stem + "e")) {
                return true;
            }
            if (lower.endsWith("ement")) {
                String stemE = lower.substring(0, lower.length() - 5);
                if (frenchDictionary.contains(stemE)) return true;
            }
        }

        // 8. Mots composés avec traits d'union (ex: peut-être, c'est-à-dire, au-dessus, va-t-il)
        if (lower.contains("-")) {
            String[] parts = lower.split("-");
            boolean allPartsValid = true;
            for (String p : parts) {
                if (p.isEmpty() || p.equals("t") || p.equals("il") || p.equals("elle") || p.equals("on") || p.equals("ils") || p.equals("elles") || p.equals("ce")) {
                    continue;
                }
                if (!isFrenchWordValid(p)) {
                    allPartsValid = false;
                    break;
                }
            }
            if (allPartsValid) return true;
        }

        // 9. Préfixes fréquents (re-, dé-, in-, im-, mal-, sur-, sous-, auto-, anti-)
        String[] prefixes = {"re", "ré", "dé", "dés", "in", "im", "mal", "sur", "sous", "auto", "anti"};
        for (String pfx : prefixes) {
            if (lower.startsWith(pfx) && lower.length() > pfx.length() + 3) {
                String rest = lower.substring(pfx.length());
                if (frenchDictionary.contains(rest) || isFrenchWordValid(rest)) {
                    return true;
                }
            }
        }

        return false;
    }

    public boolean isEnglishWordValid(String lower) {
        if (lower == null || lower.isEmpty()) return true;
        lower = java.text.Normalizer.normalize(lower, java.text.Normalizer.Form.NFC);
        if (englishDictionary.contains(lower)) return true;

        if (lower.endsWith("s")) {
            if (englishDictionary.contains(lower.substring(0, lower.length() - 1))) return true;
        }
        if (lower.endsWith("es")) {
            if (englishDictionary.contains(lower.substring(0, lower.length() - 2))) return true;
        }
        if (lower.endsWith("ed")) {
            if (englishDictionary.contains(lower.substring(0, lower.length() - 2)) ||
                englishDictionary.contains(lower.substring(0, lower.length() - 1))) return true;
        }
        if (lower.endsWith("ing") && lower.length() > 5) {
            String stem = lower.substring(0, lower.length() - 3);
            if (englishDictionary.contains(stem) || englishDictionary.contains(stem + "e")) return true;
        }
        if (lower.endsWith("ly") && lower.length() > 4) {
            if (englishDictionary.contains(lower.substring(0, lower.length() - 2))) return true;
        }
        return false;
    }
}
