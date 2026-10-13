# Mission 002 — prototype Pocket TTS français CPU

Prototype indépendant de l'APK et de JARVIS CORE. Les fichiers Android de la version 49a946b restent inchangés. Aucun entraînement, GPU ou service de synthèse commerciale n'est utilisé.

**État réel :** génération française CPU réussie avec la voix prédéfinie Estelle, trois passages mesurés, réutilisation safetensors et génération sans réseau validées, appel HTTP authentifié au serveur loopback réussi. Les 37 tests locaux passent. Le clonage du MP3 personnel reste à valider : le fichier n’est pas fourni et l’accès authentifié aux poids avec clonage doit être configuré. Voir [le rapport](docs/results.md).

## Installation sur ordinateur

Configuration testée : Linux x86_64, Python 3.12, ffmpeg/ffprobe, uv. Installer ffmpeg et uv via leurs sources officielles, puis depuis ce dossier :

```sh
uv sync --frozen --group dev
uv run --frozen pytest -q
uv run --frozen ruff check .
export JARVIS_VOICE_DATA_DIR="$HOME/.local/share/jarvis-voice"
```

PyTorch 2.8.0 est résolu depuis l'index CPU officiel. Pocket TTS est fixé à 3.3.0 ; les dépendances transitives sont verrouillées par `uv.lock`. Les chemins personnels doivent être **hors de tout checkout Git**. Windows/macOS et les performances sur votre propre ordinateur restent à vérifier.

Dans l’environnement de développement de cette session, `bash prototype/scripts/setup-cloud.sh` depuis la racine prépare `/workspace/toolchains/pocket-venv`. Utiliser son binaire `jarvis-pocket` et `--data-dir /workspace/private/jarvis-voice` dans les commandes suivantes.

## Modèles et licences avant le premier essai

Lire [les vérifications de licences](docs/licenses.md). Le français doit être choisi explicitement : le défaut de Pocket TTS est l'anglais. Le prototype impose `language="french"`, sans utiliser le modèle `french_24l`, plus lourd.

Les modèles sont téléchargés depuis Hugging Face dans le cache privé lors du premier chargement. Il ne s'agit pas d'une API de synthèse : le MP3, le texte et la synthèse restent sur l'ordinateur. Les cartes des révisions ciblées ont été vérifiées : poids CC BY 4.0, référence Estelle CC0. Les conditions du modèle avec clonage doivent être acceptées sur Hugging Face avant son téléchargement authentifié. Si l'accès demande un compte ou l'acceptation de conditions, utiliser l'accès officiel ; ne pas contourner cette restriction. Une éventuelle variable `HF_TOKEN` doit être renseignée localement et rester secrète.

Le cache public est préparé dans cette session et la génération hors ligne a été vérifiée. Sur un autre ordinateur, un téléchargement complet est nécessaire avant l’utilisation sans Internet. Une fois le cache préparé, `HF_HUB_OFFLINE=1` permet de vérifier que la génération ne dépend pas du réseau. Le modèle de secours « without voice cloning » permet des voix prédéfinies, mais le prototype refuse de le présenter comme un outil de création de profil personnel.

## Importer le MP3 autorisé

Le fichier original n'est ni copié dans le dépôt ni envoyé à un tiers. Exemple :

```sh
uv run --frozen jarvis-pocket prepare /chemin/prive/ma-voix.mp3 --seconds 12
```

Le résultat donne un `reference_id`. Le programme vérifie le format réel MP3, la taille (32 MiB maximum) et la durée (3 à 300 secondes), décode en mono à 24 kHz et cherche une fenêtre de 12 secondes avec peu de silence et de saturation. Un fichier de 1 à 3 minutes entre dans ces limites. Une pochette intégrée n'est pas utilisée comme audio.

L'extrait et son analyse sont conservés dans `$JARVIS_VOICE_DATA_DIR/references/<reference_id>/`. Écouter `reference.wav` : une seule personne, parole compréhensible, sans musique, et idéalement une phrase complète. Les mesures d'énergie ne détectent ni le nombre de personnes, ni la musique, ni le bruit de fond avec fiabilité. Le rééchantillonnage n'améliore pas un mauvais enregistrement.

Pour choisir un passage précis :

```sh
uv run --frozen jarvis-pocket prepare /chemin/prive/ma-voix.mp3 --start 35 --seconds 12
```

## Créer et réutiliser le profil

Après écoute de l'extrait et vérification des droits :

```sh
uv run --frozen jarvis-pocket export REFERENCE_ID --name 'JARVIS' --authorized --reviewed
```

