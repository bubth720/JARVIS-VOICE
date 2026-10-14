# Prototype indépendant Pocket TTS français

Mission 004 : APK `com.jarvis.voice.pocketprobe`, Pocket TTS français 6 couches INT8 sur CPU, voix prédéfinie Alba. Aucun service, serveur, jeton, entraînement, sélection de MP3 ni clonage. Le moteur Android TTS est un choix manuel distinct.

Lire [l'installation](INSTALLATION.md), [le protocole téléphone](TEST-PHONE.md), [les résultats et limites](RESULTS.md) et [les licences](LICENSES.md).

`pocketRuntime` contient le frontend français, la validation du pack, la lecture de l'état public et la boucle ONNX de streaming ; il est exécuté directement sur JVM dans les tests. `pocketProbe` fournit l'interface, AudioTrack, la gestion du cycle de vie et les mesures Android. Le module `app` est inchangé.

Modèle fixé dans `scripts/pocket/source-lock.json` et pack dérivé limité à Alba dans `pocketRuntime/src/main/resources/model-lock.json`. Chaque fichier est validé par taille et SHA-256. L'import utilise un répertoire temporaire, n'accepte que les noms attendus et ne remplace un pack valide qu'après vérification complète. Les fichiers restent dans le stockage privé ; sauvegardes et transferts de données sont désactivés.

```bash
# Linux/cloud, JDK 17 ou 21, SDK Android 35 et Build Tools 35.0.0.
# Dans cet environnement : source /workspace/toolchains/jarvis-env.sh
python scripts/pocket/prepare_model.py \
  --work-dir /workspace/toolchains/pocket-probe-pack \
  --output /workspace/JARVIS-Pocket-Probe-fr-6l-alba.zip
POCKET_MODEL_DIR=/workspace/toolchains/pocket-probe-pack/fr \
  ./gradlew :pocketRuntime:test :pocketProbe:assembleDebug :pocketProbe:lintDebug \
  :app:testDebugUnitTest --no-daemon --max-workers=2
python scripts/pocket/verify_apk.py \
  pocketProbe/build/outputs/apk/debug/pocketProbe-debug.apk \
  --build-tools "$ANDROID_HOME/build-tools/35.0.0" \
  --report pocketProbe/build/apk-verification.json
```

Sans `POCKET_MODEL_DIR`, les tests nécessitant les vrais modèles sont explicitement **ignorés**, pas déclarés validés. La CI prépare le pack et fournit cette variable pour exécuter tous ces tests. `POCKET_SMOKE_DIR` est une option de diagnostic local pour conserver des WAV issus uniquement du corpus public, hors Git. La CI ne l'active pas et ne publie aucun audio.

Le runtime ne charge que les providers CPU, 1 ou 2 threads, un pas LSD et un maximum de 50 jetons / 250 frames. Les états vocaux incluent déjà le BOS : il n'est pas ajouté une seconde fois. Les caches natifs restent vivants entre les étapes ONNX pour éviter de recopier chaque frame en mémoire Java. La fin EOS est exigée ; une limite atteinte est une erreur, pas une réussite tronquée.

Arrêt coupe le lecteur et demande la terminaison via ONNX `RunOptions`. La création d'une session native pendant un premier chargement n'est pas interrompue au milieu ; l'annulation est vérifiée avant toute lecture. Les sessions sont libérées en arrière-plan et lors du changement de budget CPU. Le PSS mesure tout le processus, y compris l'éventuel client Android TTS initialisé.

Les contrats `ReferenceImporter`, `ReferenceDecoder` et `LocalProfileEncoder` préparent des traitements locaux et des profils liés au modèle. Aucune implémentation n'est active ; `CloningCapability.available=false`. Avant leur activation : vrai encodeur français autorisé, décodage MP3 local 24 kHz mono, contrôle/écoute de référence et tests de profil réutilisé hors ligne.

Les changements sont proposés sur `feat/android-pocket-probe`, à partir de `feat/android-tts-foundation`. Aucun commit n'est ajouté à la branche stable et aucune fusion dans main n'est effectuée.
