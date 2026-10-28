# Refonte Ocarina : Robustesse Réseau, Artwork Dynamique & Évolutions UI/UX

Ce plan détaille la mise en œuvre complète des spécifications d'Ocarina : renforcement du service de lecture audio en arrière-plan, alerte critique après 5 minutes de reconnexion, enrichissement en direct des pochettes d'albums (iTunes/Deezer), gestion du skip et refonte visuelle ergonomique des écrans et modales.

> [!IMPORTANT]
> ### Décisions Utilisateur Validées (Phase 1)
> 1. **Alerte d'échec de reconnexion (5 min)** : Notification système persistante avec bouton d'action explicite « Réessayer » couplée à une bannière d'alerte visible dans l'application.
> 2. **Option de contrôle du flux au skip** : Activée par défaut (« Reconnexion active du flux audio au saut de piste »), configurable via un nouveau paramètre dédié dans l'écran Paramètres.
> 3. **Résolution des pochettes d'albums (Artwork)** : Récupération automatique en haute définition (600x600 px) pour un affichage net dans le lecteur Compose et la notification système Android MediaStyle.
> 4. **Identité Visuelle & Logo** : Rebranding total en « Ocarina », intégration du logo officiel fourni (onde audio sage/olive) en icône adaptative de l'application et en visuel central.

---

## 1. Vue d'Ensemble & Concept

Ocarina est un lecteur de webradio minimaliste, robuste et moderne pour Android. L'application garantit une continuité de lecture irréprochable face aux aléas réseau et enrichit l'expérience musicale par l'extraction directe des métadonnées de diffusion et la découverte visuelle des albums associés en temps réel.

- **Public cible** : Amateurs de musique et auditeurs de stations webradio indépendantes recherchant un lecteur épuré, réactif, sans publicité ni distractions.
- **Valeur clé** : Tolérance maximale aux coupures réseau, affichage automatique des titres et pochettes d'albums de haute qualité, et compatibilité totale avec les contrôles système (Android MediaSession, écran de verrouillage, Bluetooth, DLNA).

---

## 2. Expérience Utilisateur & Design Visuel

### Parcours Utilisateur Clés
1. **Écoute & Découverte Visuelle** : Au lancement d'un flux, le titre du morceau est extrait en temps réel du flux Icecast/Shoutcast avec décodage multi-encodage (UTF-8, Latin-1, Windows-1252). Une recherche instantanée interroge l'API iTunes (fallback Deezer) pour charger la pochette en 600x600.
2. **Saut de morceau (Skip)** : L'auditeur appuie sur le bouton harmonisé « Passer » (ou depuis la notification système). La requête de saut est transmise au serveur de la radio, et selon la préférence utilisateur, le flux audio est réinstancié pour supprimer la latence de tampon.
3. **Résilience Réseau & Notification Critique** : En cas de perte de flux, le compte à rebours de reconnexion s'active pendant 5 minutes. En cas d'échec final, l'utilisateur reçoit une alerte système avec bouton « Réessayer » et un bandeau clair dans l'app.
4. **Diffusion Audio (DLNA / UPnP)** : L'icône de diffusion abandonne l'analogie de la télévision pour un pictogramme moderne d'ondes de partage audio, avec une modale fluide présentant un bouton d'actualisation interactif bien détaché.

### Direction Artistique & Thème
- **Aura & Atmosphère** : Tonalité naturelle inspirée du logo officiel (nuances sauge, olive, surfaces neutres chaudes et fond sombre immersif).
- **Typographie** : Hiérarchie contrastée dans la carte de lecture (« Morceau diffusé » discret en surtitre, titre de chanson en graisse `Bold` et corps 18–20sp).
- **Régularité & Équilibre** : Alignement strict des boutons d'action inférieurs (bouton rond de rechargement à gauche et bouton d'action à droite équilibrés autour du bouton Play central).
- **Marges Système & Insets** : Respect des marges supérieures de la barre d'état système (`WindowInsets.statusBars` et padding de sécurité) et élévation du bouton flottant (+) au-dessus de la barre de navigation gestuelle.

---

## 3. Décisions Clés & Arbitrages Techniques

