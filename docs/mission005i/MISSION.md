# JARVIS VOICE — MISSION 005I
## Expression vocale, personnalité et conservation stricte de l'identité

**Mission à exécuter par Codex. Ce document est une spécification, pas un rapport de code exécuté.**

- Dépôt : `bubth720/JARVIS-VOICE`
- Branche dédiée : `feat/voice-expression-005i`
- Base vérifiée : `feat/voice-identity-005h`, commit `1d0285ea19fa84c052ab5ce3b2f08146bcf30976` (PR #6 brouillon, non fusionnée)
- Dépendances : JARVIS 005G (sampler LSD et rendu naturel), 005H (coffre d'identités + archives JVIENC01)
- Livraison souhaitée : composants indépendants, tests et APK expérimentale Android hors ligne, PR brouillon distincte vers la branche 005H, jamais vers main sans validation explicite
- **Important :** l'utilisateur a choisi **le profil B** à l'oreille dans 005H. Ses fichiers réels et son profil privé vivent uniquement sur son Xiaomi ; ne pas inférer UUID/empreinte ni en réclamer de copie Cloud. Ne jamais remplacer cette identité automatiquement.

## Objectif utilisateur

JARVIS conserve trois dimensions distinctes :
1. **Identité vocale** : timbre/voix principale B conservés au cours de toutes les situations.
2. **Personnalité** : tempérament, façon de s'exprimer, humour, précision, politesse ; appartient conceptuellement à CORE.
3. **Expression** : style d'élocution en fonction de la situation, par exemple calme/informatif, chaleureux/conversation, ferme/urgence, curieux, surpris, humoristique.

Ne pas confondre personnalité textuelle et émotion acoustique. Pocket TTS French 6L **ne fournit pas aujourd'hui de véritable commande d'émotions validée**, ni de dissociation mathématique timbre/style. Le rendu naturel 005G est déjà apprécié, particulièrement sur les phrases longues : ne pas le dégrader par des découpages excessifs, des effets artificiels ou des réglages imposés.

### Règles absolues

1. Garder les applications et données **005D/005F/005G/005H** intactes. Pas de modification destructive, migration forcée, désinstallation, changement de signatures, pertes de profil, poids ou packs. Préférer une APK indépendante `com.jarvis.voice.voiceexpression005i`, avec sandbox propre et clé stable privée vérifiée. Justifier toute dérogation avant modification.
2. Aucune donnée privée (MP3, WAV, JVFP1, .jvi, PCM, notes d'écoute, mots de passe) vers GitHub/Codex/Cloud ; aucun accès réseau ni microphone dans l'APK. Le modèle, le ZIP privé, la clé de signature restent privés et non embarqués.
3. Éviter toute rupture de compatibilité des interfaces `VoiceIdentityId`, `VoicePersonaId`, `VoiceExpressionRequest`, `ExpressionCapabilities`, `VoiceBackendIdentityBinding` existantes dans `identityRuntime`. Ajouter par versionnement/migration non destructive.
4. Conserver le même **profil actif** (conditionnement exact, profil UUID et hash) dans chaque variante d'expression testée ; aucun basculement vers une autre référence expressive sans action explicite de l'utilisateur. Un profil étiqueté « chaleureux » n'est pas un paramètre d'émotion.
5. Android Xiaomi prioritaire : hors ligne, 1 thread ONNX par défaut, garde RAM 2 Gio / thermique / batterie, annulation, lecture différée STATIC, cache PCM borné, contexte par phrase inchangé ; mode Pleine puissance sur futur mini-PC réservé à autre mission.
6. Aucun fusion de branche/main, ni clôture/fusion des PR #5/#6. Tests et constats sourcés, sauts/échecs signalés. Aucun contrôle émotionnel ni gains acoustiques revendiqués sans écoute réelle de l'utilisateur.
7. Consentement explicite et légal pour toute voix clonée. Un style « urgent » n'autorise ni usurpation ni fausse alerte. Priorité aux avertissements non trompeurs.

## Mission courte A — Architecture et contrat d'expression

Inspecter l'état actuel de :
- `identityRuntime/src/main/kotlin/com/jarvis/voice/identity/IdentityTypes.kt` (types d'identité/persona/expression déjà existants)
- `pocketRuntime/src/main/kotlin/com/jarvis/voice/pocket/QualityEngine.kt`, `FrenchFrontend.kt`, `QualitySampling.kt` ou son emplacement réel
- `voiceIdentity` 005H (stockage, sauvegarde/restore `JVIENC01`, lecture PCM et contrôle utilisateur)
- rapports/CI 005G + 005H et contraintes Android

Définir et implémenter en Kotlin testable, sans CORE :
- `VoicePersonaDescriptor` : identité *conversationnelle* / préférences de formulation (titre, réglages manuels simples) ; ne contient pas de tenseur vocal et n'associe pas un timbre à une personnalité ;
- `VoiceExpressionIntent` ou schéma versionné issu du champ `intent` existant : `INFORMATIVE`, `CONVERSATION`, `SERIOUS`, `URGENT`, `CURIOUS`, `SURPRISED`, `HUMOROUS`, `NEUTRAL` ;
- `VoiceExpressionPlan` : texte exact ou segments préparés, pauses autorisées, moteur/profil/pack, paramètres de synthèse explicitement choisis, capacités réellement disponibles, effets demandés / appliqués / non pris en charge, provenance du contexte et garde de priorité ;
- `ExpressionPolicy` pure et déterministe : transformation conservative des *instructions explicites* en plan ; aucun classifieur imaginaire, aucun sentiment prétendument détecté ; par défaut NEUTRAL ;
- contrat de future intégration CORE→VOICE versionné (JSON ou Kotlin sérialisable avec validation taille/valeurs/injections) : `utteranceText`, `identityId`, `personaId` facultatif, `expressionIntent`, `intensity`, `priority`, `speedRequested`, `pauseMs`, flags. **Aucune modification effective de JARVIS CORE dans 005I.**

Garde : l'intention ne doit jamais remplacer l'identityId/profileId ni contourner le consentement ou les protections. `ExpressionCapabilities` doit faire apparaître exactement les demandes ignorées / dégradées.

**Critère d'acceptation A** : tests JVM de sérialisation/validation, priorité, style inconnu, champs invalides, fallback NEUTRAL déterministe, non-confusion persona/identité, aucune action non prise en charge présentée comme réussie. Documenter contrats avec exemples.

## Mission courte B — Expressions sûres avec Pocket TTS 6L

Introduire un `LocalProsodyPlanner` et une couche de rendu aux capacités **strictement vérifiées**. Premières fonctions :
1. Style d'information calme et conversation chaleureuse **via choix de formulation provenant de CORE plus tard**, et dans l'app 005I via texte fourni/choix manuel ; ne pas fabriquer ni paraphraser automatiquement des informations critiques.
2. Gestion contrôlée des pauses **entre phrases complètes**, quand techniquement sûre. Ne pas fragmenter artificiellement les phrases longues qui sont plus naturelles dans 005G. Mesurer limites de 50 tokens / 20 secondes et refuser une phrase trop longue plutôt que la tronquer.
3. Gestion de ponctuation et du rythme seulement après vérification que les mots, chiffres, négations, citations, noms propres, sens et contenu informatif restent identiques. Ne pas insérer arbitrairement de mots, respirations, effets de pitch, timestretch ou changement de profil pour simuler une émotion.
4. Préserver le **sampler/seed/température/profil/pack** choisi. Ne pas changer discrètement LSD ni température pour « exprimer » une émotion. Si des essais de réglages spécifiques sont envisagés, les exposer comme expériences manuelles A/B, jamais comme commandes émotions garanties.
5. Gestion « URGENT » sûre : le moteur local lent n'est pas une alarme temps réel. Spécifier et, si utile, prototyper un signal d'alerte synthétique immédiat distinct de la phrase longue (autorisation utilisateur et sans confusion avec une urgence avérée). Ne jamais promettre de latence bornée sur Xiaomi. Message urgent plus court uniquement si le CORE fournit explicitement une version courte validée ; sinon conserver le texte.
6. Les styles `SURPRISED`/`HUMOROUS`/`CURIOUS` peuvent rester en **intention non réalisable acoustiquement** : l'UI doit le signaler. Pas de « mode émotion » simulé par simple renommage d'un sampler.

**Critère B** : tests du plan sur phrases françaises longues et courtes, refus des altérations de sens, invariance profile hash/pack, mesures du PCM et des timings, comparaison baseline NEUTRAL vs moteur 005G (même input/profil/seed/settings -> sortie identique dans test de référence publique), garde mémoire/annulation ; aucune amélioration subjective inventée.

## Mission courte C — Application Android 005I, expérience complète

Application expérimentale indépendante installable aux côtés de 005H, **sans PC ni ADB**. Obtenir la voix sans perdre le profil B :
- Reprendre le **ZIP FP32 existant** via SAF local.
- Permettre **l'import d'une archive privée `.jvi` issue de 005H**, en réutilisant le protocole `JVIENC01` existant, authentifié AES-256-GCM / PBKDF2. Demander le mot de passe uniquement localement. Ne pas réencoder ou reconstruire le profil si l'archive est valide et le pack identique. Ne pas changer le format JVFP1. Refuser clairement si l'archive est incompatible ou si un profil est absent.
- Avant tout essai réel, **conserver 005H installée avec le profil B** et une sauvegarde chiffrée. La restauration 005H→005I est *à valider physiquement* sur Xiaomi : tests JVM et émulateur ne le démontrent pas. Si la migration de l'archive échoue sur appareil, stopper et diagnostiquer ; ne jamais faire supprimer 005H, ni demander d'envoyer l'archive au cloud.
- Vérifier la possibilité de préserver les labels et les références de l'identité ; si l'archive n'inclut pas les WAV, ne pas prétendre qu'ils y figurent. Conserver un seul profil actif par génération.

Interface modeste, sans forcer un utilisateur à entrer des JSON :
- sélectionner une identité importée et son profil actif (B par action explicite) ; afficher un repère local non sensible (alias) et le profil/pack compatibles ;
- saisir une phrase, sélectionner un style, montrer **« prise en charge / limitée / non prise en charge »**, générer, écouter, arrêter, réécouter ;
- comparaison A/B `NEUTRAL` / style choisi **même voix, texte, graine, sampler, température**, notes perceptives privées (naturel, expressivité, timbre, intelligibilité), contrôles de dérive. Une sortie acoustiquement identique entre styles non pris en charge est une issue honnête.
- pas de texte secret dans logs, rapports, crash ou télémétrie ; aucune donnée personnelle exportée autrement qu'archive manuelle chiffrée.
- conserver **mêmes contrôles de sécurité** que 005H (RAM, thermique, annulation, taille PCM, sauvegarde).
- modèle/pack/source non inclus dans APK ; clé de signature stable existante réutilisée uniquement si disponible et vérifiée ; sinon documenter blocage, ne pas prétendre stabilité.

**Critère C** : 005I s'installe à côté de 005H ; migration JVIENC01 005H→005I testée sur fixture publique JVM+Android, mauvais mot de passe/altération sans perte, profil toujours strictement le même, synthèse neutre identique au backend référence, statut honnête des styles, retour arrière sans perte. APK installée/écoutée physiquement sur Xiaomi = validation humaine ultérieure, pas un test Cloud.

## Découpage, publication et validation

Codex : ne pas réaliser tout d'un seul bloc risqué. Effectuer **A puis B puis C**, chaque phase en commits séparés avec tests ciblés et notes. Si le contexte ou l'environnement Cloud empêche une phase, livrer les phases finies et expliciter le blocage, sans cocher fictivement la recette globale.

Livrer :
- `docs/mission005i/ARCHITECTURE.md`
- `docs/mission005i/EXPRESSION-CAPABILITIES.md` (véritables capacités vs souhaitées)
- `docs/mission005i/CONTRAT-CORE-VOICE.md`
- `docs/mission005i/TEST-XIAOMI.md` (depuis seul Android, sauvegarde B et profil)
- `docs/mission005i/RESULTATS.md` et résultats JSON factuels de CI / tests locaux
- APK expérimentale signée 005I et URL d'artefact uniquement si l'application passe les tests et audit
- PR **brouillon** de `feat/voice-expression-005i` vers `feat/voice-identity-005h`, sans fusion

Audit : Kotlin JVM ; tests instrumentés Android pour expression, refus capacités, conservation identité, audio, cache, archive ; vérification non-régression 005F 10/10, 005G 14/14, 005H 18/18 si ces suites tournent ; documenter l'échec 005D connu sans le dissimuler. Vérifier aucune permission réseau/micro, absence de poids et de médias privés, signature, versionCode et packaging.

Rapport final strict : module, mission, branche, commit, PR, tests réussis/échoués/sautés, blocages et prochaine action concrète. Ne jamais confondre tests d'émulateur avec retour perceptif de l'utilisateur. Ne créer ni déployer de modification dans JARVIS CORE.

**Démarre immédiatement par l'inspection du dépôt et la phase A, puis poursuis la réalisation jusqu'à la livraison vérifiable. Ne fusionne aucune branche.**
