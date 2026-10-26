# FUSION-02 — VOICE : tranche autonome, intégration bloquée

Module `bubth720/JARVIS-VOICE`, issue[#22](https://github.com/bubth720/JARVIS-VOICE/issues/22),
mission directrice [UNIFIED#5](https://github.com/bubth720/JARVIS-UNIFIED/issues/5).
Branche `feat/fusion-02-voice-timeline`, base exacte
`fe57452fcb33f5b6870070f30ba6c5f1d349c4ba`. HEAD = commit contenant le présent rapport ;
le SHA de code et la CI sont précisés dans VALIDATION.json après publication.
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
frère modifié. La proposition est additif/inert : le Studio conserve sa chronologie v1
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
Passe locale forcée :260cas,238réussis,22sautés,0échec/erreur. Dont16nouveaux JVM,3nouveaux
Robolectric et le benchmark natif réellement exécuté. Les22sauts historiques nécessitent
leurs modèles/opt-in spécifiques ; ils ne deviennent pas des succès par le présent benchmark.
Compilation Android de la fixture effectuée ; exécution émulateur/CI à vérifier séparément.
Pas de /dev/kvm dans ce Cloud. Les8régressions Android et3nouveaux tests sont préparés pour
API26/35 : signal synthétique avec le vrai lecteur, pas modèle personnel ni écoute humaine.

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
