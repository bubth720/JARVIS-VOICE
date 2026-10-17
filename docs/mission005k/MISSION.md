# JARVIS VOICE — MISSION 005K : latence mesurée + adaptateur CORE→VOICE expérimental

**État : cahier des charges, pas un résultat d'implémentation.** Exécuter depuis Codex sur le dépôt JARVIS-VOICE. Ne pas toucher à CORE ni à UNIFIED.

## Références et périmètre
- Dépôt unique : `bubth720/JARVIS-VOICE`.
- Branche dédiée créée : `feat/voice-performance-core-contract-005k`.
- Base figée : `feat/voice-studio-005j` au commit `cce9a4549c35e721ee37daff014d427ef3656b2a`. PR #8 reste brouillon/non fusionnée.
- UI référence : `voiceStudio` 005J. Moteur : `pocketRuntime/QualityEngine` et ses composants ; `identityRuntime`, `expressionRuntime`, `studioRuntime`.
- Documents à inspecter : `docs/mission005j/RESULTATS.md`, `docs/mission005i/CONTRAT-CORE-VOICE.md`.
- Coordination informative : `bubth720/JARVIS-UNIFIED` branche `decision/d4-02d-v1-v5-approved-2026-10-17`, ADR-001 et dossier D4-02D ; CORE réel `bubth720/JAVIS-Local-v2` (pas `JARVIS-CORE`). Au moment de la préparation, LLM 003F a une branche publiée, CORE 004D n'est pas présent comme branche ; **vérifier l'état réel à l'exécution**.
- Retour humain sur Xiaomi : utilisateur confirme 005J fonctionnelle, plus simple et plus rapide *à utiliser* ; qualité et pauses appréciées ; **aucune mesure chiffrée de vitesse d'inférence ou thermique communiquée**.
- Objectifs : améliorer **si démontrable** délai avant parole et latence de génération ; préparer un client CORE→VOICE de démonstration strictement local **sans IPC réel ni permission nouvelle**.

## Garde-fous absolus
1. Ne modifier aucun code/donnée/APK des versions 005D/F/G/H/I/J déjà validées. Livrer uniquement du code additionnel autonome (bibliothèque `voiceBridgeRuntime` et banc de mesure `voicePerformance`, ou noms conformes au dépôt), plus si possible APK expérimentale **indépendante** `com.jarvis.voice.voicepilot005k` dérivée visuellement de 005J mais sans toucher à son package.
2. Conserver ton du profil personnel **B** et son archive chiffrée `.jvi`. B n'est pas dans GitHub, Codex, les tests publics ou des logs. Import local chiffré dans 005K si APK et vrai essai utilisateur ; ne jamais réencoder / modifier automatiquement la voix et ne jamais transférer les fichiers privés hors téléphone.
3. Zéro permission INTERNET/micro, télémétrie, exécution de commande ou fournisseur distant. Contrat d'identité/profil borné ; aucune autorisation déduite d'un UUID déclaré par l'appelant. CORE seul sera propriétaire des permissions dans une future intégration.
4. Maintenir 1 thread ONNX sur Android, admission RAM ~2 Gio, protections thermiques/batterie, annulation, plafond de PCM, limites françaises de tokenizer, lecture STATIC ; pas de modèle chargé indéfiniment, pas de deuxième travail lourd simultané et pas de mode pleine puissance Android. Une optimisation proposée ne peut contourner ces gardes.
5. Pas de changement caché aux textes, graines, LSD, températures, pauses, segmentation, modèle, 18 états, chiffres ou timbre. À mêmes entrées, **PCM bit-identique** au chemin 005J de référence pour les cas publiés (avec et sans pauses). Aucune proclamation de gain sans benchmark reproductible et comparaison avec dispersion.
6. Ne pas fusionner dans main, modifier UNIFIED v1, CORE/LLM, accepter un transport Binder, appairer un certificat ou publier un service exporté. Aucun raccordement inter-APK, processus global d'arbitrage ou déploiement n'est autorisé par cette mission. Les décisions D4-02D V1–V5 concernent des **fixtures CORE↔LLM** et ne valent pas approbation CORE↔VOICE. Future autorisation distincte requise.