- **Pipeline d'Artwork sans clé API** :
  - *Approche retenue* : Appel asynchrone non-bloquant vers iTunes Search API (`https://itunes.apple.com/search?term=...&entity=song&limit=1`), avec extraction du champ `artworkUrl100` surdimensionné en 600x600 (`600x600bb.jpg`), et bascule immédiate vers Deezer API (`https://api.deezer.com/search?q=...&limit=1`) si aucun résultat n'est retourné.
  - *Gestion du cache* : Mise en cache mémoire et disque via Coil pour éviter toute requête répétitive sur une même chanson.
  - *Placeholder* : Composant visuel vectoriel arborant le logo officiel Ocarina sur fond dégradé doux lorsque l'API ne retourne aucun visuel.
- **Nettoyage & Encodage des Métadonnées** :
  - *Approche retenue* : Décodage binaire intelligent dans `IcyMetadataExtractor` testant séquentiellement UTF-8, Windows-1252 et ISO-8859-1 avec nettoyage des entités HTML (`&amp;`, `&#039;`, `&quot;`) et normalisation des séparateurs (`Artist - Title`).
- **MediaStyle Notification Android** :
  - *Approche retenue* : Titre du morceau affiché en titre principal de la notification (station en sous-titre), affichage de l'artwork en grand icône (`setLargeIcon`), et intégration directe des actions Play/Pause et Next.

---

## 4. Architecture Technique & Flux de Données

```
┌────────────────────────────────────────────────────────────────────────┐
│                              UI / Compose                              │
│   ┌─────────────────────────────┐    ┌─────────────────────────────┐   │
│   │         HomeScreen          │    │       SettingsScreen        │   │
│   │  • Pillule Ocarina ajustée  │    │  • Card Base de données     │   │
│   │  • Pochette 600x600 + Logo  │    │  • Option Skip Reconnect    │   │
│   │  • Titre contrasté          │    │  • Mentions 2026 en footer  │   │
│   │  • Boutons alignés          │    │  • FAB repositionné         │   │
│   └──────────────┬──────────────┘    └──────────────┬──────────────┘   │
└──────────────────┼──────────────────────────────────┼──────────────────┘
                   ▼                                  ▼
┌────────────────────────────────────────────────────────────────────────┐
│                             RadioViewModel                             │
│       • PlayerState (Status, Titre, ArtworkUrl, Erreurs, Skip)         │
│       • AppPreferences (SkipReloadEnabled, Theme, Language)            │
└──────────────────┬──────────────────────────────────┬──────────────────┘
                   │                                  │
                   ▼                                  ▼
┌──────────────────────────────────────┐  ┌──────────────────────────────┐
│        RadioPlaybackService          │  │     ArtworkRepository        │
│   • ExoPlayer + MediaSession         │  │   • iTunes Search API        │
│   • Watchdog 5 min + Notification    │  │   • Deezer API (Fallback)    │
│   • Notification MediaStyle + Art    │  │   • Coil ImageLoader Cache   │
└──────────────────┬───────────────────┘  └──────────────────────────────┘
                   │
                   ▼
┌──────────────────────────────────────┐
│        IcyMetadataExtractor          │
│   • Multi-charset (UTF-8, CP1252)    │
│   • HTML Entities Sanitizer          │
└──────────────────────────────────────┘
```

### Plan d'Exécution Détaillé
1. **Rebranding & Ressources Fondamentales** :
   - Mise à jour de `app_name` dans `strings.xml`, `metadata.json`, `settings.gradle.kts`.
   - Création de l'icône adaptative Ocarina à partir du fichier visuel officiel.
   - Ajout des chaînes de caractères (FR/EN) pour l'option de skip, l'alerte de coupure 5 minutes et le copyright 2026.
2. **Couche Données & Préférences** :
   - Ajout du paramètre `skipStreamReload` dans `AppPreferences` (valeur par défaut : true).
   - Création du service d'enrichissement d'artwork `ArtworkRepository` (iTunes + Deezer fallback) avec cache mémoire.
3. **Moteur Audio & Robustesse (MediaSessionService)** :
   - Amélioration de `IcyMetadataExtractor` pour la gestion des caractères accentués et encodages exotiques.
   - Intégration du déclenchement d'artwork dès la réception du titre.
   - Mise à jour de la notification Android MediaStyle : affichage prioritaire du morceau, grand icône d'artwork, bouton Passer.
   - Ajout de l'alerte d'échec critique après 5 minutes de reconnexion infructueuse avec bouton « Réessayer ».
   - Prise en compte du paramètre `skipStreamReload` dans `triggerNextSong()`.
