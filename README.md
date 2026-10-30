# 🎵 Ocarina — Lecteur Webradio Libre & Diffusion Audio Intelligente

<div align="center">
  <img src="app/src/main/res/drawable/ic_ocarina_logo.png" alt="Ocarina Logo" width="140" height="140" />
  <p><em>Lecteur webradio épuré, robuste et 100% logiciel libre pour Android avec diffusion connectée DLNA / UPnP.</em></p>

  [![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
  [![F-Droid](https://img.shields.io/badge/F--Droid-Ready-brightgreen.svg)](#-f-droid--anti-features)
  [![Platform](https://img.shields.io/badge/Platform-Android%2024%2B-green.svg)](#)
  [![No Google Services](https://img.shields.io/badge/Google%20Play%20Services-None-success.svg)](#-vie-privée--zéro-traqueur)
</div>

---

## 📖 À propos d'Ocarina

**Ocarina** est une application Android moderne, épurée et entièrement libre sous licence **GNU General Public License v3.0 (GPLv3)**.

L'application met l'accent sur la continuité d'écoute, le respect absolu de la vie privée (aucun traqueur, aucune publicité, zéro SDK propriétaire), une tolérance maximale aux aléas réseau et une compatibilité approfondie avec l'écosystème Android libre (MediaSession, notifications MediaStyle, Bluetooth, raccourcis dynamiques et enceintes connectées DLNA / UPnP).

---

## 🛡️ F-Droid & Anti-Features

Ocarina est préparée pour être compilée et distribuée via **F-Droid**.

### Mention Anti-Feature : `NonFreeNet`
> **Pourquoi cette mention ?**  
> Ocarina permet d'écouter les directs de plateformes réseau propriétaires telles que **YouTube** et **Twitch** (résolution automatique des flux HLS audio). Conformément aux règles d'inclusion de F-Droid, toute application qui dépend ou interagit avec des services réseau non libres est accompagnée du flag `AntiFeatures: [NonFreeNet]`.

Le reste de l'application s'appuie exclusivement sur des technologies ouvertes et des protocoles standardisés (Icecast, Shoutcast, HLS direct, UPnP/DLNA, et l'annuaire communautaire Radio Browser).

---

## ✨ Fonctionnalités Principales

### 📻 Moteur Audio & Streaming Webradio
- **Compatibilité Universelle** : Prise en charge des flux audio Shoutcast, Icecast, AAC, MP3, OGG, ainsi que des playlists `.m3u` et `.pls` grâce au résolveur automatique `StreamUrlResolver`.
- **Découverte & Recherche Libre (Radio Browser)** : Recherche intégrée parmi plus de 40 000 stations de radio mondiales libres via l'API Radio Browser, avec sélection automatique du flux audio ayant la meilleure qualité disponible (débit le plus élevé).
- **Extraction des Métadonnées en Direct** : Extraction en temps réel du titre et de l'artiste (`Icy-MetaData`) avec décodage multi-encodage (UTF-8, Windows-1252, ISO-8859-1).
- **Pochettes d'Albums Dynamiques (Artwork)** : Recherche instantanée de pochettes en haute définition (600×600 px ou 300×300 px) avec cache mémoire et disque Coil.
- **Saut de Morceau Intelligent (Skip)** : Envoi d'une requête de changement de morceau au serveur avec reconnexion active configurable du flux audio pour éliminer la latence de tampon.

### 📡 Diffusion Audio Sans Fil (DLNA / UPnP)
- **Détection Automatique sur le Réseau Wi-Fi** : Découverte SSDP native des récepteurs audio (enceintes Wi-Fi, téléviseurs connectés, amplificateurs de salon, box multimédia).
- **Envoi des Métadonnées Étendues (DIDL-Lite)** : Transmission dynamique du titre en cours, de l'artiste et de l'URL de la pochette de l'album directement à l'appareil de rendu connecté.
- **Contrôle Matériel du Volume** : Ajustement direct du volume de l'enceinte connectée à l'aide des boutons physiques de volume du smartphone (`VOLUME_UP` / `VOLUME_DOWN`) avec bandeau visuel interactif (HUD).

### ⏱️ Compteur de Temps d'Écoute Persistant
- **Comptabilisation Active Universelle** : Suivi précis et persistant du temps d'écoute cumulé, fonctionnant sur le haut-parleur du téléphone ou lors de la diffusion DLNA.
- **Visualisation & Réinitialisation** : Visualisation claire dans l'écran des paramètres avec possibilité de réinitialiser le compteur à tout moment.

### 🛡️ Robustesse Réseau & Watchdog 5 Minutes
- **Monitoring Réseau Automatique** : Détection instantanée des pertes et retours de connexion Internet (`ConnectivityManager.NetworkCallback`).
- **Reconnexion Progressive** : Compte à rebours automatique jusqu'à 5 minutes lors des interruptions de flux.
- **Alerte Critique** : Notification système persistante et bandeau dans l'application avec bouton explicite « Réessayer ».

### 💾 Sauvegarde & Restauration (JSON)
- **Exportation Complète** : Exportez vos stations et votre temps d'écoute cumulé dans un fichier JSON via le Storage Access Framework, ou partagez-le vers d'autres applications.
- **Importation Flexible** : Importez vos données depuis un fichier `.json` ou en collant directement le contenu texte, avec choix entre les modes **Fusion** ou **Remplacement total**.

### 🎨 Design & Ergonomie (Material Design 3)
- **Harmonie Visuelle** : Palette raffinée inspirée de l'onde sonore Ocarina (teintes sauge et olive `#889058`, contrastes profonds et surfaces douces).
- **Animation d'Ondes Sonores** : Visualiseur animé `SoundWaveVisualizer` réactif à l'état de lecture et de buffering.
- **Widgets pour Écran d'Accueil** :
  - **Widget Rond (Circulaire)** : Bouton Play/Pause relançant la dernière station avec pochette d'album.
  - **Widget Rectangle Classique** : Lecteur complet avec extraction automatique de la couleur dominante de la pochette pour teinter dynamiquement le fond, carré d'artwork, métadonnées et contrôles.
- **Multilingue & Thèmes** : Français et Anglais intégrés, modes Clair, Sombre ou Suivre le système.

---

## 🔒 Vie Privée & Zéro Traqueur

Ocarina respecte intégralement la vie privée de ses utilisateurs :
- **0 traqueur** (pas de Google Analytics, pas de Crashlytics, pas de Firebase)
- **0 publicité**
- **0 bibliothèque Google Play Services requise** (fonctionne parfaitement sur GrapheneOS, LineageOS, CalyxOS, /e/OS ou tout appareil sans Google Play Services)
- **Stockage 100% local** via Room (SQLite chiffré/sécurisé dans le sandbox de l'application)

---

## 🏗️ Architecture du Projet

Le projet suit les principes de l'architecture moderne Android (MVVM / Architecture Components) :

```
app/src/main/java/com/example/
├── data/                       # Couche Données & Persistance Locale (Room)
│   ├── AppPreferences.kt       # Préférences utilisateur (thème, langue, temps d'écoute)
│   ├── BackupData.kt           # Sérialisation / Désérialisation JSON
│   ├── RadioBrowserService.kt  # Client annuaire libre Radio Browser
│   ├── RadioDatabase.kt        # Base de données locale Room (SQLite)
│   ├── RadioStation.kt         # Entité Room des stations
│   └── RadioStationDao.kt      # Requêtes DAO pour les stations
├── dlna/                       # Couche Diffusion Sans Fil (UPnP / DLNA)
│   ├── DlnaDevice.kt           # Modèle de données pour les appareils UPnP
│   └── DlnaManager.kt          # Découverte SSDP, SOAP actions, AVTransport, volume
├── service/                    # Couche Système & Lecture Audio en Arrière-plan
│   ├── ArtworkRepository.kt    # Récupération des pochettes libres
│   ├── IcyMetadataExtractor.kt # Analyse des en-têtes HTTP Icecast et détection de titres
│   ├── NetworkMonitor.kt       # Surveillance proactive de l'état réseau
│   ├── PlayerState.kt          # Modèle d'état immuable du lecteur
│   ├── RadioPlaybackService.kt # MediaSessionService Media3 & ExoPlayer
│   └── StreamUrlResolver.kt    # Résolution des flux directs, HLS, Twitch et YouTube
├── ui/                         # Interface Utilisateur Jetpack Compose (M3)
│   ├── Localization.kt         # Traductions FR/EN et chaînes de licence GPLv3
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

## 🚀 Compilation & Build F-Droid

### Prérequis
- **JDK 17** ou supérieur
- **Android SDK** (API 24 minimum, API 36 cible)
- **Gradle** 8.x

### Commandes de Compilation

Pour compiler l'APK en mode Debug :
```bash
gradle assembleDebug
```

Pour compiler l'APK de production :
```bash
gradle assembleRelease
```

Pour exécuter les tests unitaires locaux :
```bash
gradle :app:testDebugUnitTest
```

---

## 📄 Licence

Ce programme est un logiciel libre : vous pouvez le redistribuer et/ou le modifier selon les termes de la **GNU General Public License** telle que publiée par la Free Software Foundation, soit la **version 3** de la licence, soit (à votre convenance) toute version ultérieure.

Consultez le fichier [LICENSE](LICENSE) pour plus de détails.

Ocarina © 2026.
