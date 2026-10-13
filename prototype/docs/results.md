# Résultats Mission 002 — 13 octobre 2026

Branche : `feat/pocket-tts-cpu-prototype`, basée sur `49a946b`.

## Résultat principal

**La génération française fonctionne sur CPU**, sans API vocale commerciale ni entraînement. Les essais réels utilisent le modèle public français Pocket TTS 3.3.0, révision `8843db76457a91db32077edf8dfcd1c0e3e755fd`, et la voix prédéfinie Estelle (référence annoncée CC0).

Ce résultat ne valide pas encore le clonage du MP3 de l’utilisateur. Aucun MP3 personnel n’est fourni ; le téléchargement des poids avec clonage exige une acceptation sur Hugging Face et un accès de lecture authentifié. Le modèle public indique explicitement `voice_cloning_available: false`.

## Capacités vérifiées

- Pocket TTS 3.3.0 et PyTorch 2.8.0+cpu installés, sans CUDA, GPU ou entraînement. Dépendances verrouillées.
- 37 tests exécutés, 37 réussis, 0 ignoré ; Ruff et son contrôle de format passent.
- Import réel par CLI d’un MP3 synthétique de 65 secondes : WAV mono PCM16 de 12 secondes à 24 kHz et rapport de qualité privés produits. Ce signal ne représente pas une voix humaine.
- Trois WAV français non silencieux de 4 à 4,56 secondes générés avec le modèle réel.
- Profil prédéfini Estelle exporté en safetensors : 8 259 384 octets, environ 7,88 MiB, puis rechargé pour générer une nouvelle phrase. Les checksums et la révision sont contrôlés. Ce profil prédéfini n’est pas une voix clonée à partir d’un MP3.
- Génération avec `HF_HUB_OFFLINE=1` : nouveau WAV français de 3,12 secondes produit à partir de ce profil, sans requête Hugging Face.
- Serveur réel conservant le modèle en mémoire : réponse 401 sans secret, état prêt authentifié, WAV 24 kHz de 4,56 secondes via `POST /v1/speech` en 1,72 seconde. Écoute uniquement sur `127.0.0.1` vérifiée ; serveur arrêté après l’essai.
- Contrôle de transcription local complémentaire par faster-whisper 1.2.1 / Whisper tiny (MIT), sur CPU : les phrases françaises sont globalement retrouvées, avec des erreurs sur « JARVIS » et certaines liaisons. Cela confirme une sortie vocale française mais ne remplace pas l’écoute humaine. La ressemblance avec la voix personnelle n’est pas évaluée.
- Aucun fichier de l’APK Android ni de JARVIS CORE modifié. Aucun audio, profil, poids ou secret suivi dans Git.

## Mesures CPU

Texte identique sur les trois passages : « Bonjour, je suis JARVIS. Je parle français sans service vocal commercial. » Graines 42, 43 et 44 ; modèle gardé en mémoire.

| Passage | Durée audio | Génération | Premier bloc | RTF | Vitesse relative | Pic RSS |
|---|---:|---:|---:|---:|---:|---:|
| 1 | 4,48 s | 1,550 s | 132 ms | 0,346 | 2,89× | 920 MiB |
| 2 | 4,00 s | 1,435 s | 123 ms | 0,359 | 2,79× | 963 MiB |
| 3 | 4,56 s | 1,566 s | 112 ms | 0,343 | 2,91× | 1 000 MiB |

RTF = temps de génération / durée audio. RTF < 1 signifie plus rapide que la lecture. Le pic au chargement est d’environ 1 025 MiB ; le modèle chargé compte 109 501 826 paramètres. Le processus utilise 2 threads Torch, avec des threads Python de génération/décodage ; la mesure CPU cumulée est de 4,55 à 5,15 secondes par passage.

Environnement : Linux x86_64, AMD EPYC 9V74, 5 CPU visibles et quota cgroup équivalent à 4 vCPU, limite RAM 32 GiB, Python 3.12.14. L’environnement Python occupe environ 1,1 GiB, hors poids et caches. Les performances sur un ordinateur personnel, un profil nouvellement cloné ou un téléphone ne sont pas établies par ces chiffres.

Le premier chargement a duré 105 secondes **téléchargement inclus**, puis 6,60 secondes pour récupérer l’état prédéfini. Dans l’essai hors ligne, le chargement du modèle déjà en cache a duré 0,746 seconde (hors import Python/Torch) et le chargement du profil safetensors 7,3 ms. La nouvelle phrase de 3,12 secondes a été générée en 1,307 seconde, RTF 0,419. Ce deuxième texte n’est pas directement comparable aux trois passages du premier benchmark.

Le délai du premier bloc mesure le générateur local. Le serveur actuel renvoie le WAV complet : ce délai de 112–132 ms ne représente pas la latence de lecture dans l’APK.

## Accès et licences

Le proxy a d’abord bloqué Hugging Face. L’accès est ensuite devenu fonctionnel ; les gros fichiers redirigent vers `us.aws.cdn.hf.co`, désormais accessible. Les domaines correspondants et la déclaration sécurisée `HF_TOKEN` ont été enregistrés dans le brouillon d’environnement, sans valeur de secret. Aucun jeton Hugging Face préexistant n’a été trouvé.

Les métadonnées des révisions exactes confirment CC BY 4.0 pour les poids français, avec attribution et conditions interdisant notamment clonage sans consentement et usages trompeurs/illégaux. La référence Estelle est annoncée CC0. Les anciens exports sherpa-onnx comportent une contradiction entre LICENSE (CC BY 4.0) et README (« non-commercial ») : leur diffusion commerciale devra être clarifiée. Voir [les licences](licenses.md).

## Étapes restantes

1. Écouter les WAV de démonstration et vérifier notamment la prononciation de « JARVIS ».
2. Pour le clonage personnel, accepter les conditions sur `kyutai/pocket-tts`, puis renseigner un accès Hugging Face de lecture dans les paramètres sécurisés `HF_TOKEN` de cet environnement, ou localement sur l’ordinateur. Le jeton sert au téléchargement initial, pas à une API de synthèse commerciale.
3. Importer le MP3 autorisé depuis un dossier privé, écouter l’extrait, exporter le véritable profil personnel et mesurer sa génération/cache hors ligne. Ne jamais copier le MP3 ou le profil dans GitHub.
4. Préparer l’intégration APK avec le serveur personnel ; conserver Android TTS pour le repli hors ligne du téléphone.
5. Vérifier séparément les exports ONNX français, leur tokenizer, leurs licences et leurs performances avant toute exécution directe sous Android. Les profils safetensors PyTorch ne sont pas déclarés compatibles directement avec sherpa-onnx.

Le prototype français est validé avec la voix prédéfinie. Le clonage personnel et l’exécution du modèle sur Android restent non validés.
