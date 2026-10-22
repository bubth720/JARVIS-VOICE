# VOICE-01 — SDK vocal local réutilisable et lecture continue à faible latence

## 1. Fiche officielle
**Domaine :** VOICE · **Repo :** `bubth720/JARVIS-VOICE` · **Priorité :** P0 · **Dépendances :** Aucune.
**Base vérifiée :** branche `feat/voice-bridge-pilot`, commit `04c4ca1c050c16e097d27b08831544b66121128c`. **Branche de dépôt des consignes :** `plan/intensive-2026-10-22`. **Implémentation proposée :** `feat/voice-01-intensive`.
**Statut :** non exécutée ; validation explicite requise avant Codex.

## 2. État réel préservé
VOICE possède `app` Android TTS, `pocketRuntime` local, `identityRuntime` profils/archives, `expressionRuntime` prosodie et Studio005J autonome. Le pilote Binder V1 opt-in a passé des tests Android réels sur émulateur, mais refuse la génération native ; V2/V3 n'ont pas été activés. Un échec historique AudioTrack005D subsiste. Aucun essai VOICE bridge propriétaire sur Xiaomi n'est implicite.

## 3. Résultat concret attendu
Transformer les moteurs/lecteurs autonomes VOICE en véritable bibliothèque d'usage client réutilisable, conservant Studio 005J et les packs locaux, sans lier la voix privée au cloud.

## 4. Fonctionnalités à implémenter
1. Construire SpeechSession avec IDs, états IDLE/PREPARING/PLAYING/STOPPING/RELEASED, reprise logique et fermeture idempotente.
2. Faire une API de lecture de texte par lots (sentence queue) vers le moteur local disponible, génération chunk par chunk lorsque le moteur l'autorise.
3. Respecter les permissions sur les packs .jvi, profils du vault et variantes FP32/encodées, sans recopier B depuis le Xiaomi.
4. Créer OutputAudioPort / AudioFocusController et file PCM limitée, comportement clair aux underruns et interruption.
5. Implémenter fallback Android TTS réellement local seulement si son absence de réseau est observable ; sinon retourner NON_VERIFIED plutôt que promettre hors ligne.
6. Intégrer au Studio via une fonction opt-in, garder app005J historique et tous ses schémas.

## 5. Classes/interfaces et responsabilités
1. SpeechSession, SpeechSessionRepository et SpeechRequest
2. LocalSpeechEnginePort, VoicePackResolver
3. PcmFrameQueue, AudioFocusController, PlaybackMetrics
4. AndroidTtsFallbackGate
Un pack ne doit être détenu que par son coffre d'origine ; distinguer orchestration vocale, stockage de l'identité et lecture audio.

## 6. Chemins de code
1. Existants : app/src/main/java/com/jarvis/voice/speech/{SpeechEngine,AndroidSpeechEngine,SpeechRouter}.kt
2. Existants : pocketRuntime/src/main/kotlin/com/jarvis/voice/pocket/*.kt, voiceStudio 005J (inspecter sourceSets exacts)
3. À créer : voiceSdk/src/main/kotlin/com/jarvis/voice/sdk/{SpeechSession,SpeechQueue,LocalSpeechEnginePort}.kt
4. À créer : voiceSdk/src/test/.../SpeechSessionTest.kt
Les dossiers SDK nouveaux sont proposés et doivent être intégrés aux settings Gradle de la branche ; les chemins existants doivent être revérifiés avant écriture.

## 7. Programme d'implémentation
1. Inventorier réellement les lecteurs VOICE005D à 005J et identifier les réutilisations sans renommer les archives.
2. Créer SDK audio sans UI ni permissions nouvelles, types requestId et transitions sûres.
3. Ajouter pack resolver passant par le vault privé sans traversée de sandbox.
4. Raccorder lecteur PCM local et métriques de queue, sans nouveau backend résident.
5. Démontrer speech session sur texte synthétique public et documenter ce qui n'est pas audible en cloud.

## 8. Technologies, performance et dépendances
Kotlin/JVM + Android AudioTrack, coroutines, identité vocale déjà livrée ; pas de nouveau moteur TTS cloud.
Conserver les versions, la signature, l'API min existantes et la consommation Android modérée ; ne pas introduire de moteur lourd permanent.

## 9. Contrats et interfaces futures
CORE ne consommera le SDK que par adaptation explicitement autorisée ; INTERFACE pourra recevoir les états SPEAKING via événement public.

## 10. Sécurité et stabilité
Conserver consentement explicite, stockage local et profiles exacts. Aucun transfert de voix, pack, secret, PCM privé ou données de l'utilisateur vers un cloud. V-BRIDGE-001 et les anciennes applis restent intactes. Tout nouveau service/export/permission/native doit être soumis à validation séparée ; sans preuve de fin native, nouvelle admission refusée.

## 11. Livrables du code
Modules SDK et composants compilés, vraie intégration de lecture/refus local, exemple public exécutable, tests essentiels, notes très courtes des limites, commit sur branche fille. Ne pas livrer uniquement des DTO/mocks sans moteur relié lorsque le SDK existant est disponible.

## 12. Critères d'acceptation
1. Le SDK déclenche une génération/lecture via l'implémentation existante ou retourne explicitement le refus technique.
2. Annulation/libération ne réactive pas un lecteur déjà fermé.
3. Anciens packs, noms et identités ne sont ni copiés ni réencodés.
Séparer tests cloud, émulateur, écoute réelle et Xiaomi non testé.

## 13. Planification
Aucune. VOICE-01 avant VOICE-02, et VOICE-03 après les prérequis de contrat. Missions concurrentes dans CORE ou INTERFACE possibles sur branches séparées.

## 14. Git / consentement
La publication de cette fiche n'autorise aucun travail agent, aucune installation, aucune fusion `main`, aucune migration de profil B et aucune modification sensible. Les déploiements et signatures requièrent consentement distinct.

## 15. Restitution à CENTRAL
Module ; mission ; branche ; commits ; sources touchées ; tests effectivement exécutés ; démonstrations ; limitations acoustiques ; problèmes ; prochaine action.