## 005K-A — Mesurer avant d'optimiser

Inspecter `QualityEngine` (qui crée/ferme des sessions ONNX à chaque génération), vérification pack, front-end, Mimi, politique de lecture, capacités de `voiceStudio`. Établir mesures séparées :
- ouverture/contrôle pack, chargement sessions, préparation texte/tokenisation ;
- temps génération premier PCM, génération totale, latence des étapes main/flow/Mimi et files ;
- délai entre tap « Parler » et audio remis au lecteur ; ne **pas** prétendre mesurer le premier son réellement entendu sans instrument physique ;
- temps froid/chaud, même phrase courte vs longue, sans pauses / 2 phrases + 250ms, LSD 1/3, graine et température fixes ;
- CPU, PSS/RAM, statut thermique, éventuelles estimations batterie et effets d'annulation, avec mention des compteurs indisponibles.

Une mesure « plus rapide à utiliser » est une appréciation UX différente du RTF/temps d'inférence. Aucun benchmark « Xiaomi » n'est déduit de Linux/émulateur.

Implémenter un instrumenteur local privé, borné et non identifiant, n'enregistrant **ni textes, ni empreintes personnelles, ni PCM, ni UUIDs** dans les rapports/CI. Fixtures d'Alba publique uniquement en Cloud ; vraie voix seulement sur Xiaomi pour test manuel et aucun téléchargement de pack privé vers CI.

**Acceptation A** : banc mesurant ≥5 jalons avec unités et définitions ; scénarios répétés (médiane, dispersion, environnement, version+SHA) ; cas d'abandon/thermique ; trace sans données personnelles ; observations séparées « Android physique pas encore testé ».

## 005K-B — Optimisations à faible risque, sous porte de preuve

Après l'audit, essayer au maximum **une ou deux** optimisations localisées et réversibles, seulement si un profilage attribue clairement le coût (par exemple éviter du travail redondant de préparation non sensible). Faire d'abord des expérimentations isolées avec tests et drapeaux off/on, comparer mêmes entrées et PCM exact.

L'ouverture/fermeture de sessions peut coûter cher ; étudier son coût, **sans activer une résidence persistante par défaut** : cela peut dépasser les budgets RAM, rendre la gestion multi-processus incohérente et aggraver la batterie. Ne jamais enlever la vérification d'intégrité du pack ni mettre en cache une confiance périmée. Pas de quantification ou conversion ONNX sans nouveau protocole de validation, ni threads supplémentaires ni préchauffage automatique.

Si aucun gain vérifié ou si régression : conserver le moteur identique et livrer uniquement les mesures + propositions. La mission peut être réussie sans accélération mesurée. Prévoir tests cold/warm et déchargement effectif.

**Acceptation B** : baseline et essai identifiés, métriques par texte/durée, PCM bit-identique sur ≥4 cas de référence publics, consommation mémoire/thermique non dégradée **dans les environnements effectivement mesurés**, fallback flag OFF, annulation et arrêt corrects. Ne pas annoncer « 2x plus rapide » sans mesure physique.

## 005K-C — Adaptateur logiciel CORE→VOICE sans connexion physique

