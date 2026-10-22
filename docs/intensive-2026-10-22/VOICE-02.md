# VOICE-02 — Planificateur d'expressivité, pauses et émotions contrôlées

## 1. Fiche officielle
**Domaine :** VOICE · **Repo :** `bubth720/JARVIS-VOICE` · **Priorité :** P1 · **Dépendances :** VOICE-01,ID-01.
**Base vérifiée :** branche `feat/voice-bridge-pilot`, commit `04c4ca1c050c16e097d27b08831544b66121128c`. **Branche de dépôt des consignes :** `plan/intensive-2026-10-22`. **Implémentation proposée :** `feat/voice-02-intensive`.
**Statut :** non exécutée ; validation explicite requise avant Codex.

## 2. État réel préservé
VOICE possède `app` Android TTS, `pocketRuntime` local, `identityRuntime` profils/archives, `expressionRuntime` prosodie et Studio005J autonome. Le pilote Binder V1 opt-in a passé des tests Android réels sur émulateur, mais refuse la génération native ; V2/V3 n'ont pas été activés. Un échec historique AudioTrack005D subsiste. Aucun essai VOICE bridge propriétaire sur Xiaomi n'est implicite.

## 3. Résultat concret attendu
Donner une personnalité audible en reliant les contrats d'expression VOICE005I à un plan de prosodie concret, sans altérer les échantillons ou faire passer une simulation pour du synthèse vocale.

## 4. Fonctionnalités à implémenter
1. Compléter LocalProsodyPlanner existant avec contrôles de pauses, intensité, rythme et ponctuation par intention de phrase.
2. Créer ExpressionStyleProfiles (Athéna, neutre et réglages) et répartition des styles sans inférer une émotion privée.
3. Construire ExpressionTimeline avec marqueurs de segments et horodatages utilisables par AVATAR.
4. Raccorder au SDK vocal : planification → génération par blocs → lecture, tout en conservant PCM brut et budget mémoire.
5. Ajouter comparaison A/B reproductible de deux styles sur texte public avec écoute manuelle disponible dans Studio.
6. Limiter demande impossible du moteur en sortie explicite STYLE_UNSUPPORTED ; aucun faux effet acoustique.

## 5. Classes/interfaces et responsabilités
1. ExpressionStyleProfile et ExpressionTimeline
2. ProsodyPlannerAdapter + PauseMap
3. SpeechChunkPlanner + AudibleComparisonStore
4. AvatarTimingCuePort
Un pack ne doit être détenu que par son coffre d'origine ; distinguer orchestration vocale, stockage de l'identité et lecture audio.

## 6. Chemins de code
1. Existants : expressionRuntime/src/main/kotlin/com/jarvis/voice/expression/{LocalProsodyPlanner,ExpressionContract}.kt
2. À créer : expressionRuntime/src/main/kotlin/com/jarvis/voice/expression/{ExpressionStyleProfiles,ExpressionTimeline}.kt
3. À créer : voiceSdk/src/main/kotlin/com/jarvis/voice/sdk/ExpressionSpeechAdapter.kt, tests
Les dossiers SDK nouveaux sont proposés et doivent être intégrés aux settings Gradle de la branche ; les chemins existants doivent être revérifiés avant écriture.

## 7. Programme d'implémentation
1. Relire contrats 005I et prévenir toute rupture du format des profils.
2. Implémenter styles, contraintes et planificateur chronologique sans dépendance LLM.
3. Brancher vrais appels d'engine permettant pauses/segments ; si une modulation est indisponible, refuser ce champ.
4. Créer UI de pré-écoute style A/B sans importer le pack B.
5. Tester timing stable et annulation avant/après pause.

## 8. Technologies, performance et dépendances
Kotlin, expressionRuntime existant, AudioTrack, aucun SaaS ni apprentissage en ligne.
Conserver les versions, la signature, l'API min existantes et la consommation Android modérée ; ne pas introduire de moteur lourd permanent.

## 9. Contrats et interfaces futures
IDENTITY transmet choix de style publics validés; AVATAR lit des cues temporels, mais pas de microphone/PCM ni voix privée transférée.

## 10. Sécurité et stabilité
Conserver consentement explicite, stockage local et profiles exacts. Aucun transfert de voix, pack, secret, PCM privé ou données de l'utilisateur vers un cloud. V-BRIDGE-001 et les anciennes applis restent intactes. Tout nouveau service/export/permission/native doit être soumis à validation séparée ; sans preuve de fin native, nouvelle admission refusée.

## 11. Livrables du code
Modules SDK et composants compilés, vraie intégration de lecture/refus local, exemple public exécutable, tests essentiels, notes très courtes des limites, commit sur branche fille. Ne pas livrer uniquement des DTO/mocks sans moteur relié lorsque le SDK existant est disponible.

## 12. Critères d'acceptation
1. Deux profils produisent des plans de segments différents et reproductibles.
2. La lecture effective honore au moins pauses et segmentation avec le moteur local disponible.
3. Aucun champ non pris en charge n'est annoncé comme appliqué.
Séparer tests cloud, émulateur, écoute réelle et Xiaomi non testé.

## 13. Planification
VOICE-01,ID-01. VOICE-01 avant VOICE-02, et VOICE-03 après les prérequis de contrat. Missions concurrentes dans CORE ou INTERFACE possibles sur branches séparées.

## 14. Git / consentement
La publication de cette fiche n'autorise aucun travail agent, aucune installation, aucune fusion `main`, aucune migration de profil B et aucune modification sensible. Les déploiements et signatures requièrent consentement distinct.

## 15. Restitution à CENTRAL
Module ; mission ; branche ; commits ; sources touchées ; tests effectivement exécutés ; démonstrations ; limitations acoustiques ; problèmes ; prochaine action.
