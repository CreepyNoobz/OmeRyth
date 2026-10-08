/**
 * OmeRyth Landing Page — Core Scripts
 * - Robust Dark / Light Theme Manager (defaults to dark, swaps logos)
 * - French / English Bilingual Internationalization (defaults to French)
 * - Showcase Window Tabs & Video Player Controller (smooth single-stream playback)
 * - Download Button Feedback Notification
 */

(function () {
  'use strict';

  // =========================================================================
  // 1. SAFE STORAGE HELPER (Prevents SecurityError on file:/// protocol)
  // =========================================================================
  function safeGet(key, fallback) {
    try {
      if (typeof window !== 'undefined' && window.localStorage) {
        return window.localStorage.getItem(key) || fallback;
      }
    } catch (e) {
      console.warn('localStorage not accessible, using fallback:', key);
    }
    return fallback;
  }

  function safeSet(key, value) {
    try {
      if (typeof window !== 'undefined' && window.localStorage) {
        window.localStorage.setItem(key, value);
      }
    } catch (e) {
      console.warn('localStorage not writable:', key);
    }
  }

  // =========================================================================
  // 2. DICTIONARY & I18N STRINGS (FR & EN)
  // =========================================================================
  const translations = {
    fr: {
      // Navbar
      nav_features: "Fonctionnalités",
      nav_showcase: "Démos Vidéo",
      nav_specs: "Spécifications",
      nav_faq: "FAQ",
      nav_contact: "Contact",
      nav_download: "Télécharger",

      // Hero
      hero_badge: "Version 1.2 — Export Vidéo Ultra-Rapide & Format 9:16",
      hero_title_p1: "La Bande Rythmo",
      hero_title_accent: "Nouvelle Génération",
      hero_title_p2: "pour Doublage & Post-Synchro",
      hero_subtitle: "Concevez, synchronisez et exportez vos bandes rythmo avec une fluidité absolue. Intègre l'isolation vocale par IA Demucs, l'alignement automatique WhisperX et le rendu streaming direct GPU.",
      hero_cta_download: "Télécharger pour Windows",
      hero_cta_demo: "Voir les démos en action",

      // Trust Bar Buttons
      trust_speed: "Rendu RAM Direct (NVENC/AMF/QSV)",
      trust_demucs: "Séparation Vocale HTDemucs",
      trust_mobile: "Format Vertical 9:16 Mobile",
      trust_detx: "Import/Export DETX Cappella",
      trust_waveform_btn: "Waveform & WhisperX IA",

      // Showcase
      showcase_tag: "Démonstrations En Direct",
      showcase_title: "Voyez OmeRyth en Pleine Action",
      showcase_subtitle: "Découvrez la puissance et la réactivité d'OmeRyth à travers ces aperçus interactifs en boucle.",
      tab_rythmo: "Bande Rythmo & Sync",
      tab_demucs: "Séparation IA Demucs",
      tab_mobile: "Format Mobile 9:16",
      tab_waveform: "Waveform & WhisperX",
      window_status_loop: "EN BOUCLE",

      // Showcase View 1: Rythmo
      rythmo_title: "Synchronisation Image & Bande Rythmo",
      rythmo_desc: "Défilement régulier et ultra-fluide synchronisé avec le moteur libvlc 64-bit. Les syllabes et signes labiaux (FVR, MPB, voyelles ouvertes) sont calibrés à la milliseconde près pour le confort optimal du comédien au micro.",
      rythmo_feat_1: "Scrubbing audio fluide sans saccade",
      rythmo_feat_2: "Affichage direct des signes de lipsync",
      rythmo_feat_3: "Gestion multi-rôles et couleurs d'acteurs",

      // Showcase View 2: Demucs
      demucs_title: "Suppression Vocale Chirurgicale IA",
      demucs_desc: "Isoler la bande sonore pour enregistrer un doublage n'a jamais été aussi simple. Le modèle HTDemucs extrait automatiquement les voix étrangères tout en préservant intacts l'ambiance sonore, les bruitages et la bande originale.",
      demucs_feat_1: "Zéro artefact sur les basses et percussions",
      demucs_feat_2: "Génération instantanée de bande témoin M&E",
      demucs_feat_3: "Gestionnaire autonome de modèles IA",
      stem_active_label: "IA HTDemucs : Dialogue Original Atténué (-∞ dB)",
      stem_bg: "Musique & Bruitages (Bande M&E)",
      stem_vox: "Voix Originale (Dialogue Supprimé)",
      stem_muted_txt: "MUET",

      // Showcase View 3: Mobile 9:16
      mobile_title: "Format Vertical 9:16 pour Réseaux Sociaux",
      mobile_desc: "Partagez vos doublages et extraits directement sur TikTok, Instagram Reels et YouTube Shorts. Un clic suffit pour formater la vidéo en 1080×1920 avec la bande rythmo judicieusement placée sous la scène.",
      mobile_feat_1: "Rendu optimisé 1080×1920 en direct",
      mobile_feat_2: "Cadrage intelligent 16:9 centré avec sous-titrage",
      mobile_feat_3: "Export GPU ultra-rapide en quelques secondes",

      // Showcase View 4: Waveform & WhisperX
      waveform_title: "Transcription WhisperX & Visualisation d'Onde",
      waveform_desc: "Accélérez la détection des répliques. La transcription automatique par IA positionne le texte directement sous les pics vocaux visualisés en temps réel via la forme d'onde audio haute résolution.",
      waveform_feat_1: "Affichage instantané de la waveform (Ctrl+W)",
      waveform_feat_2: "Alignement automatique phonème par phonème",
      waveform_feat_3: "Compatible projets bilingues français & anglais",

      // Features Section
      feat_tag: "Fonctionnalités Clés",
      feat_title: "Conçu Pour les Professionnels du Son",
      feat_subtitle: "Chaque détail d'OmeRyth est pensé pour maximiser la vitesse d'écriture, l'ergonomie et le confort d'enregistrement en studio.",
      f1_title: "Export Vidéo Streaming Direct",
      f1_desc: "Le moteur vidéo n'écrit aucun fichier temporaire sur disque. Les frames transitent en direct par pipe RAM vers FFmpeg avec accélération matérielle NVENC, AMF ou QSV. Un export de 8 minutes passe de ~30 min à quelques secondes !",
      f2_title: "Isolation Vocale IA Demucs",
      f2_desc: "Suppression propre des voix des acteurs d'origine tout en préservant intacts les effets sonores (SFX), la musique et l'ambiance grâce au modèle officiel HTDemucs de Facebook Research.",
      f3_title: "Format Mobile 9:16 en 1 Clic",
      f3_desc: "Basculez d'un clic en format 1080×1920 pour TikTok, Reels et YouTube Shorts avec cadrage intelligent de la vidéo 16:9 au centre et bande rythmo parfaitement calibrée en dessous.",
      f4_title: "Compatibilité DETX (Cappella)",
      f4_desc: "Importation et exportation sans faille des fichiers de doublage professionnels de cinéma/TV avec détection et conservation intégrale des signes de synchronisation labiale.",
      f5_title: "Association Windows & Fichiers .rythmo",
      f5_desc: "Double-cliquez directement sur vos projets .rythmo dans l'Explorateur Windows pour les ouvrir instantanément avec le logo officiel OmeRyth et sans latence via NativeDialog.",
      f6_title: "Lecteur Intégré libvlc 64-bit",
      f6_desc: "Compatibilité avec tous les codecs vidéo modernes (H.264, HEVC, ProRes, AV1) sans installation de packs de codecs tiers. Scrubbing précis à la milliseconde.",

      // Specs
      specs_tag: "Architecture & Spécifications",
      specs_title: "Une Base Technique Solide et Optimisée",
      specs_desc: "Développé pour répondre aux exigences des studios et créateurs indépendants : réactivité, indépendance vis-à-vis du cloud, et respect strict de votre matériel.",
      spec_os_lbl: "Système d'exploitation",
      spec_runtime_lbl: "Environnement d'exécution",
      spec_gpu_lbl: "Accélération Matérielle",
      spec_ai_lbl: "Modèles IA Intégrés",
      spec_formats_lbl: "Formats & Protocoles",
      specs_local_txt: "100% Traitement Local & Hors-Ligne (Aucune donnée envoyée dans le cloud)",
      specs_gpu_box_title: "Boost de Vitesse en Chiffres",
      speed_old: "Export classique disque",
      speed_new: "OmeRyth v1.2 Direct RAM",

      // FAQ
      faq_tag: "Questions Fréquentes",
      faq_title: "Tout ce que vous devez savoir",
      q1_title: "Qu'est-ce qu'une bande rythmo et à quoi sert OmeRyth ?",
      q1_desc: "Une bande rythmo est une bande texte défilante synchronisée à l'image utilisée dans les studios de doublage pour permettre au comédien de synchroniser précisément ses répliques et ses lèvres sur le mouvement des lèvres de l'acteur à l'écran. OmeRyth simplifie et accélère la création de ces bandes grâce à des assistants intelligents et des outils IA.",
      q2_title: "Comment fonctionne l'isolation vocale Demucs ?",
      q2_desc: "OmeRyth utilise le modèle de réseaux de neurones HTDemucs développé par Facebook Research. Il analyse les fréquences audio et sépare chirurgicalement la piste voix des autres instruments, bruits d'ambiance et effets sonores, le tout directement sur votre processeur ou votre carte graphique sans connexion internet requise.",
      q3_title: "Les fichiers créés avec OmeRyth sont-ils compatibles avec les studios ?",
      q3_desc: "Oui, OmeRyth supporte pleinement le standard professionnel DETX (Cappella) en import et export, vous permettant d'échanger vos projets avec les studios de doublage sans perte de synchronisation ou de métadonnées.",
      q4_title: "Puis-je exporter directement pour TikTok, YouTube Shorts ou Instagram ?",
      q4_desc: "Absolument ! Depuis la version 1.2, un préréglage 1080×1920 (9:16 vertical) permet de générer des clips prêts à publier avec la vidéo centrée et la bande rythmo défilante sous la scène.",

      // Bottom Banner
      banner_title: "Prêt à créer des bandes rythmo parfaites ?",
      banner_desc: "Téléchargez OmeRyth pour Windows et découvrez la nouvelle référence du doublage assisté par ordinateur.",
      banner_versions_title: "Versions disponibles de l'application",
      banner_btn: "Télécharger OmeRyth v1.2",
      banner_note: "Compatible Windows 10 & 11 • Version autonome portable disponible",

      // Footer
      footer_tagline: "Logiciel professionnel de synchronisation et création de bandes rythmo pour comédiens, adaptateurs et studios.",
      fcol_nav: "Navigation",
      fcol_tech: "Technologies",
      fcol_compat: "Compatibilité",
      footer_privacy: "Conçu pour les créateurs de doublage",

      // Contact
      contact_tag: "Contact & Support",
      contact_title: "Une question ? Contactez-nous",
      contact_desc: "Une question sur OmeRyth, les licences de doublage studio ou une suggestion ? Écrivez-nous directement.",
      contact_name_label: "Nom",
      contact_name_placeholder: "Votre nom complet",
      contact_email_label: "Adresse Email",
      contact_email_placeholder: "votre@email.com",
      contact_message_label: "Commentaire / Message",
      contact_message_placeholder: "Votre message, question ou retour...",
      contact_btn_send: "Envoyer le message",
      contact_btn_sending: "Envoi en cours...",
      contact_privacy_note: "Vos informations restent strictement confidentielles et ne sont jamais partagées.",
      contact_success: "Merci ! Votre message a bien été envoyé. Nous vous répondrons dans les plus brefs délais.",
      contact_error: "Une erreur est survenue lors de l'envoi. Veuillez réessayer ou vérifier vos informations.",

      // Toast
      toast_title: "Version 1.2 — Téléchargement lancé",
      toast_msg: "Le téléchargement d'OmeRyth_Setup_v1.2.exe a démarré !"
    },

    en: {
      // Navbar
      nav_features: "Features",
      nav_showcase: "Video Demos",
      nav_specs: "Specifications",
      nav_faq: "FAQ",
      nav_contact: "Contact",
      nav_download: "Download",

      // Hero
      hero_badge: "Version 1.2 — Ultra-Fast Video Export & 9:16 Mobile Format",
      hero_title_p1: "The Next-Generation",
      hero_title_accent: "Rhythmic Band",
      hero_title_p2: "for Dubbing & Post-Sync",
      hero_subtitle: "Create, synchronize, and export professional rhythmic bands with pinpoint accuracy. Powered by Demucs AI vocal stem isolation, WhisperX speech alignment, and direct GPU RAM streaming.",
      hero_cta_download: "Download for Windows",
      hero_cta_demo: "Watch live demos",

      // Trust Bar Buttons
      trust_speed: "Direct RAM Streaming (NVENC/AMF/QSV)",
      trust_demucs: "HTDemucs AI Vocal Isolation",
      trust_mobile: "9:16 Mobile Vertical Format",
      trust_detx: "DETX Cappella Import/Export",
      trust_waveform_btn: "Waveform & WhisperX AI",

      // Showcase
      showcase_tag: "Live Demonstrations",
      showcase_title: "See OmeRyth in Full Action",
      showcase_subtitle: "Experience the speed, ergonomics, and precision of OmeRyth through these looping interactive previews.",
      tab_rythmo: "Rythmo Band & Sync",
      tab_demucs: "Demucs AI Separation",
      tab_mobile: "9:16 Mobile Format",
      tab_waveform: "Waveform & WhisperX",
      window_status_loop: "LOOPING",

      // Showcase View 1: Rythmo
      rythmo_title: "Image & Rhythmic Band Synchronization",
      rythmo_desc: "Ultra-smooth scrolling synchronised with the 64-bit libvlc video engine. Syllables and lip-sync markers (FVR, MPB, open vowels) are calibrated down to the millisecond for optimal voice actor comfort.",
      rythmo_feat_1: "Smooth zero-stutter audio scrubbing",
      rythmo_feat_2: "Instant display of lip-sync markers",
      rythmo_feat_3: "Multi-role management & actor color coding",

      // Showcase View 2: Demucs
      demucs_title: "Surgical AI Vocal Removal",
      demucs_desc: "Isolating audio stems for dubbing sessions has never been faster. The HTDemucs neural network extracts dialogue while preserving environmental acoustics, Foley sound effects, and music.",
      demucs_feat_1: "Zero artifacts on basslines and drums",
      demucs_feat_2: "Instant M&E track generation",
      demucs_feat_3: "Built-in autonomous AI model manager",
      stem_active_label: "HTDemucs AI: Original Dialogue Muted (-∞ dB)",
      stem_bg: "Music & SFX (M&E Track)",
      stem_vox: "Original Voice (Dialogue Removed)",
      stem_muted_txt: "MUTED",

      // Showcase View 3: Mobile 9:16
      mobile_title: "9:16 Vertical Video for Social Media",
      mobile_desc: "Share your dubs and voice acting reels directly to TikTok, Instagram Reels, and YouTube Shorts. A single click formats your video to 1080×1920 with the rhythmic band positioned right below.",
      mobile_feat_1: "Direct 1080×1920 optimized rendering",
      mobile_feat_2: "Smart 16:9 centering with subtitled rythmo",
      mobile_feat_3: "Ultra-fast GPU export in under 60 seconds",

      // Showcase View 4: Waveform & WhisperX
      waveform_title: "WhisperX Transcription & Audio Waveform",
      waveform_desc: "Accelerate dialogue spotting. Automated speech recognition places text syllables directly beneath vocal peaks visualized in real-time through high-resolution waveform rendering.",
      waveform_feat_1: "Instant waveform display (Ctrl+W)",
      waveform_feat_2: "Automated phoneme-by-phoneme alignment",
      waveform_feat_3: "Full French and English bilingual support",

      // Features Section
      feat_tag: "Core Capabilities",
      feat_title: "Engineered For Sound Professionals",
      feat_subtitle: "Every feature in OmeRyth is built to maximize writing speed, studio ergonomics, and booth recording comfort.",
      f1_title: "Direct RAM Streaming Video Export",
      f1_desc: "The video engine bypasses slow disk I/O entirely. Frames are piped directly through memory to FFmpeg using hardware GPU encoders (NVENC, AMF, QSV). An 8-minute export drops from ~30 minutes to seconds!",
      f2_title: "Demucs AI Vocal Isolation",
      f2_desc: "Cleanly remove original actor voices while leaving music, ambiance, and sound effects intact using the state-of-the-art HTDemucs model from Facebook Research.",
      f3_title: "One-Click 9:16 Mobile Format",
      f3_desc: "Switch with one click to vertical 1080×1920 format tailored for TikTok, Reels, and YouTube Shorts with intelligent video scaling and perfectly readable scrolling text.",
      f4_title: "DETX (Cappella) Studio Compatibility",
      f4_desc: "Seamlessly import and export industry-standard cinema and TV dubbing files, preserving all lip-sync signs and production timings.",
      f5_title: "Windows .rythmo File Association",
      f5_desc: "Double-click any .rythmo project in Windows Explorer to open it immediately with the official OmeRyth icon and native latency-free Windows 10/11 file dialogs.",
      f6_title: "Built-in 64-bit libvlc Player",
      f6_desc: "Play any modern video format (H.264, HEVC, ProRes, AV1) without external codec packs. Scrub backwards and forwards with millisecond fidelity.",

      // Specs
      specs_tag: "Architecture & Specs",
      specs_title: "Robust, Hardware-Accelerated Engine",
      specs_desc: "Engineered to satisfy studios and indie creators: zero latency, cloud independence, and respectful utilization of your hardware.",
      spec_os_lbl: "Operating System",
      spec_runtime_lbl: "Runtime Environment",
      spec_gpu_lbl: "Hardware Acceleration",
      spec_ai_lbl: "Embedded AI Models",
      spec_formats_lbl: "Formats & Protocols",
      specs_local_txt: "100% Local & Offline Processing (No data is ever sent to the cloud)",
      specs_gpu_box_title: "Speed Comparison in Numbers",
      speed_old: "Traditional disk export",
      speed_new: "OmeRyth v1.2 RAM Streaming",

      // FAQ
      faq_tag: "Frequently Asked Questions",
      faq_title: "Everything you need to know",
      q1_title: "What is a rhythmic band and what does OmeRyth do?",
      q1_desc: "A rhythmic band (bande rythmo) is a scrolling text strip synchronized with video, used in dubbing studios so voice actors can match dialogue rhythm and mouth movements. OmeRyth accelerates this workflow with intuitive timeline tools and AI automation.",
      q2_title: "How does the Demucs AI vocal isolation work?",
      q2_desc: "OmeRyth integrates the HTDemucs deep neural network. It analyzes frequencies and isolates vocals from instruments and sound effects locally on your CPU or GPU without sending anything over the internet.",
      q3_title: "Are files created with OmeRyth compatible with studios?",
      q3_desc: "Yes! OmeRyth provides full import and export compatibility for Cappella DETX files, allowing you to collaborate seamlessly with professional post-production studios.",
      q4_title: "Can I export directly for TikTok, Instagram, or Shorts?",
      q4_desc: "Yes. In version 1.2, you can export directly in 9:16 vertical format (1080×1920) with centered video and the rhythmic band positioned for mobile viewing.",

      // Bottom Banner
      banner_title: "Ready to create flawless rhythmic bands?",
      banner_desc: "Download OmeRyth for Windows and experience the new standard in computer-assisted dubbing.",
      banner_versions_title: "Available Application Versions",
      banner_btn: "Download OmeRyth v1.2",
      banner_note: "Compatible with Windows 10 & 11 • Portable standalone version available",

      // Footer
      footer_tagline: "Professional rhythmic band software for voice actors, adaptors, and dubbing studios.",
      fcol_nav: "Navigation",
      fcol_tech: "Technologies",
      fcol_compat: "Compatibility",
      footer_privacy: "Built for dubbing creators and sound designers",

      // Contact
      contact_tag: "Contact & Support",
      contact_title: "Have a Question? Get in Touch",
      contact_desc: "Any questions about OmeRyth, studio dubbing licensing, or feedback? Drop us a line directly.",
      contact_name_label: "Name",
      contact_name_placeholder: "Your full name",
      contact_email_label: "Email Address",
      contact_email_placeholder: "your@email.com",
      contact_message_label: "Comment / Message",
      contact_message_placeholder: "Your message, question, or feedback...",
      contact_btn_send: "Send Message",
      contact_btn_sending: "Sending...",
      contact_privacy_note: "Your information remains strictly confidential and will never be shared.",
      contact_success: "Thank you! Your message has been sent successfully. We will get back to you shortly.",
      contact_error: "An error occurred while sending. Please try again or verify your input.",

      // Toast
      toast_title: "Version 1.2 — Download Started",
      toast_msg: "Downloading OmeRyth_Setup_v1.2.exe has started!"
    }
  };

  // =========================================================================
  // 3. CACHE ALL DOM ELEMENTS FIRST
  // =========================================================================
  const navLogo = document.getElementById('nav-logo');
  const footerLogo = document.getElementById('footer-logo');
  const themeToggleBtn = document.getElementById('theme-toggle');
  const langToggleBtn = document.getElementById('lang-toggle');
  const langDisplay = document.getElementById('lang-display');
  const toast = document.getElementById('toast');
  const toastCloseBtn = document.getElementById('toast-close-btn');

  const showcaseTabs = document.querySelectorAll('.showcase-tab');
  const showcaseViews = document.querySelectorAll('.showcase-view');
  const activeWindowTitleEl = document.getElementById('active-window-title');
  const switchTabButtons = document.querySelectorAll('[data-switch-tab]');
  const downloadButtons = document.querySelectorAll('.download-btn');

  // Mobile Drawer Elements
  const mobileLogo = document.querySelector('.mobile-logo');
  const mobileMenuToggle = document.getElementById('mobile-menu-toggle');
  const mobileNavDrawer = document.getElementById('mobile-nav-drawer');
  const mobileNavBackdrop = document.getElementById('mobile-nav-backdrop');
  const mobileNavCloseBtn = document.getElementById('mobile-nav-close-btn');
  const mobileNavLinks = document.querySelectorAll('.mobile-nav-link');

  // State
  const state = {
    theme: safeGet('omeryth_theme', 'dark'), // Standard is dark mode
    lang: safeGet('omeryth_lang', 'fr'),     // Standard is French
    activeTab: 'rythmo'
  };

  // =========================================================================
  // 4. WINDOW TITLE MANAGER
  // =========================================================================
  const titles = {
    rythmo: {
      fr: "OmeRyth v1.2 — Projet : Ryusei_Shidou_Dub.rythmo [Synchronisation]",
      en: "OmeRyth v1.2 — Project: Ryusei_Shidou_Dub.rythmo [Sync Engine]"
    },
    demucs: {
      fr: "OmeRyth v1.2 — Isolation Vocale IA HTDemucs [Bande M&E]",
      en: "OmeRyth v1.2 — HTDemucs AI Vocal Isolation [M&E Stems]"
    },
    mobile: {
      fr: "OmeRyth v1.2 — Atelier Export Vertical 1080×1920 (9:16)",
      en: "OmeRyth v1.2 — Vertical 1080×1920 (9:16) Export Studio"
    },
    waveform: {
      fr: "OmeRyth v1.2 — Forme d'Onde & WhisperX Auto-Spotting",
      en: "OmeRyth v1.2 — Audio Waveform & WhisperX Auto-Spotting"
    }
  };

  function updateWindowTitle() {
    if (!activeWindowTitleEl) return;
    const tab = state.activeTab || 'rythmo';
    const lang = state.lang || 'fr';
    if (titles[tab] && titles[tab][lang]) {
      activeWindowTitleEl.textContent = titles[tab][lang];
    }
  }

  // =========================================================================
  // 5. THEME CONTROLLER & LOGO SWITCHING
  // =========================================================================
  function applyTheme(theme) {
    state.theme = theme;
    document.documentElement.setAttribute('data-theme', theme);
    safeSet('omeryth_theme', theme);

    // Swap logos according to requirement:
    // - Dark mode: White 'O' + Green 'R' (logo-dark.png)
    // - Light mode: Black 'O' + Green 'R' (logo-light.png)
    const logoSrc = (theme === 'light') ? 'assets/images/logo-light.png' : 'assets/images/logo-dark.png';
    if (navLogo) navLogo.src = logoSrc;
    if (footerLogo) footerLogo.src = logoSrc;
    if (mobileLogo) mobileLogo.src = logoSrc;
  }

  function toggleTheme() {
    const nextTheme = (state.theme === 'dark') ? 'light' : 'dark';
    applyTheme(nextTheme);
  }

  if (themeToggleBtn) {
    themeToggleBtn.addEventListener('click', toggleTheme);
  }

  // =========================================================================
  // 6. LANGUAGE CONTROLLER
  // =========================================================================
  function applyLanguage(lang) {
    if (!translations[lang]) return;
    state.lang = lang;
    document.documentElement.setAttribute('lang', lang);
    safeSet('omeryth_lang', lang);

    if (langDisplay) {
      langDisplay.textContent = lang.toUpperCase();
    }

    // Update all elements with data-i18n
    const translatableElements = document.querySelectorAll('[data-i18n]');
    translatableElements.forEach(el => {
      const key = el.getAttribute('data-i18n');
      if (translations[lang][key]) {
        el.textContent = translations[lang][key];
      }
    });

    // Update input placeholders with data-i18n-placeholder
    const placeholderElements = document.querySelectorAll('[data-i18n-placeholder]');
    placeholderElements.forEach(el => {
      const key = el.getAttribute('data-i18n-placeholder');
      if (translations[lang][key]) {
        el.setAttribute('placeholder', translations[lang][key]);
      }
    });

    updateWindowTitle();
  }

  function toggleLanguage() {
    const nextLang = (state.lang === 'fr') ? 'en' : 'fr';
    applyLanguage(nextLang);
  }

  if (langToggleBtn) {
    langToggleBtn.addEventListener('click', toggleLanguage);
  }

  // =========================================================================
  // 7. SHOWCASE WINDOWS CONTROLLER (Single-Stream Video Playback)
  // =========================================================================
  function switchShowcaseTab(targetTab) {
    if (!targetTab) return;
    state.activeTab = targetTab;

    // 1. Update tab buttons active state
    showcaseTabs.forEach(tab => {
      const isTarget = tab.getAttribute('data-tab') === targetTab;
      tab.classList.toggle('active', isTarget);
      tab.setAttribute('aria-selected', isTarget ? 'true' : 'false');
    });

    // 2. Switch views & manage video playback
    showcaseViews.forEach(view => {
      const isTarget = (view.id === `view-${targetTab}`);
      view.classList.toggle('active', isTarget);

      const video = view.querySelector('video');
      if (video) {
        if (isTarget) {
          // Play only the active video from start
          video.currentTime = 0;
          const playPromise = video.play();
          if (playPromise !== undefined) {
            playPromise.catch(function (err) {
              console.log('Video autoplay restrained, waiting for user click:', err);
            });
          }
        } else {
          // Immediately pause inactive videos to free GPU decoders
          video.pause();
        }
      }
    });

    updateWindowTitle();
  }

  // Attach tab click events
  showcaseTabs.forEach(tab => {
    tab.addEventListener('click', function (e) {
      e.preventDefault();
      const targetTab = this.getAttribute('data-tab');
      switchShowcaseTab(targetTab);
    });
  });

  // Attach shortcut triggers (e.g. hero trust bar buttons)
  switchTabButtons.forEach(btn => {
    btn.addEventListener('click', function (e) {
      e.preventDefault();
      const targetTab = this.getAttribute('data-switch-tab');
      if (targetTab) {
        switchShowcaseTab(targetTab);
        const showcaseSection = document.getElementById('showcase');
        if (showcaseSection) {
          showcaseSection.scrollIntoView({ behavior: 'smooth', block: 'start' });
        }
      }
    });
  });

  // =========================================================================
  // 8. DOWNLOAD BUTTON ACTION & FEEDBACK TOAST
  // =========================================================================
  let toastTimeout = null;

  function showDownloadToast(e) {
    if (e) e.preventDefault();
    if (!toast) return;

    toast.classList.add('show');

    if (toastTimeout) {
      clearTimeout(toastTimeout);
    }

    toastTimeout = setTimeout(() => {
      toast.classList.remove('show');
    }, 4500);
  }

  downloadButtons.forEach(btn => {
    btn.addEventListener('click', function () {
      showDownloadToast();
    });
  });

  if (toastCloseBtn) {
    toastCloseBtn.addEventListener('click', () => {
      if (toast) toast.classList.remove('show');
    });
  }

  // =========================================================================
  // 9. SMOOTH SCROLL FOR IN-PAGE ANCHORS
  // =========================================================================
  document.querySelectorAll('a[href^="#"]').forEach(anchor => {
    anchor.addEventListener('click', function (e) {
      const targetId = this.getAttribute('href');
      if (!targetId || targetId === '#') return;
      const targetElement = document.querySelector(targetId);
      if (targetElement) {
        e.preventDefault();
        targetElement.scrollIntoView({
          behavior: 'smooth',
          block: 'start'
        });
      }
    });
  });

  // =========================================================================
  // 10. CONTACT FORM HANDLER (Formspree AJAX)
  // =========================================================================
  const contactForm = document.getElementById('contact-form');
  const contactStatus = document.getElementById('contact-status');
  const contactSubmitBtn = document.getElementById('contact-submit-btn');
  const contactBtnText = document.getElementById('contact-btn-text');

  if (contactForm) {
    contactForm.addEventListener('submit', async function (e) {
      e.preventDefault();

      if (!contactForm.checkValidity()) {
        contactForm.reportValidity();
        return;
      }

      const currentLang = state.lang || 'fr';
      const dict = translations[currentLang] || translations.fr;

      if (contactSubmitBtn) {
        contactSubmitBtn.disabled = true;
      }
      if (contactBtnText) {
        contactBtnText.textContent = dict.contact_btn_sending;
      }
      if (contactStatus) {
        contactStatus.style.display = 'none';
        contactStatus.className = 'form-status';
      }

      try {
        const formData = new FormData(contactForm);
        const response = await fetch(contactForm.action, {
          method: 'POST',
          body: formData,
          headers: {
            'Accept': 'application/json'
          }
        });

        if (response.ok) {
          contactForm.reset();
          if (contactStatus) {
            contactStatus.textContent = dict.contact_success;
            contactStatus.className = 'form-status form-status-success';
            contactStatus.style.display = 'flex';
          }
        } else {
          const data = await response.json().catch(() => ({}));
          let errorMsg = dict.contact_error;
          if (data && data.errors && data.errors.length > 0) {
            errorMsg = data.errors.map(err => err.message).join(', ');
          }
          if (contactStatus) {
            contactStatus.textContent = errorMsg;
            contactStatus.className = 'form-status form-status-error';
            contactStatus.style.display = 'flex';
          }
        }
      } catch (err) {
        if (contactStatus) {
          contactStatus.textContent = dict.contact_error;
          contactStatus.className = 'form-status form-status-error';
          contactStatus.style.display = 'flex';
        }
      } finally {
        if (contactSubmitBtn) {
          contactSubmitBtn.disabled = false;
        }
        if (contactBtnText) {
          contactBtnText.textContent = dict.contact_btn_send;
        }
      }
    });
  }

  // =========================================================================
  // 11. MOBILE MENU DRAWER CONTROLLER
  // =========================================================================
  function openMobileMenu() {
    if (!mobileNavDrawer) return;
    mobileNavDrawer.classList.add('open');
    mobileNavDrawer.setAttribute('aria-hidden', 'false');
    if (mobileMenuToggle) {
      mobileMenuToggle.classList.add('open');
      mobileMenuToggle.setAttribute('aria-expanded', 'true');
    }
    document.body.style.overflow = 'hidden';
  }

  function closeMobileMenu() {
    if (!mobileNavDrawer) return;
    mobileNavDrawer.classList.remove('open');
    mobileNavDrawer.setAttribute('aria-hidden', 'true');
    if (mobileMenuToggle) {
      mobileMenuToggle.classList.remove('open');
      mobileMenuToggle.setAttribute('aria-expanded', 'false');
    }
    document.body.style.overflow = '';
  }

  function toggleMobileMenu() {
    if (mobileNavDrawer && mobileNavDrawer.classList.contains('open')) {
      closeMobileMenu();
    } else {
      openMobileMenu();
    }
  }

  if (mobileMenuToggle) {
    mobileMenuToggle.addEventListener('click', toggleMobileMenu);
  }

  if (mobileNavCloseBtn) {
    mobileNavCloseBtn.addEventListener('click', closeMobileMenu);
  }

  if (mobileNavBackdrop) {
    mobileNavBackdrop.addEventListener('click', closeMobileMenu);
  }

  mobileNavLinks.forEach(link => {
    link.addEventListener('click', closeMobileMenu);
  });

  const mobileDrawerDownloadBtn = document.getElementById('mobile-drawer-download-btn');
  if (mobileDrawerDownloadBtn) {
    mobileDrawerDownloadBtn.addEventListener('click', function () {
      closeMobileMenu();
      showDownloadToast();
    });
  }

  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape' && mobileNavDrawer && mobileNavDrawer.classList.contains('open')) {
      closeMobileMenu();
    }
  });

  // =========================================================================
  // 12. INITIALIZATION
  // =========================================================================
  applyTheme(state.theme);
  applyLanguage(state.lang);

  // Trigger active video play on initial load
  const initialVideo = document.querySelector('.showcase-view.active video');
  if (initialVideo) {
    const p = initialVideo.play();
    if (p !== undefined) {
      p.catch(() => {});
    }
  }

})();
