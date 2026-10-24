# VOICE-04 — prototype livré, mission PARTIELLE

Branche `feat/voice-04-speed-longform-emotions`, base `feat/voice-02-intensive` `3ffb28011506abcc94b5d4ee71d6c79fc278333b`, mission préparée `4f47eb3`. [PR13 brouillon](https://github.com/bubth720/JARVIS-VOICE/pull/13), aucune fusion/install automatique. La mission entière ne peut être déclarée terminée sans mesures physiques du premier son, complétude sur téléphone et émotion reconnue humainement.

## Livraisons par phase

- **A — implémentée/testée logiciel et ONNX public** : traces locales expurgées, quatre ouvertures et intégrité distinctes, premier PCM/écriture/présentation estimée/EOS/fin/fermeture, statistiques PCM et compteurs mémoire/thermique/batterie, export volontaire JSON et UI Diagnostic. Refus/annulation n'inventent pas de temps audio ; valeurs absentes=null.
- **B — prototype conditionnel** : ring4s, prélecture1/2s (UI2s), backpressure et bornes20s/job, PCM exact, focus/arrêt/nettoyage, repli différé avant lecture si débit insuffisant. OFF par défaut. API26–30 exclues du STREAM après underrun réellement observé ; aucun retrait de la garde. Le natif Linux est resté différé dans les six jobs. Aucun gain Xiaomi revendiqué.
- **C — implémentée/testée** : contrat interne versionné, tokenizer officiel50tokens/job, plan lossless<=six jobs, ambiguïtés nécessitant des coupes manuelles, invalidation des éditions, admission/fermeture entre jobs et reprise explicite sans rejouer les jobs terminés.178tokens naturels générés en six jobs Linux publics. Pas de cache PCM de narration entière ni de modèle résident.
- **D — capacités honnêtes et étude livrées, effet acoustique bloqué** : intentions versionnées, refus des émotions/intensités sans capacité, boutons désactivés, matrice et étude Qwen3-TTS/Chatterbox officielles. Aucun filtre, nouvelle voix/modèle, instruction audible ou émotion fictive. Aucune écoute humaine ni reconnaissance d'émotion obtenue.

Commits : `b474445` A ; `423b4dd` B ; `3aa089e` C ; `fbab1bf` D/gates ; `ef8c9df` qualification/corrections ciblées. Correctifs ultérieurs : `d2d3c13` conserve l’historique du compteur dans drain, `b4a98df` refuse les collages trop longs en entier ; la qualification finale réussie porte sur **b4a98dfe433361470bdc764e2e45b385b5365d82**. La clôture documentaire suit sans changer le runtime testé.

## Tests et preuves disponibles

[Reçu JVM local](proofs/LOCAL-JVM.json) :194 déclarés,175 réussis,19 sautés,0 échec/erreur. Dont VOICE-04 :30 déclarés,27 réussis,3 tests publics optionnels sautés dans cette passe sans variables de fixture. Ces **trois tests publics ont ensuite été exécutés et réussis séparément** avec le pack existant et Alba publique ; aucun saut déclaré comme réussite. Les16 autres sauts sont historiques, fixtures natives non activées pour ces suites, pas une assertion supprimée.

| Suite JVM | Réussis | Sautés | Source |
|---|---:|---:|---|
|VOICE-04|27|3|Nouvelle exécution logicielle ; tests publics séparés3/3|
|SDK VOICE-01/02|29|0|Réexécutée|
|studioRuntime|9|1|Réexécutée|
|expressionRuntime|19|1|Réexécutée|
|identityRuntime|18|1|Réexécutée|
|pocketRuntime|33|13|Réexécutée|
|contrats005K|29|0|XML disponible, Gradle UP-TO-DATE local ; CI fraîche séparée|
|harness005K|3|0|Réexécutée|
|app|8|0|XML disponible, Gradle UP-TO-DATE local ; CI fraîche séparée|

Pipeline :101 cycles synthétiques avec copie Float32 bit-identique, ordre/bornes, fermeture du consommateur, producteur rapide/lent, annulation pendant backpressure, exception mi-flux, EOS absent, PCM invalide, dépassement de budget, fermeture inconnue et appareil exclu. **Ce stress n'est pas101 cycles ONNX/AudioTrack ni une preuve de fuite native absente.** Tokenisation exacte50/51/100/150/200tokens [reçu](proofs/TOKENIZATION.json), textes de comptage distincts de la qualité.

ONNX public réel : [parité LSD1/3/5](proofs/PARITY.json), zéro écart PCM au moteur original ; [six jobs naturels178tokens](proofs/PUBLIC-LONG.json), EOS fiables, hashes répétés identiques sur deux explorations, scopes de fermeture retournés. CPU/PSS Linux et limites dans [PERFORMANCE.md](PERFORMANCE.md), pas d'AudioTrack/thermique mobile/écoute. Le pack n'a pas été reconstruit, aucun B personnel utilisé.

Build ARM64, APK tests et Lint local réussis (0 erreur,17 avertissements). Audit signature/ID distinct/min26/target35/alignement16K/empreintes JNI/lockFP32/pas de poids ou permission réseau réussi. L'audit historique refuse le nouvel applicationId par sa whitelist ; **copie d'audit VOICE-04 distincte**, mêmes assertions/hashes sans modifier l'ancien script.

Première CI `423b4dd`, run[40037284377](https://github.com/bubth720/JARVIS-VOICE/actions/runs/40037284377) : JVM réussie ; API26=5/6,1échecUNDERRUN_STREAM au drain ; API35=3/6,3échecs focus/setup hors premier plan. Corrections : garde API26–30 avant création de track, Activity réellement visible pour les tests35. Aucune assertion retirée, ancien défaut005D non masqué. Les erreurs de compilation de mise au point (dépendance ORT manquante, exclusions Kotlin/sources dupliquées, reconstruction du runtime requise après extension du constructeur) ont été corrigées ; les builds réussis ultérieurs sont ceux cités.

La passe `ef8c9df`, run[40039077703](https://github.com/bubth720/JARVIS-VOICE/actions/runs/40039077703), réussit11/11 surAPI26 (repli différé) et10/11 surAPI35. ÉchecAPI35 : COMPTEUR_STAGNANT. Cause de code trouvée : finish() écrasait le précédent head avant health(), donc la progression réelle ne rafraîchissait plus lastProgress. Correctifd2d3c13 : health() conserve seul l’historique, seuil2s/assertions inchangés. La non-régression est le test matériel de flux complet déjà en échec, conservé.

Autre correctif : InputFilter.LengthFilter pouvait tronquer un collage ; VOICE-04 rejette l'édition entière, conserve la sélection remplacée et affiche le refus. Un test Android vérifie cette propriété. La qualification finale comprend12 scénarios/API, pas uniquement AudioTrack : identité hors ligne, entrée, lifecycle et gardes négatives incluses. **12/12 réussis,0saut/échec surAPI26 et12/12 surAPI35**, [run40080095168](https://github.com/bubth720/JARVIS-VOICE/actions/runs/40080095168) terminé avec succès : JVM, deux émulateurs, buildARM64/Lint et auditAPK. Le STREAM complet est réellement testé sur35 ; sur26 la politique refuse STREAM et teste le différé. Les cas négatifs et la vérification d'identité ne sont pas tous des lectures matérielles. [Preuves CI et livraison](proofs/CI-DELIVERY.json).

Pilote Binder : [17/17 simulations Robolectric réexécutées](proofs/PILOT-JVM.json),0saut/échec. Ni un test Binder noyau de cette mission, ni une preuve de ressources natives ; aucun pont activé.

Les gardes historiques005K/pilote/styles et certaines gardes de build ancien refusent la portée déjà étendue par VOICE-01/02 et la seule extension de visibilité de CancellationToken. Elles restent inchangées/rouges ; elles ne sont pas contournées ni annoncées comme réussies. Notre garde dédiée vérifie au byte près toutes anciennes apps, livraisons, coffres, contrats/moteurs et l'unique différence de visibilité autorisée. Les résultats ci-dessous proviennent des conclusions de jobs et journaux décodés, pas d’une compilation seule.

## Régressions Android au code qualifié

| Application | Tests Android | Run / job instrumenté |
|---|---|---|
|F|10/10 réussis|40080095199 /120831672108|
|G|14/14 réussis|40080095180 /120831672240|
|H|18/18 réussis|40080095202 /120831686348|
|I|31/31 réussis|40080095167 /120831886057|
|J|24/24 réussis|40080095225 /120831684750|
|D|4/5 réussis,1échec historique|40080095200 /120831672181|

D : `DeviceAudioTests.testThreeSecondsOfContinuousDeferredAudioHasNoUnderruns`, `AudioTrack count=1 frames=72000 submitted=72000, expected0 but1`. Assertion inchangée ; échec conservé. Build Android principal réussi40080095231. Les jobs **build-and-audit H/I/J** et workflows005K/pilote/styles restent en échec sur leurs anciennes gardes de portée (source déjà étendue par VOICE-01/02, et ici visibilité de withOptions) ; leurs jobs Android réussis ne rendent pas ces workflows globalement verts. Pas de garde ni assertion historique modifiée.

## APK indépendante disponible

[Télécharger le ZIP de livraison GitHub Actions](https://github.com/bubth720/JARVIS-VOICE/actions/runs/40080095168/artifacts/12554654424), compteGitHub autorisé requis. Depuis Android, extraire le fichier nommé **voice04Studio-debug.apk** ; aucune connexionPC/USB nécessaire. Le ZIP contient aussi l'audit et les rapports Lint. Expiration annoncée23novembre2026 ; source/tests restent dans laPR.

- APK : **34 222 109 octets**, SHA256 **ff5f37ef657dc0abc25bf678a48bc69cb3f26126c96facd785b18b61492be150** (hash de l'APK, calculé dans le jobCI et lu dans son journal).
- ZIP artefact :15 887 567 octets, SHA256 annoncéGitHub **32001e09248ba05648db857dfd419c715fc4dad42d3b9fd5f29ea3c4b6411da6** ; distinct du hashAPK.
- Signature laboratoireCI : certificatSHA256 `a491f592fadd797c2fff5ed4466ae069f860204cb1e07f6e9f028123cd77dd4a`, différente de005J. Aucune promesse de mise à jour avec clé stable. Ce n'est pas une APK de remplacement005J.
- Signature valide, ARM64/API26/target35, alignement16K, mêmes hashesJNI/lockFP32, ni modèle/profil/enregistrement ni permissionINTERNET/micro. Les licences/notices des composants existants sont conservées ; aucun poids gated redistribué.

Les archivesCI n'ont pas été indépendamment téléchargées/reparsées dans le runtimeCloud. Les conclusions, assertions, audit JSON imprimé et métadonnées d'artefact sont vérifiés via le connecteurGitHub ; aucune URL signée n'est publiée. [Protocole physique volontaire](TEST-XIAOMI.md).

| Niveau | Statut réel |
|---|---|
|Code écrit|A/B/C et gardesD ; émotions non activées|
|JVM|175 succès/19 sauts dans194 déclarés ;17 simulations pilote séparées réussies|
|Android émulateur|12/12 parAPI26/35 ; anciennes suites et défautD ci-dessus|
|ONNX public réel Linux|3 tests optionnels réussis séparément ; PCM parité et6 jobs178tokens|
|Essai physique B Xiaomi|**Non exécuté** ; gain de vitesse, qualité, énergie et émotions non validés|

## Confidentialité et limites

Identifiant neuf `com.jarvis.voice.studio.voice04`, stockage séparé.005J et son archive signée restent inchangés ; B n'est ni lu dans le Cloud, ni déplacé, réencodé ou supprimé. Aucune migration automatique. [Préservation vérifiée](proofs/PRESERVATION.json) : ZIPFP32 original234932686octets/SHA9436555c… et archive005J SHA82db41ca… inchangés. ArchiveJVIENC01/profilJVFP1, paramètres/poids,18états, bibliothèques natives et signatures historiques conservés. Un thread ONNX ; admissions~2Gio, surveillance250ms, arrêt mémoire/thermique inchangés. Aucun réseau, upload de journal/texte/audio, pont CORE actif ou cache inter-tours.

La clé de l'APK laboratoire n'est pas la clé de005J ; **ne pas remplacer/désinstaller005J**. Aucun fichier gated/personnel n'est livré. Une qualification propriétaire requiert accord distinct et import volontaire de copies locales, voir[TEST-XIAOMI.md](TEST-XIAOMI.md). Les poids alternatifs ne sont pas téléchargés ; licences/métadonnées vérifiées sans prouver leur inférence Android. Aucun résultat acoustique/énergie/Xiaomi inventé.
