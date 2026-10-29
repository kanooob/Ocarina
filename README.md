# 🎵 Ocarina — Lecteur Webradio & Diffusion Audio Intelligente

<div align="center">
  <img src="app/src/main/res/drawable/ic_ocarina_logo.png" alt="Ocarina Logo" width="140" height="140" />
  <p><em>Lecteur webradio épuré, robuste et moderne pour Android avec diffusion connectée DLNA / UPnP.</em></p>
</div>

---

## 📖 À propos d'Ocarina

**Ocarina** est une application Android moderne conçue pour les passionnés de musique et d'émissions radio en ligne. L'application met l'accent sur la continuité d'écoute, l'absence totale de distractions ou de publicités, une tolérance maximale aux aléas réseau et une compatibilité approfondie avec l'écosystème Android (MediaSession, notification MediaStyle, Bluetooth, Android Auto, raccourcis lanceur et enceintes connectées DLNA).

---

## ✨ Fonctionnalités Principales

### 📻 Moteur Audio & Streaming Webradio
- **Compatibilité Universelle** : Prise en charge des flux audio Shoutcast, Icecast, AAC, MP3, OGG, ainsi que des playlists `.m3u` et `.pls` grâce au résolveur automatique `StreamUrlResolver`.
- **Découverte & Recherche Libre (RadioBrowser)** : Recherche intégrée parmi plus de 40 000 stations de radio mondiales libres et open-source via l'API RadioBrowser, avec sélection automatique du flux audio ayant la meilleure qualité disponible (débit le plus élevé). Écoute immédiate, diffusion DLNA ou ajout en 1 clic à votre liste avec pré-remplissage automatique du nom et de l'URL.
- **Extraction des Métadonnées en Direct** : Extraction en temps réel du titre et de l'artiste (`Icy-MetaData`) avec décodage multi-encodage (UTF-8, Windows-1252, ISO-8859-1) et nettoyage des entités HTML.
- **Pochettes d'Albums Dynamiques (Artwork)** : Recherche instantanée de pochettes en haute définition (600×600 px) via l'API iTunes avec bascule transparente vers Deezer en secours, et cache mémoire/disque optimisé avec Coil.
- **Saut de Morceau Intelligent (Skip)** : Envoi d'une requête de changement de chanson au serveur avec reconnexion active configurable du flux audio pour éliminer la latence du buffer.

### 📡 Diffusion Audio Sans Fil (DLNA / UPnP)
- **Détection Automatique sur le Réseau Wi-Fi** : Découverte SSDP native des récepteurs audio (enceintes Wi-Fi, téléviseurs connectés, amplificateurs de salon, box multimédia).
- **Envoi des Métadonnées Étendues (DIDL-Lite)** : Transmission dynamique du titre en cours, de l'artiste et de l'URL de la pochette de l'album directement à l'appareil de rendu connecté.
- **Contrôle Matériel du Volume** : Ajustement direct du volume de l'enceinte connectée à l'aide des boutons physiques de volume du smartphone (`VOLUME_UP` / `VOLUME_DOWN`) avec affichage d'un bandeau visuel interactif (HUD).

### ⏱️ Compteur de Temps d'Écoute en Temps Réel
- **Comptabilisation Active Universelle** : Suivi précis et persistant du temps d'écoute cumulé, fonctionnant aussi bien sur le haut-parleur du téléphone que lors de la diffusion sur un appareil connecté (DLNA).
- **Affichage Direct & Paramètres** : Visualisation du temps dans l'écran des paramètres et affichage instantané dans la bulle d'état du lecteur.
- **Chronomètre Système** : Notification Android MediaStyle et écran de verrouillage dotés d'un chronomètre temps réel synchronisé.

### 🛡️ Robustesse Réseau & Watchdog 5 Minutes
- **Monitoring Réseau Automatique** : Détection instantanée des pertes et retours de connexion Internet (`ConnectivityManager.NetworkCallback`).
- **Reconnexion Progressive** : Compte à rebours automatique jusqu'à 5 minutes lors des interruptions de flux.
- **Alerte Critique** : Notification système persistante et bandeau dans l'application avec bouton d'action explicite « Réessayer » en cas d'indisponibilité prolongée.