Cela encode la référence avec le modèle existant, sans entraînement, et crée `profiles/<profile_id>/voice.safetensors` avec un manifeste privé. Le profil est un état de conditionnement propre au modèle français et à sa version ; ce n'est pas un format vocal universel. Les identifiants de modèle et les checksums sont contrôlés lors du chargement.

```sh
uv run --frozen jarvis-pocket generate --profile PROFILE_ID \
  --text 'Bonjour, je suis JARVIS. Comment puis-je vous aider ?' \
  --output "$JARVIS_VOICE_DATA_DIR/outputs/bonjour.wav"
```

Texte limité à 500 caractères par appel. Les WAV et les mesures sont exclusivement écrits dans le dossier privé. Les états ne sont pas modifiés pendant la synthèse et peuvent être réutilisés.

## Profil prédéfini pour vérifier le serveur

Avant de disposer des poids avec clonage et du MP3 personnel :

```sh
uv run --frozen jarvis-pocket preset --voice estelle
```

Cette commande crée un profil privé **prédéfini**, réutilisable en safetensors, à partir de l’état public Estelle déjà fourni par Kyutai. Elle n’encode pas votre MP3 et ne crée pas une nouvelle voix personnelle. L’ID retourné peut être utilisé avec `generate`, `benchmark` et le serveur. Si le modèle chargé change (public sans clonage / modèle avec clonage), recréer le profil prédéfini pour conserver la compatibilité de révision.

## Mesurer la génération sur CPU

```sh
uv run --frozen jarvis-pocket --threads 2 benchmark --profile PROFILE_ID \
  --text 'Bonjour, je suis JARVIS. Cette phrase est générée en français sur cet ordinateur.' \
  --repeats 3 --output "$JARVIS_VOICE_DATA_DIR/benchmark/francais.wav"
```

Avant de posséder un profil, `--voice estelle` permet un essai avec la voix française prédéfinie, **après vérification de sa licence spécifique**. Cet essai n'est pas le clonage de votre MP3.

Le rapport privé `.metrics.json` distingue chargement du modèle, chargement du profil, temps de génération, délai du premier bloc, durée audio, CPU et pic RSS échantillonné toutes les 10 ms. Le premier passage inclut l'échauffement ; les suivants reflètent le modèle conservé en mémoire. Les graines sont fixées et changent entre passages. RTF = temps de génération / durée audio ; RTF < 1 signifie une génération plus rapide que la lecture. Le délai du premier bloc n'est pas la latence Android : le serveur actuel renvoie le WAV complet. La RAM mesurée est celle du processus, pas le cache disque ni un processus de lecture audio.

## Serveur privé et Android

```sh
uv run --frozen jarvis-pocket token
uv run --frozen jarvis-pocket serve --port 8765
```

`token` crée un fichier secret privé sans en afficher la valeur, et refuse d'écraser un secret existant. `serve` conserve le modèle en mémoire et écoute **uniquement sur 127.0.0.1**. Aucun paramètre ne permet une écoute publique. L'API vérifie `Authorization: Bearer <secret-local>` ; elle ne reçoit que l'ID d'un profil privé déjà créé et le texte. Elle n'accepte aucun chemin, URL audio ou upload. Appels disponibles : `GET /health`, `POST /v1/speech` avec `{"profile_id":"UUID","text":"Bonjour"}`. Le résultat est un WAV, sans cache HTTP.

Un seul appel modèle est permis à la fois, les autres reçoivent 429. Ne pas configurer de redirection de port sur le routeur ni de tunnel public. Ce service est un prototype personnel à secret partagé, pas un service multiutilisateur.

Pour une future validation Android, un câble USB et `adb reverse tcp:8765 tcp:8765` permettront une communication avec le serveur de l'ordinateur sans exposer son port réseau. Un accès depuis le réseau privé devra être étudié séparément avec VPN/TLS et règles d'accès. L'APK actuel n'est pas modifié à cette étape. Son moteur Android restera le repli hors ligne lorsque l'ordinateur est inaccessible.

L'exécution directement sur téléphone avec sherpa-onnx est décrite dans [l'étude Android](docs/android.md). Le français et la réutilisation des profils safetensors PyTorch sur ONNX ne sont pas encore validés.

## Confidentialité et Git

Le dossier privé est refusé s'il est à l'intérieur d'un checkout Git. Les références, profils, audios générés et secrets sont privés ; sur Linux, le dossier racine est en mode 0700 et les principaux fichiers en 0600. Les ignore Git couvrent également les audios, états, modèles et manifestes privés. Aucun test CI ne télécharge de modèle, n'utilise de voix personnelle ou ne publie d'audio. Les tests audio utilisent des signaux synthétiques ; les tests de profils utilisent des tenseurs synthétiques ; le serveur est testé avec un moteur simulé.
