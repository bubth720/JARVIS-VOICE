# Reproduction — aucun téléchargement de modèle

JDK17+, SDK35, Gradle8.13 (wrapper). Laboratoire émulateur uniquement pour les scripts
ADB : refus des serials physiques, vérification ro.kernel.qemu. Les clés de livraison
historiques ne sont ni lues, ni remplacées. Ne pas installer les APK sur un téléphone
ou publier leur distribution dans cette mission.

```sh
python3 scripts/fusion-02/check_scope.py
./gradlew :voiceFusion02:test :voiceFusion02:run :voiceFinalSdk:test \
  :voiceFinalRuntime:test :voice04Runtime:test :voiceSdk:test :studioRuntime:test \
  :expressionRuntime:test :identityRuntime:test :pocketRuntime:test \
  :voiceBridgeContract:test :voiceBridgeHarness:test :app:testDebugUnitTest \
  :voiceBridgePilot:testDebugUnitTest -PincludeVoicePilot=true \
  --no-daemon --max-workers=2 --rerun-tasks
python3 scripts/fusion-02/collect_tests.py --output work/fusion-02/JVM.json
./gradlew :voiceFinalStudio:assembleDebug :voiceFinalStudio:assembleDebugAndroidTest \
  :voiceFinalStudio:lintDebug -PprototypeTestAbi=x86_64 --no-daemon --max-workers=2
# Après création explicite d'un émulateur propre API26 ou35 :
ANDROID_SERIAL=emulator-5554 bash scripts/fusion-02/android_audio.sh
```

Tests ajoutés : 16 JVM protocole/exécution locale, 3 Robolectric refus de proposition
vers vraie autorité V1, 3 instrumentés lecteur réel/signal synthétique et 1 benchmark
natif opt-in. Les 8 anciens tests Android restent exécutés sans changer leurs assertions.
Pause de frontière est éprouvée dans la machine d'états JVM ; pas de pause intra-AudioTrack
ajoutée au lecteur qualifié. Pas de test V2 Binder kernel revendiqué.

Le Cloud utilise ses JDK/Gradle/SDK/cache déjà présents sous work, `--offline` et
`-PpilotRobolectricDir=$PWD/work/voice-bridge-pilot/robolectric` (jar SDK de test existant).
Autorisation réseau du shell nécessaire au socket local de verrou Gradle. Aucune
valeur de credential n'est lue/affichée. Les variables HOME/CODEX_HOME ne sont pas modifiées.

## Benchmark optionnel réel

Réutiliser uniquement un pack FP32 déjà contrôlé et Alba publique autorisée au hash
46264e83cb99115c3d210260e029117566d9c64f20266d10daa78107759ede3e.
Ne pas donner ces variables à un pack privé inconnu, ni un MP3 personnel.

```sh
FUSION02_PACK="$PWD/work/mission005e/fp32-pack" \
FUSION02_PUBLIC_WAV="$PWD/work/public-alba.wav" \
FUSION02_OUT="$PWD/work/fusion-02/native" \
./gradlew :voiceFusion02:test --tests '*PublicRenderBenchmarkTest' \
  --no-daemon --max-workers=1 --rerun-tasks
```

Ce chemin exécute l'encodeur de référence officiel public, puis le même moteur qualifié
par segment. Aucune modification de poids ni d'arithmétique. Trois répétitions par
corpus : deux phrases courtes et un document de huit phrases/segments, tous <=50 tokens,
texte source reconstruit exactement. 9 documents,30 segments réellement synthétisés.
Le PCM est effacé en mémoire après réception ; aucune sauvegarde audio. Artefact JSON
public de chiffres uniquement, pas de profil ni d'audio. Température0,3, graine42, LSD3,
un thread ONNX. Linux n'a pas AudioTrack : firstWrite/firstAudible restent null.
PSS lu dans /proc/self/smaps_rollup après document, pas pic ; CPU = delta durée CPU du
processus. P50/p95 nearest-rank n=3, exploratoire ; JIT/GC/caches non neutralisés.
Notre passe locale a été lancée avec les régressions dans le même Gradle max-workers2 :
concurrence d'autres JVM possible, aucune comparaison de gain causal revendiquée.
L'encodage/résolution de référence et la planification précèdent les répétitions.

## Non exécuté / limites

Pas d'écoute humaine, pas d'appareil POCO/Redmi, pas de voix B ; n=0. Pas d'IPC CORE,
STT, émotion non neutre ou preuve de déchargement natif. Aucun état FUSION-02 global
n'est déduit d'un PASS JVM/Robolectric. AndroidAPI24/25 ne concerne pas le VOICE min26 :
CORE doit garder sa restitution existante et traiter ce moteur comme indisponible.
La CI séparée publie XML/rapports uniquement. Les gardes historiques ont leur périmètre
propre et ne sont pas modifiées pour accepter les nouveaux fichiers ; leurs refus sont
rapportés séparément. Ne pas prétendre « tous les workflows verts ».
