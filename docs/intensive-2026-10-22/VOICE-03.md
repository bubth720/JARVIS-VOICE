# VOICE-03 — Pont CORE–VOICE opt-in pour texte parlé et fin de session

## 1. Fiche officielle
**Domaine :** VOICE · **Repo :** `bubth720/JARVIS-VOICE` · **Priorité :** P2 · **Dépendances :** VOICE-01,VOICE-02,CORE-02,UNI-01.
**Base vérifiée :** branche `feat/voice-bridge-pilot`, commit `04c4ca1c050c16e097d27b08831544b66121128c`. **Branche de dépôt des consignes :** `plan/intensive-2026-10-22`. **Implémentation proposée :** `feat/voice-03-intensive`.
**Statut :** non exécutée ; validation explicite requise avant Codex.

## 2. État réel préservé
VOICE possède `app` Android TTS, `pocketRuntime` local, `identityRuntime` profils/archives, `expressionRuntime` prosodie et Studio005J autonome. Le pilote Binder V1 opt-in a passé des tests Android réels sur émulateur, mais refuse la génération native ; V2/V3 n'ont pas été activés. Un échec historique AudioTrack005D subsiste. Aucun essai VOICE bridge propriétaire sur Xiaomi n'est implicite.

## 3. Résultat concret attendu
Construire le transport contrôlé de demandes publiques de parole entre CORE et VOICE, sans activer l'autorité native / V2 du pilote avant preuves de libération des moteurs.

## 4. Fonctionnalités à implémenter
1. Réutiliser V-BRIDGE-001 Binder/AIDL v1 authentifié et vérifications signature/UID/package, non réinventer une permission permissive.
2. Créer DTO SpeechJobPublic (texte non personnel, style, requestId, priorité), validation stricte et sérialisation bornée.
3. Ajouter handshake de capacité et refus si CORE/VOICE n'ont pas même autorité signée, permission explicite ou pack local autorisé.
4. Créer états de réservation, progress/ack/release et expiration ; aucun état RELEASE_CONFIRMED sans attestation native réelle.
5. Implémenter côté VOICE une façade de réponse refuse-by-default et, sur laboratoire isolé autorisé, un adaptateur de lecture publique sans poids privés.
6. Préparer tests inter-APK des rejets et comportement à la mort de client ; pas de transfert de profil B ni installation personnelle.

## 5. Classes/interfaces et responsabilités
1. SpeechBridgeContractPublic et StrictDtoValidator
2. AuthenticatedVoiceGateway sur V-BRIDGE-001
3. SpeechJobDispatcher, ResourceLeaseProof
4. VoiceClientAdapter et RefusalReason
Un pack ne doit être détenu que par son coffre d'origine ; distinguer orchestration vocale, stockage de l'identité et lecture audio.

## 6. Chemins de code
1. Existants : modules pilot V-BRIDGE-001, docs/mission-voice-bridge-pilot/CONTRAT-ET-CONFIANCE.md
2. À créer : voiceBridgePilot/src/main/.../SpeechJobDispatcher.kt et voiceBridgePilotClient/src/main/.../SpeechJobClient.kt (vérifier chemins)
3. À créer : tests Binder et port Android autorisé
Les dossiers SDK nouveaux sont proposés et doivent être intégrés aux settings Gradle de la branche ; les chemins existants doivent être revérifiés avant écriture.

## 7. Programme d'implémentation
1. Comparer contrat signature V1 et gate V2/V3 bloqués ; construire DTO sur branche fille.
2. Coder validations et refus sans toucher aux anciens APK.
3. Développer côté VOICE une file de demandes publiques et liaison au SDK dans pilote isolé, uniquement après accord sécurité précis.
4. Exécuter tests d'abonnement, annulation, UID erroné, expiration ; les tests natifs restent à qualifier.
5. Publier patch d'activation séparé si nécessaire avec comparaison de signatures et politique consentement.

## 8. Technologies, performance et dépendances
Kotlin Android Binder/AIDL, permission signature existante, SDK VOICE01 ; Android API min26.
Conserver les versions, la signature, l'API min existantes et la consommation Android modérée ; ne pas introduire de moteur lourd permanent.

## 9. Contrats et interfaces futures
CORE-02 sera client futur ; ne pas activer entrée de génération LLM ni migrer voix privées, UNIFIED reste registre.

## 10. Sécurité et stabilité
Conserver consentement explicite, stockage local et profiles exacts. Aucun transfert de voix, pack, secret, PCM privé ou données de l'utilisateur vers un cloud. V-BRIDGE-001 et les anciennes applis restent intactes. Tout nouveau service/export/permission/native doit être soumis à validation séparée ; sans preuve de fin native, nouvelle admission refusée.

## 11. Livrables du code
Modules SDK et composants compilés, vraie intégration de lecture/refus local, exemple public exécutable, tests essentiels, notes très courtes des limites, commit sur branche fille. Ne pas livrer uniquement des DTO/mocks sans moteur relié lorsque le SDK existant est disponible.

## 12. Critères d'acceptation
1. Une fixture authentifiée peut demander un statut et recevoir refus/accusé corrélé ; parole publique réelle seulement si gate natif validé.
2. Mauvais UID, package et certificat sont rejetés avant lecture du contenu.
3. Rupture du client ne libère jamais à tort un backend natif vivant.
Séparer tests cloud, émulateur, écoute réelle et Xiaomi non testé.

## 13. Planification
VOICE-01,VOICE-02,CORE-02,UNI-01. VOICE-01 avant VOICE-02, et VOICE-03 après les prérequis de contrat. Missions concurrentes dans CORE ou INTERFACE possibles sur branches séparées.

## 14. Git / consentement
La publication de cette fiche n'autorise aucun travail agent, aucune installation, aucune fusion `main`, aucune migration de profil B et aucune modification sensible. Les déploiements et signatures requièrent consentement distinct.

## 15. Restitution à CENTRAL
Module ; mission ; branche ; commits ; sources touchées ; tests effectivement exécutés ; démonstrations ; limitations acoustiques ; problèmes ; prochaine action.
