# Lecteur Webradio en Direct (Live Stream Player)

Une application Android moderne en Jetpack Compose permettant d'écouter des webradios en direct avec persistance locale Room pour gérer les stations, lecture continue en arrière-plan via un service de premier plan, mécanisme de reconnexion automatique intelligent jusqu'à 5 minutes, et déclencheur d'actualisation de morceau via URL personnalisée.

## Décisions Clés & Synthèse des Réponses Utilisateur

> [!IMPORTANT]
> Les choix suivants ont été confirmés lors de la phase de cadrage :
> - **Stations initiales** : Démarrage avec une liste vide prête à accueillir les stations créées par l'utilisateur (avec bouton facile pour ajouter sa première station).
> - **Bouton Suivant / Passer** : Envoie une requête HTTP à l'URL configurée de la station pour ordonner le changement de morceau côté serveur, puis réinitialise/actualise le flux en direct pour réduire le temps de latence (décalage).
> - **Style visuel** : Design Material You épuré avec thématique dynamique, formes douces M3, indicateur visuel de direct (Live) et ondes audio animées.

---

## 1. Vue d'Ensemble & Fonctionnalités Clés

- **Gestion des stations (Room DB locale)** :
  - Nom de la station (obligatoire)
  - URL du flux audio (obligatoire, compatible MP3, AAC, OGG, HLS)
  - URL de changement de musique (optionnelle/recommandée pour le déclenchement du morceau suivant)
  - Station active sauvegardée
- **Écran d'accueil & Lecteur central** :
  - En haut à gauche : Sélecteur de station compact avec menu déroulant / panneau rapide pour changer instantanément de radio.
  - En haut à droite : Accès direct aux Paramètres / Gestion de la base de données des stations.
  - Au centre : Lecteur audio immersif affichant le visualiseur d'ondes sonores en direct, le nom de la radio, le titre de la musique détecté ou en cours de diffusion, les contrôles de lecture (Play/Pause, Recharger le flux) et le bouton **Suivant** (Passer).
  - Indicateur de statut précis : "En direct", "Mise en mémoire tampon...", "Changement de morceau...", ou "Reconnexion automatique (max 5 min)".
- **Lecture audio en arrière-plan ininterrompue** :
  - Implémentée avec un `ForegroundService` Android et notification multimédia interactive (Play, Pause, Suivant, Quitter).
  - Gestion du focus audio et maintien du verrou de veille (`WAKE_LOCK`).
- **Résilience & Reconnexion automatique (5 minutes)** :
  - Si le flux radio s'interrompt (perte de connexion, coupure du serveur de streaming), le lecteur diffuse le silence sans planter et déclenche un cycle de reconnexion périodique avec compte à rebours jusqu'à 5 minutes avant d'abandonner.
- **Support multi-formats** :
  - Support natif des flux HTTP/HTTPS en streaming (MPEG Audio / MP3, AAC / M4A, OGG/Opus, HLS stream).

---

## 2. Expérience Utilisateur & Design Visuel

### Parcours Utilisateur
1. **Premier lancement** : L'écran d'accueil invite chaleureusement l'utilisateur à configurer sa première webradio en cliquant sur "Ajouter une station" ou via l'icône paramètres.
2. **Configuration en base locale** : Formulaire M3 épuré avec validation (Nom, URL de streaming, URL de changement de morceau). Possibilité de tester l'URL directement.
3. **Écoute en direct** : Sélection de la station, lancement instantané du flux. Visualiseur dynamique pulsant au rythme de la lecture.
4. **Passer au morceau suivant** : Pression sur le bouton "Suivant", requête HTTP envoyée à l'URL de changement de morceau configurée, notification visuelle, puis rafraîchissement automatique du flux audio.
5. **Passage en arrière-plan** : L'utilisateur verrouille l'écran ou quitte l'application ; la musique continue sans accroc et la notification permet de contrôler le lecteur.

