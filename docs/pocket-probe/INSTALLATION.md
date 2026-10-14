# Installer JARVIS Pocket Probe depuis un téléphone

Ce prototype s'installe **à côté de JARVIS VOICE** : nom `JARVIS Pocket Probe`, identifiant `com.jarvis.voice.pocketprobe`. Il ne remplace ni JARVIS VOICE ni JARVIS CORE. Il lit des phrases françaises avec une voix prédéfinie ; il ne clone pas un MP3.

1. Télécharger l'APK `JARVIS-Pocket-Probe-0.1.0.apk` fourni dans la livraison. Ouvrir le fichier depuis Téléchargements et autoriser cette installation pour le navigateur/gestionnaire de fichiers si Android le demande.
2. Télécharger `JARVIS-Pocket-Probe-fr-6l-alba.zip`. **Ne pas extraire ce ZIP de modèle** : l'application doit l'importer directement.
3. Ouvrir **JARVIS Pocket Probe**, toucher **Importer le pack français (.zip)** et choisir le ZIP dans Téléchargements. Attendre « Pack français vérifié ». Un pack différent ou corrompu sera refusé, sans remplacer un pack valide.
4. Garder **Pocket TTS français — CPU** et le **mode économique** cochés. Toucher **Lecture**, puis effectuer les essais de [TEST-PHONE.md](TEST-PHONE.md).

Le téléchargement initial utilise Internet. Une fois le pack copié dans les données privées de l'application, Pocket fonctionne sans permission INTERNET. L'installation du modèle ne demande aucun compte ni jeton. L'APK pèse environ 29 Mo et le ZIP de modèle environ 100 Mo ; conserver environ 1 Go libre pour téléchargements, installation et import. La consommation de RAM réelle apparaît dans l'écran de diagnostic.

Si les fichiers sont récupérés via GitHub Actions : ouvrir le workflow **Pocket Probe**, puis une exécution réussie sur `feat/android-pocket-probe`. Télécharger les artefacts `jarvis-pocket-probe-apk` et `pocket-fr-6l-alba-model`. GitHub les fournit dans des ZIP enveloppes : extraire ces enveloppes avec le gestionnaire de fichiers Android pour obtenir l'APK et le ZIP de modèle intérieur. **Conserver ce dernier ZIP intact.** Connexion GitHub nécessaire pour récupérer les artefacts ; rétention 14 jours. Un lancement manuel du workflow recrée les fichiers depuis les versions fixées, sans PC.

Les fichiers livrés directement dans le chat évitent ces ZIP enveloppes. Les compilations cloud et CI utilisent des certificats debug différents : pour changer d'origine de compilation, Android peut demander de désinstaller **uniquement Pocket Probe**, puis de réimporter le pack. Ne pas désinstaller JARVIS VOICE.

Android peut afficher un avertissement Play Protect pour cet APK de diagnostic inconnu. Si le message propose « Installer quand même », vérifier qu'il s'agit bien de l'APK de cette livraison avant de choisir cette option. Il n'est pas nécessaire de désactiver Play Protect globalement. Une détection différente ou l'absence de cette option nécessite un diagnostic du message précis.

Le secours Android est **manuel et visible** : choisir « Android TTS — secours explicite ». Il nécessite un moteur et une voix française hors ligne déjà installés sur le téléphone. Une erreur Pocket n'active jamais silencieusement ce secours.

Limites : Android ARM64, API 26 minimum, cible Android 15/API 35 ; 300 caractères et 50 jetons maximum, une demande à la fois, audio limité à 20 secondes par essai. Le chargement initial peut prendre plusieurs secondes. Les contrôles 16 Ko sont statiques ; l'exécution sur votre téléphone reste à valider. Un retour en arrière-plan annule la lecture et libère le modèle.