4. **Refonte Écran Principal (HomeScreen)** :
   - Marge de sécurité haute pour la barre d'état.
   - Allègement esthétique de la pilule Ocarina.
   - Affichage de la pochette d'album dynamique avec fondu et fallback sur le logo Ocarina.
   - Renforcement du contraste visuel du bloc « Morceau diffusé ».
   - Harmonisation symétrique des commandes de lecture (bouton Actualiser circulaire à gauche, bouton Passer assorti à droite).
5. **Ajustements Écran des Paramètres (SettingsScreen)** :
   - Espacement sous « Lecteur système & Matériel connecté ».
   - Intégration du titre de la section stations dans une carte arrondie cohérente.
   - Ajout du toggle pour le rechargement du flux au skip.
   - Mention « Ocarina © 2026 » en bas de page.
   - Décalage vers le haut du bouton flottant (+).
6. **Composants Modaux (Bottom Sheets & Dialogs)** :
   - *Choisir une station* : URLs enveloppées (pas de coupure abrupte), meilleur contraste, bouton « + Gérer » espacé et stylisé.
   - *Diffuser (DLNA / UPnP)* : Pictogramme de streaming audio, correction de la troncature de l'anneau de chargement, vrai bouton cliquable « Actualiser la recherche », marges aérées.
   - *Ajouter une station* : Marges intérieures accrues, espacement du libellé sous « Suivant », ordonnancement clair des boutons (Fermer, Tester le flux en contour, Enregistrer en plein).
7. **Vérification & Compilation** :
   - Validation complète du build avec `compile_applet`.

---

## 8. Nouvelles Évolutions Validées

### A. Contrôle du Volume DLNA avec les Touches du Téléphone
- **Touches matérielles & HUD visuel** :
  - Interception des touches physiques de volume (`VOLUME_UP`, `VOLUME_DOWN`) lorsque la diffusion DLNA est active dans l'application.
  - Affichage instantané d'une capsule d'information flottante (HUD) en haut de l'écran avec nom de l'enceinte connectée, niveau sonore, icône dynamique et barre de progression.
  - Déclaration de `ForwardingPlayer` Media3 en mode `PLAYBACK_TYPE_REMOTE` permettant aux commandes du système Android (écran de verrouillage, contrôles système) de modifier le volume DLNA.
  - Synchronisation via `ContentObserver` du volume média Android vers le volume UPnP / DLNA.
  - Contrôle visuel direct avec curseur et boutons -/+ dans la feuille de diffusion DLNA.

### B. Transmission des Métadonnées du Flux au DLNA
- **Priorité au titre et artiste** :
  - Transmission du titre du morceau extrait du flux (`dc:title`, `upnp:artist`, `dc:creator`, `upnp:album`) dans le descripteur DIDL-Lite transmis à l'appareil DLNA (`SetAVTransportURI`).
  - Repli automatique vers le nom de la station uniquement si aucune métadonnée n'est présente sur le flux.
  - Mise à jour en continu à chaque nouveau morceau détecté.

### C. Import / Export des Données Utilisateur en JSON
- **Données sauvegardées** :
  - Durée globale d'écoute cumulée (`totalListeningSeconds`, `dureeEcouteGlobaleSecondes`).
  - Liste intégrale des stations radio avec nom, URL de flux et URL de skip (`skipUrl` / `changeSongUrl`).
- **Options d'exportation** :
  - Enregistrement de fichier `.json` via Storage Access Framework (`CreateDocument`).
  - Partage standard Android vers d'autres apps.
  - Copie dans le presse-papiers avec prévisualisation.
- **Options d'importation** :
  - Sélection d'un fichier `.json` via `OpenDocument`.
  - Collage direct de texte JSON avec validation en temps réel.
  - Boîte de dialogue de confirmation avec modes « Remplacer tout » ou « Fusionner ».

### D. Améliorations de l'Interface Utilisateur (UI)
- **Bulle d'Information de Débit** : Remplacement de « kbps » par « kb/s » dans la bulle d'information du lecteur.
- **Sélecteur de Station** : Remplacement du bouton textuel « Créer une station » par une simple icône « + » ergonomique dans l'en-tête de la feuille modale.