### Direction Artistique (Material You épuré)
- **Palette & Couleurs** : Utilisation du Material You dynamique (`dynamicLightColorScheme` / `dynamicDarkColorScheme` sur Android 12+, avec palette de secours saphir/indigo moderne).
- **Typographie** : Hiérarchie typographique nette (titres clairs, affichage du morceau en évidence avec défilement fluide si texte long).
- **Animations** : Barres d'égaliseur animées en direct (`Canvas` / `Animatable`), transitions fondues entre états Play et Pause, compte à rebours de reconnexion avec jauge circulaire.

---

## 3. Architecture Technique & Flux de Données

```
┌─────────────────────────────────────────────────────────────┐
│                    Jetpack Compose UI                       │
│  ┌───────────────────────┐       ┌───────────────────────┐  │
│  │     HomeScreen        │       │    SettingsScreen     │  │
│  │ (Station Selector,    │       │ (Room DB Management,  │  │
│  │  Live Player, Waves)  │       │  Add/Edit/Delete)     │  │
│  └───────────┬───────────┘       └───────────┬───────────┘  │
│              │                               │              │
│              └───────────────┬───────────────┘              │
│                              ▼                              │
│                      RadioViewModel                         │
└──────────────────────────────┬──────────────────────────────┘
                               │
            ┌──────────────────┴──────────────────┐
            ▼                                     ▼
┌─────────────────────────┐           ┌─────────────────────────┐
│     Room Database       │           │   RadioPlaybackService  │
│  - StationEntity        │           │   (Foreground Service)  │
│  - StationDao           │           │  - MediaPlayer Core     │
│  - RadioRepository      │           │  - 5-Min Retry Watchdog │
│                         │           │  - Media Notification   │
└─────────────────────────┘           └───────────┬─────────────┘
                                                  │
                                                  ▼
                                      ┌─────────────────────────┐
                                      │ Web Radio HTTP Streams  │
                                      │ Next-Song Trigger URL   │
                                      └─────────────────────────┘
```

### Composants Principaux
1. **Persistance locale (Room)** :
   - `RadioStation` : `id`, `name`, `streamUrl`, `changeSongUrl`, `isSelected`, `createdAt`.
   - `RadioStationDao` : Récupération réactive en `Flow<List<RadioStation>>`, insertions, suppressions, mise à jour de la station sélectionnée.
2. **Moteur Audio & Service (`RadioPlaybackService`)** :
   - Service d'arrière-plan avec `startForeground` et notification multimédia.
   - Gestion d'état réactive exposée via `StateFlow` ou `Broadcast`/`Binder` : `PlaybackState` (Idle, Buffering, Playing, Reconnecting, Error).
   - Watchdog de reconnexion : réessais progressifs avec timer de 5 minutes max.
   - Requête asynchrone sur l'URL de changement de musique (`OkHttpClient`) lors de l'appui sur "Suivant", puis reconnexion propre du lecteur.
3. **Interface Compose** :
   - `MainActivity` avec Navigation M3 entre le lecteur d'accueil et les paramètres.
   - Dialogue rapide de changement de station depuis le bouton supérieur gauche.
   - Visualiseur d'ondes sonores interactif.

---

## 4. Plan de Réalisation Étape par Étape

1. **Manifest & Permissions** :
   - Déclarer `INTERNET`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `WAKE_LOCK`, `POST_NOTIFICATIONS`.
2. **Entités Room & Repository** :
   - Créer `RadioStation`, `RadioStationDao`, `RadioDatabase` et `RadioRepository`.
3. **Moteur Audio & Service de lecture en arrière-plan** :
   - Créer `RadioPlaybackService` avec notification persistante, gestion du cycle de vie audio, boucle de reconnexion automatique (5 min) et gestionnaire de requête du bouton Suivant.
4. **ViewModel & Couche de données** :
   - `RadioViewModel` coordonnant l'état de lecture, la liste des stations, la station active et la communication avec le service.
5. **Composants d'Interface Jetpack Compose** :
   - Écran principal avec sélecteur de station en haut à gauche, visualiseur d'ondes, lecteur central avec nom du morceau et bouton Suivant.
   - Écran de paramètres / gestion des stations dans la base de données.
6. **Synchronisation du projet & Validation** :
   - Mettre à jour `metadata.json` et `strings.xml`.
   - Vérifier la compilation sans erreur avec `compile_applet`.
