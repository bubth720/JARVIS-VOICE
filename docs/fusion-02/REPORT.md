# FUSION-02 — VOICE : tranche autonome, intégration bloquée

Module `bubth720/JARVIS-VOICE`, issue[#22](https://github.com/bubth720/JARVIS-VOICE/issues/22),
mission directrice [UNIFIED#5](https://github.com/bubth720/JARVIS-UNIFIED/issues/5).
Branche `feat/fusion-02-voice-timeline`, base exacte
`fe57452fcb33f5b6870070f30ba6c5f1d349c4ba`. HEAD = commit contenant le présent rapport ;
code1094c740a6961c59e5e9fdb52e5e4a6ee3c56b47 publié ;
[PR#23 brouillon](https://github.com/bubth720/JARVIS-VOICE/pull/23), base feat/voice-final-01.
Les commits documentaires suivants ne changent pas ce code qualifié.
**PARTIAL : protocole et laboratoire exécutables ; pas de liaison CORE, pas de GO global.**

## Fonctions livrées

- `voiceFusion02` : SDK de chronologie corrélée session/request/generation, horloge
  monotone et provenance d'observation explicites, compteur de séquence, état terminal,
  EOS et comptabilité intégrale des frames. Le SDK v1 reste byte-identique.
- Proxy local effectivement exécutable avec SPI renderer/playback : admission unique,
  refus replay/génération périmée, Arrêt collant, admission conservée tant que le worker
  n'est pas revenu, quarantaine UNKNOWN sans reset. 128 tombstones/256 événements bornés,
  capacité épuisée refusée. Ce n'est pas un arbitre natif CORE ni un journal crash-safe.
- Proposition SpeakRequest limitée à deux phrases publiques, testée contre la vraie
  autorité V1 avec UID/certificat/grant/expiration simulés par Robolectric. Même autorisé,
  résultat NATIVE_AUTHORITY_UNAVAILABLE. Aucun AIDL/service/permission/signature modifié.
- Façade avatar locale : speaking **estimé**, pause/arrêt/unknown, refus des événements
  hors ordre et de mauvaises opérations. Pas de phonèmes ni raccordement INTERFACE.
- Préparation STT : UNSUPPORTED_CORE_WHISPER_NOT_CONNECTED, aucun second moteur ou
  permission micro. Émotions autres que NEUTRAL toujours UNSUPPORTED ; baseline neutre
  ne signifie pas nouvelle qualification humaine.
- Benchmark natif public rendu-seul, tests nouveaux, garde de tous les fichiers
  historiques, CI indépendante avec campagne AudioTrack API26/35 et rapports uniquement.

Le code runtime des applications antérieures, JNI, poids, paramètres ONNX, gardes
thermiques/batterie/mémoire, clés, profils et archives n'est pas modifié. Seulement
settings ajoute le module ; deux builds ajoutent une dépendance **de test**. Aucun module
frère modifié. La proposition reste additive et inerte : le Studio conserve sa chronologie v1
jusqu'à une future revue d'intégration. Le profil B présent sur téléphone n'a pas été
ouvert, copié ou réencodé. Les régressions utilisent des coffres synthétiques neufs.

## Corrections et refus vérifiés

La proposition initiale appelait l'observateur sous verrou de chronologie : supprimé
au profit de snapshots immuables bornés, afin de ne pas bloquer Arrêt avec un callback.
Modes rendu-seul/lecture séparés, fin tronquée ou sans EOS refusée, aucun Finished Linux.
Échec concurrent après Arrêt ne remplace pas Cancelled ; NativeUnknown peut aggraver
l'état terminal et interdit un nouveau job. Buffer rendu effacé après usage.

Deux échecs de mise en place conservés dans le bilan : première compilation du test
utilisait flatten sur FloatArray (corrigé en all imbriqué), puis commande sans
-PincludeVoicePilot=true (corrigée, pas de modification du contrôle opt-in). Le premier
lancement Gradle sans socket autorisé échouait avant build ; exécution avec autorisation
réseau du shell pour IPC local, sans altération de TLS ni credential.

## Audit et branches réellement consommées

[PROVENANCE.json](PROVENANCE.json) contient les huit têtes revérifiées par git ls-remote
le26octobre2026, alternatives et merge-bases. Elles sont inchangées par rapport au gel
initial. Seule VOICE-FINAL est consommée comme base ; le pilote V1 en est déjà ancêtre.
VOICE-07 diverge depuis1a9f713 et n'est pas implicitement composé ; CORE-FUSION et
CORE-VOICE, NEXUS port/final et SERVICES port/final divergent également.
Les historiques fratries ont été consultés via clones Git nus de métadonnées et rapports,
pas via copie de sources dans les applications. Aucun lock UNIFIED interrepo modifié.

[CI historique auditée](evidence/BASELINE-CI.json) :

| Référence | Résultat constaté et portée |
|---|---|
| VOICE-FINAL fe57452,40145045344 | SUCCESS : unit/audio26/audio35/APK ; autres workflows à ce SHA ont aussi des échecs historiques, conservés |
| CORE40215415459 puis40215604539 | Premier échec setup-android tools ; reprise au36761ce réellement SUCCESS, pas qualification C1 Android |
| NEXUS40115049468 | JVM vert ; setup Android tools échoué avant instrumentation |
| SERVICES40115305244 | JVM/instrumented verts ; build port échoue à vérification de guava-parent-33.3.1-jre.pom, pas preuve de défaut FTS/SAF |
| UNIFIED40214722427 | FAILURE, liste jobs vide ; cause précise UNKNOWN, endpoint de diagnostic détaillé indisponible. Aucun échec YAML inventé |
| LLM40214822563 | SUCCESS au378a1cf (code parent de e0b7814), pas JNI/CORE qualifié |
| INTERFACE40214158554 | SUCCESS au1d0a89f, pas transport CORE ou rig validé |
| EVOLUTION39996031251 | SUCCESS JVM au c018018 ; sandbox d'exécution reste non qualifiée |

Ces CI ne sont pas réparées dans VOICE, leurs propriétaires en gardent la responsabilité.
Les rapports de port NEX-06A/SERV-04A, CORE-VOICE, LLM FUSION et INTERFACE/EVOLUTION ont
été relus. SQLite FTS5 réel API24, fence révocation/commit et parcours SAF commun restent
des gates des producteurs ; VOICE n'est **pas** un prérequis artificiel du pilote principal.

## Mesures réelles Linux

[PUBLIC-RENDER.json](evidence/PUBLIC-RENDER.json) : Alba publique CC BY4.0 déjà présente,
checkpoint FP32 exact conservé, graine42/LSD3/temp0,3/CPU1. Neuf documents,30 segments,
3répétitions par corpus, texte conservé, EOS réel de chaque segment. Aucun audio publié.

| Corpus | FirstPCM p50/p95 ms | Total p50/p95 ms | RTF p50/p95 | PSS après p50/p95 Kio |
|---|---:|---:|---:|---:|
| Train,1segment |1584,70 /1715,98|3588,87 /3724,03|1,869 /1,940|1015333 /1198409|
| Nombres,1segment |1507,36 /1651,45|7961,47 /7994,70|1,422 /1,428|1601037 /1680029|
| Document,8segments |1440,97 /1575,32|46361,99 /46439,35|1,541 /1,544|1952661 /2765953|

CPU processus p50/p95 :3670/4100ms,8150/8170ms,46530/46660ms. Document long =30,08s PCM,
pas plusieurs minutes. FirstPCM inclut ouverture/vérification et calcul mais exclut
encodage public2430,90ms/résolution/planification préalables. FirstWrite et audition=null,
thermique/énergie=null sur Linux. PSS après document n'est pas pic : p95~2,64Gio sur long,
risque mémoire maintenu ; aucun seuil de protection réduit. Quantiles n=3 exploratoires,
concurrence d'autres JVM possible durant cette passe. **Aucun gain de latence revendiqué.**

Le moteur préexistant réutilise le tokenizer léger, ouvre/ferme le modèle par segment et
conserve un thread. Garder le différé sûr : RTF>1 et coûts de calcul/ouverture ne justifient
pas de promettre un flux continu. La cause exacte des >30s sur téléphone nécessite les
compteurs physiques, pas une extrapolation Cloud. INT8/threads supplémentaires restent
non qualifiés, aucune optimisation numérique nouvelle activée.

## Tests, sécurité et prochaine action

[Commandes](TESTS.md), [résultats JVM](evidence/LOCAL-JVM.json), [validation](VALIDATION.json).
Passe locale forcée au code1094c74 :260cas,237réussis,23sautés,0échec/erreur,
worktree propre et99tâches réellement réexécutées. Dont16nouveaux JVM et3nouveaux Robolectric.
Le test natif nouveau est sauté dans cette passe sans variables de pack ; il a réellement
réussi dans la [campagne native séparée](evidence/LOCAL-NATIVE-CAMPAIGN.json), source
a41d82a :260cas,238réussis,22sauts,0échec. Le passage ultérieur de l'étiquette d'horloge
à une énumération et l'ajout de timestamps Android/port STT ne changent pas l'arithmétique
vocale. Aucun résultat acoustique supplémentaire déduit de ces changements. Les22autres
sauts requièrent leurs modèles/opt-in propres ; ils restent des non-exécutions explicites.
Compilation finale app/test x86_64 et Lint réussis :0erreur/fatal,28avertissements
conservés ; [CI40288377740](https://github.com/bubth720/JARVIS-VOICE/actions/runs/40288377740)
**SUCCESS au1094c74** : unité, audio26, audio35 et build tous réussis.
Pas de /dev/kvm dans ce Cloud. Les8régressions Android et3nouveaux tests ont **réellement réussi,11/11 sur API26 et11/11
sur API35** : signal synthétique avec le vrai lecteur, pas modèle personnel ni écoute humaine.

[Pack privé conservé](evidence/PRIVATE-PACK.json) : ZIP234932686octets,
SHA9436555c6965e3480c06f51fedced0f512a8316e482a7e54df0f4ee83d0a01cd ;10hashes extraits
conformes. Aucun nouveau modèle, HF_TOKEN, URL signée, enregistrement ou portrait traité
ou publié. Notices existantes relues : ORT/code MIT, checkpoint/référence CC BY4.0 avec
restrictions d'usage/gated conservées. Pas de clearance globale de redistribution déduite.
Les artefacts de cette mission sont rapports seulement ; pas de distribution APK/modèle
sans décision distincte prévue par la mission directrice§9.3.

Restent BLOCKED : vraie autorité native CORE (handles, arrêt/drainage, unload attesté et
baux crash-safe), transport de génération authentifié qualifié, STT CORE et connexion avatar,
émotions acoustiques/humaines, POCO/Redmi, qualificatif ANDROID_MULTI_MODULE_GO.
Aucune installation personnelle, migration, connexion sensible ou fusion main.
Prochaine action : revue du protocole autonome et des preuves CI ; mission native distincte
CORE/VOICE avant tout raccordement. Conserver le pilote indisponible jusqu'à ces preuves.


## Clôture de la tranche autonome

[Preuve CI](evidence/CI.json), [tous les workflows au SHA](evidence/WORKFLOW-STATUS.json).
La campagne FUSION a tourné du26octobre2026 07:51:34 au08:00:39UTC. Les marqueurs
Android des logs décodés sont émis seulement après assertion du fichier instrumentation
`OK (11 tests)` sans FAILURES/shortMsg. Les traces synthétiques FirstPCM/Write/Playing
sont dans l'artefact Android ; pas de mesure de première parole humaine ni de TTS Android.
Les ZIP d'artefacts n'ont pas été reparsés indépendamment dans le workspace : le connecteur
retourne des références de fichier et la récupération directe échoue URLError. Cette
limite n'annule pas les statuts/steps/logs réellement lus ; aucune métrique de ces ZIP
non lus n'est recopiée comme mesure vérifiée. Aucune URL temporaire publiée dans le dépôt.

Build de contrôle du Studio existant : ARM64,34320499octets, min26/target35,
SHA16d2be0cba534032e69e74f035ee0b14aa11e773deb6bcb7e305af86bd40e703.
Aucune permission INTERNET, aucun poids inclus, signature et alignement16K vérifiés.
Librairies natives aux empreintes qualifiées, inchangées. Certificat CI éphémère,
aucune clé historique changée ; ce build n'est pas une mise à jour signée pour le téléphone.
La CI dédiée ne distribue que les rapports, pas l'APK. Aucun téléchargement/installation
utilisateur demandé. Les avertissements Lint préexistants restent28,0erreur/fatal local.

Les autres workflows ne sont **pas tous verts** : FINAL-01, BinderV1, SDK,005K et les
anciennes missions conservent leurs gardes de portée/baselines. Celle de FINAL-01 refuse
notamment l'ajout autorisé d'une dépendance de test dans voiceBridgePilot/build.gradle.kts.
La nouvelle garde compare564fichiers à fe57452 et autorise seulement les trois ajouts
exacts de configuration/test ; toutes les sources runtime et contrats historiques sont
identiques. Ne pas supprimer les anciennes assertions pour présenter un vert global.

GO de cette tranche = protocole autonome exécuté sur JVM/émulateur, pas CORE↔VOICE.
Mission FUSION-02 globale **non terminée**, lane VOICE PARTIAL/BLOCKED pour intégration,
émotions/physique. Rapport et commits publiés dans la PR#23 toujours brouillon ; aucune
fusion main, migration, donnée vocale personnelle ou activation sensible.


Régression ancienne distincte : Pocket Probe40288377628 échoue réellement dans
`testThreeSecondsOfContinuousDeferredAudioHasNoUnderruns` sur émulateur API35 :
underrun1 au lieu de0,72000frames soumises/observées. Même type d'échec déjà conservé
au gel précédent ; sa cause physique n'est pas démontrée ici. Il n'est pas classé comme
un simple problème de garde ni comme une lecture parfaite. La source005D est inchangée :
un correctif de cette application préservée demande une mission séparée. Les jobs crypto/
playback005H/005I/005J sont réussis, leurs jobs build refusent la préservation historique.
[Diagnostic exact des jobs et extrait Pocket](evidence/LEGACY-DIAGNOSTICS.json).


## Recontrôle final des nouveaux candidats voisins

[Références distantes recontrôlées](evidence/FINAL-REMOTE-HEADS.json),
[CI exactes des nouveaux candidats](evidence/FINAL-SIBLING-CI.json). Les huit branches
primaires restent identiques, mais de nouvelles branches FUSION-02 sont publiées.
Elles sont **lues, non consommées et non intégrées** dans VOICE :

- UNIFIED6de7386 :167 tests rapportés, verrouv2/gates, pas de pilote Android.
  CI40287691712 SUCCESS au SHA exact. Le rapport/CI_DIAGNOSIS du propriétaire explique
  désormais l'ancien échec40214722427 : scalaire YAML ligne48 non protégé autour de
  --only-binary=:all:, ScannerError reproduit puis quotes corrigées. Notre première
  inspection sans annotation restait UNKNOWN ; cette mise à jour cite la preuve du
  propriétaire, sans prétendre avoir reproduit son parseur dans VOICE.
- COREf6a3b2d : C0 passif seulement,599 cas rapportés ; CI40287287330 SUCCESS exacte.
  C1–C4 restent BLOCKED, LocalTts/native/VOICE non raccordés. Les baux/unload nécessaires
  au pont vocal ne sont pas apportés par cette tranche de disponibilité documentaire.
- SERVICES51da6fa : proposition compilable de commit/fence et fixtures SAF sous
  androidTest présentes, aucun raccord runtime ; SAF_EXPORT_UNQUALIFIED conservé.
  REPORT.md et VALIDATION.json non livrés à ce SHA ; ne pas conclure à une qualification.
  CI40287588184 FAILURE, distincte du run historique f00091. Les fixtures nouvelles ne
  prouvent ni l'autorité réelle CORE ni une révocation OS inter-UID.
- LLMf147236 : rapport303 JUnit/11 Python et revue native inerte ; JNI/unload/CORE
  toujours BLOCKED. Aucun run au SHA documentaire exact ; succès au parent34491ee
  rapporté séparément, pas hérité implicitement.
- INTERFACE3c6921e : façade locale/laboratoire et client arrêt borné ; CI40288572227
  SUCCESS exacte. Aucun transport CORE signé ni rig/lip-sync qualifié. Les73 tests
  Android annoncés sont sa campagne API26, pas des tests relancés par VOICE.

Ces constats actualisent l'audit historique sans effacer ses anciens échecs. NEXUS
n'a pas de nouvelle branche FUSION-02 détectée ; le candidat9d6928 v4/port est connu,
mais ses preuves Android24/25 FTS5 ne sont toujours pas qualifiées par les documents
lus. Les progrès des lanes parallèles ne donnent aucun ANDROID_MULTI_MODULE_GO et
ne débloquent pas la génération inter-APK ou les émotions. Aucun code frère touché.


Précision nouvelle CI SERVICES : JVM et build Android réussissent cette fois ; les
quatre jobs du nouveau SDK instrumenté24/25/26/35 échouent à l'étape « Real SDK ports
and SAF laboratory ». Au moins API24 expose des assertions `SAF selection` dans les
nouveaux scénarios alors que les fichiers synthétiques sont visibles dans DocumentsUI.
C'est un échec de qualification du parcours de sélection, pas une preuve positive
CSV/fence ni la même erreur de dépendance guava-parent historique. La cause interne
exacte de sélection n'est pas établie dans VOICE ; correction chez SERVICES uniquement.

Sur API35, l'étape échoue pendant la préparation : services Android `Broken pipe`,
adb exit224 avant preuve des scénarios. Ne pas lui attribuer les assertions API24.
[Extraits exacts et steps](evidence/SERVICES-NEW-CI-DIAGNOSIS.json).
