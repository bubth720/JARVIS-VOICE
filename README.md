# JARVIS VOICE

Première application Android Kotlin : saisir un texte, appuyer sur **Écouter**, puis **Arrêter** pour interrompre la lecture. Android 8.0 (API 26) ou supérieur.

## Développement

Ouvrir le projet dans Android Studio, installer le SDK Android 35 et Build Tools 35.0.0. Utiliser JDK 17 ou 21 et le wrapper Gradle fourni.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Installer `app/build/outputs/apk/debug/app-debug.apk` sur un appareil avec un moteur de synthèse vocale Android. L'application utilise la langue de l'appareil ; une langue non prise en charge est signalée. Le bouton d'installation ouvre l'installateur du moteur disponible. Après l'installation d'une voix, relancer l'application.

## Architecture

- `MainActivity` : saisie, commandes, état accessible de la lecture et gestion du cycle de vie.
- `SpeechEngine` : contrat indépendant du fournisseur ; `SpeechRequest`, résultats et états explicites.
- `AndroidSpeechEngine` : initialisation asynchrone, langue, événements de lecture, interruption et libération du moteur Android.
- `SpeechRouter` : politiques `LOCAL`, `CLOUD`, `HYBRID`, connectivité injectable et repli local si le cloud refuse la requête avant sa prise en charge. Une erreur asynchrone après acceptation ne déclenche pas encore de repli.

Cette version utilise uniquement le moteur Android. Le mode `LOCAL` désigne le chemin Android, pas une garantie hors ligne : le moteur installé peut demander Internet. Pour un fonctionnement sans Internet, installer et sélectionner une voix locale dans les paramètres Android. La voix personnalisée et les modes cloud/hybride ne sont pas encore exposés dans l'interface.

La future voix cloud sera ajoutée via une implémentation de `SpeechEngine` et un service backend, sans clé fournisseur embarquée dans l'APK. Prévoir identifiant de voix, consentement pour l'enregistrement autorisé, création/suppression de voix, politique de conservation audio, annulation et erreurs asynchrones. Un modèle local personnalisé devra avoir sa propre implémentation et ses contraintes de téléchargement/stockage. Aucun enregistrement audio n'est collecté dans cette version.

JARVIS CORE reste indépendant. Une future bibliothèque ou API pourra exposer les contrats vocaux après définition du protocole d'intégration.

## Vérification sur appareil

1. Saisir « Bonjour, je suis JARVIS » ; écouter et vérifier la voix et les états.
2. Interrompre la lecture, puis lancer un autre texte.
3. Tester texte vide et texte dépassant la limite du moteur.
4. Quitter l'écran pendant la lecture : elle doit s'arrêter.
5. Tourner l'appareil : texte conservé, ancien moteur libéré.
6. Avec une voix locale installée, activer le mode avion et écouter.
7. Vérifier le message d'erreur avec une langue ou un moteur indisponible.

Les tests JVM couvrent le routage ; ils ne prouvent pas la lecture audio sur appareil.

## Mission 002 : Pocket TTS sur ordinateur

Le [prototype français CPU](prototype/README.md) est développé indépendamment de l'application Android. Il prépare le MP3 autorisé, l'export de profils privés et un serveur limité à l'ordinateur. Consultez son rapport pour distinguer les tests de code réussis de la génération française CPU mesurée et le clonage personnel restant à valider après configuration de l’accès aux poids et import du MP3 autorisé.
