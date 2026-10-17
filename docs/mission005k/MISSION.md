# JARVIS VOICE — MISSION 005K
## Pont expérimental CORE ↔ VOICE : contrats, sécurité, performances mesurées

**État à la création : mission technique préparée, aucun lien CORE↔VOICE opérationnel annoncé.**

### Dépôts et bases vérifiés le 16 octobre 2026
- VOICE : `bubth720/JARVIS-VOICE`, branche **`feat/voice-core-bridge-005k`** créée depuis `feat/voice-studio-005j` commit **`cce9a4549c35e721ee37daff014d427ef3656b2a`** ; PR 005J #8 en brouillon, non fusionnée.
- CORE réel : **`bubth720/JAVIS-Local-v2`**, `main` **`226e2e8cc3eebd479805b89d035eb5c1cbcad540`** ; ce n'est PAS un dépôt nommé JARVIS-CORE. Android `applicationId=com.stark.jarvislocal`, `minSdk=24`, contient `JarvisCore.chat()`, `AppGraph`, `LocalTts`, `WhisperLocalEngine`, SQLite/RAG/PDF/LLM historiques.
- UNIFIED : `bubth720/JARVIS-UNIFIED`, branche documentaire `mission-001g-maintenance`, ADR-001 approuvée avec précisions ; transport IPC, sécurité de signature, déploiement et arbitre interprocessus **non encore approuvés**. UNIFIED **n'est pas un runtime**.
- VOICE Studio 005J : `applicationId=com.jarvis.voice.voicestudio005j`, `minSdk=26`, moteur actuel Pocket TTS French 6L FP32, pack/profil local exact ; tests Android 24/24, comparaisons 005I bit-identiques sur quatre cas publics (rapports 005J). Le propriétaire a maintenant testé 005J physiquement sur Xiaomi : la restauration de son profil B et la lecture fonctionnent, interface plus simple et vitesse **ressentie** meilleure ; il demande encore d'accélérer **si possible**. Aucune mesure de latence/PSS/thermique enregistrée par ce retour.

### Objectif et frontière

Préparer le premier chemin **CORE produit une réponse → module VOICE la prononce hors ligne**, sans présumer que la simple présence d'un DTO ou d'une APK rend ce lien opérationnel, et sans modifier les applications installées.

**005K doit produire un audit précis, un adaptateur/contrat testable et des essais de performance traçables. Ne pas déployer un pont IPC exporté, modifier CORE ou demander une migration du profil B tant que les choix d'IPC, de sécurité et d'arbitrage interprocessus n'ont pas été validés séparément par le propriétaire.** Un prototype IPC peut être réalisé uniquement en application/fixture isolée de test qui ne donne pas accès aux profils personnels et n'est pas intégrée dans l'APK Studio installée. Après décision d'architecture et accord explicite, prévoir deux missions liées dans les dépôts propriétaires CORE et VOICE.

### Garanties absolues

1. Préserver `005D/F/G/H/I/J`, leurs APK, packages, signatures, modèles, profil B personnel, archives `.jvi`, rendu PCM, pauses et cache. **Pas d'édition des anciens modules/applications**, pas de `main`, pas de merge PR #1-#8, pas de réencodage des données privées ni de copie inter-sandbox.
2. Hors ligne strict : aucun serveur, HTTP, compte, micro ni réseau ajouté à VOICE ; données du propriétaire (texte personnel, fichiers, modèles, UUID/SHA privés, mots de passe) jamais transmis dans GitHub/CI/Codex.
3. CORE reste seul propriétaire des sessions, de l'autorisation et du texte définitif. VOICE synthétise exclusivement une demande autorisée ; jamais de commande système, permission, modification de mémoire ou confirmation implicite sur simple texte.
4. Préserver le TTS ancien de CORE comme **repli explicitement contrôlé**, surtout API24/25 ; ne pas remplacer `LocalTts` silencieusement. Aucun bypass de la confidentialité ou du consentement vocal.
5. Contrainte Android : **un seul travail lourd à la fois** pour le pilote, garde mémoire **2 Gio** et protections thermiques/batterie intactes. Le coordinateur LLM en cours n'est pas un arbitre Android interprocessus. Si CORE/LLM et VOICE chargent ensemble leurs modèles, risque OOM/thermique : refuser tout prétendu mode E2E « sûr » sans coordinateur et mesure.
6. API24 CORE vs API26 VOICE, signatures/UID Android différents à vérifier ; **aucune hypothèse que permission signature fonctionne entre applis signées différemment**. Choisir le transport et la stratégie de confiance après analyse ; ni service exporté non authentifié, ni Intent implicite exposant du texte ou des profils.
7. Les intentions CONVERSATION/SERIOUS/HUMOROUS etc sont des **métadonnées non prises en charge acoustiquement** par Pocket6L ; ne pas vendre des émotions natives ou une accélération subjective non mesurée.
8. Android offline, téléphone Xiaomi prioritaire ; futur mini-PC abstrait par adaptateur versionné et moteur interchangeable. Pas de « pleine puissance » sur mobile.