Implémenter un **contrat fermé versionné** en Kotlin/JVM dérivé de `CoreVoiceRequestV1`, réutilisant `expressionRuntime` et `identityRuntime`, sans nouveau protocole réseau :
- requête : `sessionId` et `requestId` synthétiques canoniques, ordre/epoch facultatif correctement spécifié, `utteranceText` borné, `identityId`, `profileId` optionnel confirmé localement, `expressionIntent`, `pauseMs`, `allowSentencePauses`, réglages explicitement approuvés ;
- états : `ACCEPTED`, `REJECTED`, `QUEUED/BUSY`, `GENERATING`, `PCM_READY`, `PLAYING`, `FINISHED`, `CANCEL_REQUESTED`, `CANCELLED`, `FAILED` **sans confondre stop demandé, fin du calcul natif et libération du moteur** ;
- client et service **in-process avec doubles de test seulement**, aucune Activity externe, aucun AIDL/Binder exporté, aucun IPC réel. Pas d'autorisation implicite par l'entrée JSON/UUID. Les futures permissions CORE sont un prérequis explicite à une autre mission ;
- versions et capacités : refuse champs supplémentaires critiques, taille/UTF-8/JSON invalide, droits absents, conflit de profil, identité inactive, duplicata `requestId` altéré, réponse tardive, annulation et contention, traitement d'erreur sans fuite ; ordre déterministe, file bornée ou BUSY explicite ;
- pas d'affirmation d'interopérabilité avec les enveloppes UNIFIED v1 sans mapping vérifié. `CoreVoiceRequestV1` existant reste compatible. Toute enveloppe additionnelle proposée est expérimentale et hors `contracts/v1`.

Scénario de fixture : CORE fictif (texte public synthétique) → vérification du profil public → plan d'expression → moteur mock → récepteur d'événements ; test de non-régression du moteur réel sur fixtures publiques dans le banc, sans données utilisateur.

**Acceptation C** : contrats/test doubles exécutables en JVM, limites et refus, correction annulation et appels tardifs, compatibilité version, refus sans identité/autorisations ; aucune nouvelle permission/exposition Android. Analyser risques d'arbitrage interprocessus avec CORE/LLM avant toute future intégration.

## 005K-D — Livraison, parcours Xiaomi et portes futures

Si une APK pilote 005K est réalisable sans toucher à 005J : coinstallable, UI familière 005J, import ZIP FP32 privé + archive chiffrée JVIENC01 locale, profil B sélectionné manuellement ; comparer le rendu, le délai d'initialisation, l'attente avant lecture et la charge sur Xiaomi. L'app peut garder l'adaptateur CORE **désactivé en production** et proposer un test local synthétique uniquement dans Avancé.

Ne pas supprimer/écraser les anciens fichiers. Si aucune APK n'est justifiée, livrer les bibliothèques/benchmarks et signaler que l'essai physique comparatif nécessite une mission suivante : ne pas inventer un lien d'APK.

Documents :
- `docs/mission005k/ARCHITECTURE.md` (contrat CORE futur, frontières et IAM)
- `docs/mission005k/MESURES.md` + résultats machine lisibles par environnement/codeCommit
- `docs/mission005k/CONTRATS-VOICE.md` + jeux de messages synthétiques
- `docs/mission005k/PROTECTIONS.md` (arbiter externe, réservation lourde, sécurité thermique, risques de régression)
- `docs/mission005k/TEST-XIAOMI.md` si APK, avec référence 005J conservée
- `docs/mission005k/RESULTATS.md` (branche, commits par phase, tests, échecs/sauts, blocages)
- PR brouillon **`feat/voice-performance-core-contract-005k` → `feat/voice-studio-005j`**, sans fusion.

Tests : JVM moteur/identité/expression/studio ; Android nouveaux si APK ; non-régression 005F/G/H/I/J ; suivi explicite défaut 005D 4/5 connu, ne pas altérer le test pour l'escamoter ; vérifier CI, artefacts, signatures et limites de ressource si publiés. Un code compile ≠ E2E.

**Exécution Codex** : inspecter d'abord l'état Git réel, puis A → B si preuve disponible → C → D, **commits séparés**, tests après chaque phase. Si un choix requiert IPC, signature, changement de garde ou matériel nouveau, **arrêter cette partie et demander décision**. Ne jamais modifier un autre dépôt. Pas de merge ni installation sur un appareil personnel.

**Compte rendu final** : module VOICE, mission 005K, branche, commit, tests exécutés, ce qui a vraiment accéléré et où, blocages, prochaine action. N'affirmer ni progrès de vitesse Xiaomi ni qualification d'un raccordement CORE sans preuves.