### 💾 Sauvegarde & Restauration (JSON)
- **Exportation Complète** : Exportez la liste de vos stations (nom, flux, URL de skip) ainsi que votre temps d'écoute cumulé dans un fichier JSON via le Storage Access Framework, ou partagez-le vers d'autres applications.
- **Importation Flexible** : Importez vos données depuis un fichier `.json` ou en collant directement le contenu texte, avec choix entre les modes **Fusion** (conserve l'existant) ou **Remplacement total**.

### 🎨 Design & Ergonomie (Material Design 3)
- **Harmonie Visuelle** : Palette raffinée inspirée de l'onde sonore Ocarina (teintes sauge et olive `#889058`, contrastes profonds et surfaces douces).
- **Animation d'Ondes Sonores** : Visualiseur animé `SoundWaveVisualizer` réactif à l'état de lecture et de buffering.
- **Widgets pour Écran d'Accueil** :
  - **Widget Rond (Circulaire)** : Cercle central cliquable affichant la pochette ou le logo faisant office de bouton Play/Pause pour relancer la dernière station écoutée, avec titre de la musique ou nom de station en dessous.
  - **Widget Rectangle Classique** : Lecteur complet avec extraction automatique de la couleur dominante de la pochette pour teinter dynamiquement le fond, carré d'artwork, métadonnées (titre, artiste, station) et contrôles de lecture (Play/Pause et Suivant).
- **Adaptabilité** : Interface fluide pour smartphones, tablettes et Android TV, respect strict des insets système (`WindowInsets`, `statusBarsPadding`).
- **Multilingue & Thèmes** : Français et Anglais intégrés, modes Clair, Sombre ou Automatique selon le système.
- **Raccourcis Dynamiques** : Épinglez vos stations favorites sur votre écran d'accueil Android via les raccourcis d'applications.

---

## 🏗️ Architecture du Projet

Le projet suit les principes de l'architecture moderne Android (MVVM / Architecture Components) :

```
app/src/main/java/com/example/
├── data/                       # Couche Données & Persistance
│   ├── AppPreferences.kt       # Préférences utilisateur (thème, langue, temps d'écoute)
│   ├── BackupData.kt           # Sérialisation / Désérialisation JSON
│   ├── RadioBrowserService.kt  # Client annuaire open-source RadioBrowser & sélection HD
│   ├── RadioDatabase.kt        # Base de données Room locale (SQLite)
│   ├── RadioStation.kt         # Entité Room des stations
│   └── RadioStationDao.kt      # Requêtes DAO pour les stations
├── dlna/                       # Couche Diffusion Sans Fil (UPnP / DLNA)
│   ├── DlnaDevice.kt           # Modèle de données pour les appareils UPnP
│   └── DlnaManager.kt          # Découverte SSDP, SOAP actions, AVTransport, volume
├── service/                    # Couche Système & Lecture Audio en Arrière-plan
│   ├── ArtworkRepository.kt    # Récupération des pochettes iTunes / Deezer
│   ├── IcyMetadataExtractor.kt # Analyse des en-têtes HTTP Icecast et détection de titres
│   ├── NetworkMonitor.kt       # Surveillance proactive de l'état réseau
│   ├── PlayerState.kt          # Modèle d'état immuable du lecteur
│   ├── RadioPlaybackService.kt # MediaSessionService Media3 & ExoPlayer
│   └── StreamUrlResolver.kt    # Résolution intelligente des flux et playlists
├── ui/                         # Interface Utilisateur Jetpack Compose
│   ├── Localization.kt         # Traductions FR/EN et formatage de durées
│   ├── RadioViewModel.kt       # ViewModel principal orchestrant l'application
│   ├── components/             # Composants réutilisables (SoundWave, Dialogs)
│   ├── screens/                # Écrans principaux (HomeScreen, SettingsScreen)
│   └── theme/                  # Thème Material 3 (Color, Theme, Type)
├── util/                       # Utilitaires
│   └── ShortcutHelper.kt       # Gestion des raccourcis Android dynamiques
└── widget/                     # Widgets Écran d'Accueil Android
    ├── CircleRadioWidgetProvider.kt    # Widget bouton rond
    ├── RectangleRadioWidgetProvider.kt # Widget rectangle adaptatif
    └── OcarinaWidgetHelper.kt          # Extraction couleur dominante, crop bitmap & synchronisation
```

---

## 🚀 Compilation & Exécution

### Prérequis
- **JDK 17** ou supérieur
- **Android SDK** (API 26 minimum, API 35 cible)
- **Gradle** 8.x

### Commandes Utiles

Pour compiler l'application en mode Debug :
```bash
gradle assembleDebug
```

Pour exécuter les tests unitaires et Robolectric :
```bash
gradle :app:testDebugUnitTest
```

L'APK compilé se trouve dans :
```
app/build/outputs/apk/debug/app-debug.apk
```

---

## 📄 Licence

Projet développé avec passion pour une expérience musicale libre et épurée.
Ocarina © 2026.
(Fait par IA)