## A — Audit et décision à soumettre : contrat CORE ↔ VOICE

Lire réellement :
- CORE `app/src/main/java/com/stark/jarvislocal/{JarvisCore.kt,AppGraph.kt,LocalTts.kt,MainActivity.kt}`, manifest, `app/build.gradle.kts`, chemins de réponse/annulation/fallback et tests.
- VOICE `expressionRuntime` et son `CoreVoiceRequestV1`, `identityRuntime` (consentement, UUID/profile/pack), `voiceStudio` 005J, gestion file CPU et génération/réécoute.
- UNIFIED `docs/DECISIONS/ADR-001-CORE-LLM-UNIFIED.md`, `contracts/v1/SEMANTICS.md`, `contracts/v1/{capability-manifest,request-envelope,response-envelope,resource-lease,health-status}.schema.json` et `docs/MATRICE_INTEGRATION.md`.

Écrire `docs/mission005k/AUDIT-CORE-VOICE.md` et `docs/mission005k/DECISIONS-IPC.md`, comparant :
- adapter bibliothèque intra-app, Bound Service Binder/AIDL inter-APK, Intent explicite avec confirmation utilisateur et autres options Android réellement pertinentes ;
- privilèges, UID et signatures réelles ou **inconnues**, isolation profil, permission, surface d'export, installation/uninstallation, API24/26, coût RAM/CPU/processus ;
- stratégie d'activation optionnelle, app absente/incompatible, policy de consentement, lancement en arrière-plan, audio focus, cas de refus, repli local et rollback ;
- quelle autorité accorde et vérifie l'admission des travaux lourds, app en veille/processus mort, nettoyage natif réel, expiration/annulation idempotente ;
- format fermé et mapping du protocole UNIFIED v1 (proposé) vers `CoreVoiceRequestV1`, capacités exactes, profile/pack liés, corrélation, limites de taille, version, timeouts et réponse attendue.

**Sortie A : recommandation argumentée de transport/signature/API et décisions demandant accord.** Ne pas auto-approuver les politiques ouvertes dans l'ADR.

## B — Contrats et adaptateurs sécurisés, sans communication avec le CORE réel

Créer si possible un module **`voiceBridgeContract`** indépendant et petit, sans activité ni permission réseau/micro, avec classes pures Kotlin testables :
- `VoiceCapabilityQuery` / réponse versionnée, profil exact accessible uniquement après confirmation utilisateur et consentement ;
- `SpeechJobRequest` : texte borné, identifiant corrélation, session, échéance/timeout, engine/pack/profile identity exacts (identifiants de test uniquement), demande de pause, priorité limitée sans préemption ;
- `SpeechJobResult` : refus/succès de génération distincts de « lu/audible », erreurs fermées, métriques non sensibles, aucun chemin absolu ou données privées ;
- mapping strict des cinq enveloppes UNIFIED sans les modifier en place ; documenter les incohérences, version négociée, refus des champs inconnus et d'une demande de profil non autorisée ;
- façade `VoiceRendererPort` (injectable) qui peut utiliser QualityEngine uniquement dans un environnement de test autorisé, avec simulation du moteur pour les tests de contrat ;
- `SpeechJobCoordinator` simulé **ou** state-machine de réservation pour tests : unknown/unavailable/ready/generating/generated/playing/cancel_requested/native_done/unloaded/refused ; les simulations ne prouvent aucun arbitrage global.
- pas de lecture d'archive `.jvi` par CORE, pas d'exfiltration de JVFP1, pas de partage de fichiers publics. Le module VOICE garde la propriété des références et du PCM.

