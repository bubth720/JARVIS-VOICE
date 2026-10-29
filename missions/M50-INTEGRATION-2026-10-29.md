# VOICE-M50-01 — Sortie vocale locale sobre et synchronisation avatar

Dépôt : bubth720/JARVIS-VOICE
Branche mission : mission/m50-integrated-beta-20261029
SHA base : d10671119fa9a52bc130c28b21ca193edd99d604

**DOCUMENT DE MISSION UNIQUEMENT — PAS DE CODE MODIFIÉ PAR CE FICHIER.** Objectif : préparer les modules optionnels pour JARVIS M50 tout en respectant indépendance et fonctionnement local. Codex crée une nouvelle branche de développement depuis la branche mission, tests et PR brouillon. Sans fusion main, activation sensible, installation utilisateur, migration ou changement de licence/ABI/minSdk sans validation humaine.

## Fichiers et contrats (vérifier présence)
- `docs/fusion-02/CONTRACT.md`
- `docs/fusion-02/TESTS.md`
- `voiceBridgeContract/src/main/kotlin/com/jarvis/voice/bridge/BridgeMapper.kt`
- `voiceSdk/src/main/kotlin/com/jarvis/voice/sdk/SpeechContracts.kt`
- `voiceFinalSdk/src/main/kotlin/com/jarvis/voice/final01/VoiceEvents.kt`

## Phases et vérification

### 1 — A — Baseline

Qualifier 237 cas réussis/23 skips du lot1; ne pas convertir skips, audio synthétique API26/35 ou rendu Linux en écoute réelle. Préserver voix privée, profils, 005J, contrat Binder signé.

Réception : commit unique/étape, tests positifs/négatifs, SHA exact + CI et statut PASS/BLOCKED/NOT_RUN, dépendances, rollback.

### 2 — B — Canal événementiel opt-in

Dessiner un consommateur local typé CORE→VOICE avec identité de session et budget, OFF sans autorisation. Aucun deuxième STT Whisper, aucune permission micro ajoutée et aucun chargeur de modèles automatique.

Réception : commit unique/étape, tests positifs/négatifs, SHA exact + CI et statut PASS/BLOCKED/NOT_RUN, dépendances, rollback.

### 3 — C — TTS réellement audible en banc

Sur émulateur autorisé : démontrer lecture d'une phrase publique synthétique sans données personnelles ; distinguer généré PCM, écrit AudioTrack, joué estimé et réellement entendu sur appareil autorisé. Tester Stop et ERROR/UNKNOWN, manque de mémoire, rotation et destruction Activity.

Réception : commit unique/étape, tests positifs/négatifs, SHA exact + CI et statut PASS/BLOCKED/NOT_RUN, dépendances, rollback.

### 4 — D — Avatar et émotions

Partager seulement IDLE, THINKING, SPEAKING_ESTIMATED, STOPPED, UNKNOWN avec INTERFACE; aucun phonème/visème fictif. Toutes émotions non NEUTRAL restent UNSUPPORTED jusqu'à évaluation humaine/acoustique ; documenter leur phase future sans retarder M50.

Réception : commit unique/étape, tests positifs/négatifs, SHA exact + CI et statut PASS/BLOCKED/NOT_RUN, dépendances, rollback.

### 5 — E — Budget

Garder petit mode Android, métriques premier PCM, temps de réponse, joules/charge estimée explicitement et stratégie pause CORE/LLM. CI JVM, Android synthétique, builds/lint au HEAD; NOT_RUN pour qualité réelle et appareil non testé.

Réception : commit unique/étape, tests positifs/négatifs, SHA exact + CI et statut PASS/BLOCKED/NOT_RUN, dépendances, rollback.

## Conditions communes M50
- Les modules indispensables de l'APK initiale sont CORE, UI et un service local de documents / CSV ; LLM natif devient indispensable pour dire « conversation IA réelle » et doit être qualifié séparément.
- VOICE et EVOLUTION ne doivent pas être des dépendances bloquantes : un échec d'audio ou de diagnostic n'affecte ni chat ni documents.
- Ne jamais présenter un simulateur, une compilation ou un refus correct comme une véritable intégration Android en production.
- Ne pas copier secrets ou modèles protégés dans le dépôt et n'utiliser aucune connexion réseau au runtime.

## Bilan requis
Module, mission, branche, commit 40 caractères, arbre, tests pass/fail/skip/not-run, ABI/API, CI URL, PR brouillon, blocages et prochaine action. Aucun merge main. Toute action sensible nécessite confirmation explicite.