Tests JVM et éventuellement tests Android **avec fixtures publiques uniquement** : schemas, limites Unicode, texte vide/trop long, ID session, mauvais pack, profil absent, consentement refusé, app indisponible, annulation répétée, timeout, réponse périmée, incompatibilité API, échec de retour/repli, absent/death, conflit deux travaux lourds, messages malveillants. N'exécuter aucune commande contenue dans le texte. Si prototype de transport Binder en test, **fixture non publiée dans l'APK Studio**, et refus fail-closed vérifié ; distinguer mock vs véritable IPC sur émulateur.

**Sortie B : module documenté/compilé/tests fermés et stable pour futur pilotage, sans faux « CORE raccordé ».**

## C — Observabilité et opportunités réelles d'accélération

Instrumenter dans une **harness indépendante de mesure / utilitaires non intrusifs**, sans modifier les APK 005J déjà installées :
- temps depuis appui/demande jusqu'au début préparation, chargement ZIP/poids ONNX, prise de lock, prompt/tokenisation, encodage du profil (si concerné), génération, premier PCM disponible, entrée lecture AudioTrack et fin audible déclarée ;
- **005J utilise une lecture différée** : distinguer `temps avant premier échantillon disponible`, `temps avant premier son audible` et `temps total`; ne pas inventer un streaming supporté.
- froid/chaud, phrase courte/longue, pauses OFF/ON, mêmes graine/LSD/température/profil de fixture publique ; latence RTF/CPU/PSS/maxRSS/thermique/batterie si disponibles ; données brutes sans texte privé ni empreinte du vrai B.
- proposer petits correctifs **uniquement si benchmark montre un goulot** : validation/réutilisation sûre des ressources déjà ouvertes, transfert de buffers, conversions évitables, séquencement non bloquant, cache borné sur PCM autorisé ; préserver audio bit-identique et intégralité de lecture, pas de quantification ni réduction de qualité/sampler automatique, pas de model-on-standby permanent sur téléphone sans qualification.
- les améliorations nécessitant modification du backend commun de 005J seront proposées dans une mission séparée et soumises à accord/test A/B.

**Critère C : produire baseline testable et recommandations classées gain/risque, pas de gain chiffré revendiqué hors matériel.** Fournir un protocole Xiaomi sans PC pour relevés éventuels ; pas de collecte automatique de données privées.

## D — Rapport et missions de suite

- Documentation obligatoire :
  - `docs/mission005k/AUDIT-CORE-VOICE.md`
  - `docs/mission005k/DECISIONS-IPC.md`
  - `docs/mission005k/CONTRAT-BRIDGE.md`
  - `docs/mission005k/PERFORMANCE-BASELINE.md`
  - `docs/mission005k/TEST-XIAOMI.md` (documentaire/mesure uniquement, **ne pas faire installer une app bridge non approuvée**)
  - `docs/mission005k/RESULTATS.md` avec preuve commit/test/code/doc et limites
  - deux courtes fiches **mission future CORE** (`JAVIS-Local-v2`) et **mission future VOICE**, conditionnées à la décision IPC/auth/ressources par le propriétaire.

- Tests non-régression existants, sans modifier les anciennes suites : JVM des nouveaux contrats ; CI des anciens VOICE 005F 10/10, 005G 14/14, 005H 18/18, 005I 31/31, 005J 24/24 si exécutables, rapporter sauts/échecs ; défaut connu 005D 4/5 jamais dissimulé. Verrou PCM et signature/permissions inchangées. Séparer « mock contrat », « IPC test fixture », « E2E réel CORE » (non implémenté) et « Xiaomi » (non exécuté ici).
- Préparer PR **brouillon** `feat/voice-core-bridge-005k` → `feat/voice-studio-005j`, sans fusion.
- Aucune modification Git de CORE, UNIFIED, LLM, autres dépôts ; aucune installation d'appareil ou transfert d'archive.
- Prévoir deux missions Codex indépendantes pour CORE et VOICE **après approbation explicite** des décisions. Elles devront se coordonner sur les hashes de contrats et l'ordre d'intégration ; conserver le TTS CORE existant en fallback sans recopier données privées.

## Commande à Codex

Vérifie la branche, les quatre dépôts/refs réels, puis exécute **A → B → C → D** par petits commits avec tests ciblés et non-régression. Ne prétends pas avoir raccordé CORE si seul l'adaptateur test existe. Stoppe toute implémentation qui exige un choix de sécurité non approuvé ; livre alors le rapport et les variantes comparées.

**Compte rendu final obligatoire** : module VOICE, mission 005K, branche, commit final, PR, tests réussis/échoués/sautés, blocages, décisions à approuver, prochaine action. **Aucun merge dans main.**
