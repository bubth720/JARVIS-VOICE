# VOICE-FINAL-01 — livraison expérimentale, mission PARTIELLE

Branche `feat/voice-final-01`, base auditée **1a9f713dff28f6e4fb145728815e9ed118f5a929**, VOICE-05. Aucune fusion, installation personnelle ou connexion CORE/INTERFACE. Les anciennes branches/APK et leurs clés ne sont pas modifiées. L'application indépendante utilise **com.jarvis.voice.studio.final01**, minSdk26/target35, ARM64 ; aucune permission réseau ou microphone.

## État des lots

| Lot | Réalisé et vérifiable | Limite / statut |
|---|---|---|
| A | Profilage séparé tokenisation, encodeur texte, FlowLM, Flow sampler, Mimi/PCM, ouverture/vérification, résolution du profil, transfert/drain audio ; tokenizer léger réutilisé avec SHA contrôlé ; un thread ONNX ; benchmark public répété | Pas d'accélération nette démontrée. Première restitution audible POCO, énergie et thermique physiques non mesurés. Pas de quantification activée |
| B | Jusqu'à 200000 unités UTF16 /4000 segments, <=300 caractères et <=50 tokens par segment ; texte source conservé sans perte, aperçu des coupures ; lecture, pause de frontière, reprise et Arrêt ; interruption ambiguë exige décision explicite | Les plusieurs minutes réelles de parole ne sont pas qualifiées. Aucune continuité annoncée si le producteur est trop lent ; coupure au mot parfois nécessaire et signalée |
| C | Matrice de toutes les intentions demandées ; seul NEUTRAL accepté ; autres réponses UNSUPPORTED ; alternatives étudiées dans les preuves antérieures | **BLOQUÉ pour émotions acoustiques validées**. Aucune émotion obtenue/écoutée, pas d'effet d'énergie présenté comme émotion |
| D | Coffre 005H/I/J réutilisé : plusieurs imports MP3 d'une même identité, un par un avec revue/consentement ; profils individuels versionnés, sélection/suppression et archives chiffrées ; nouvelle analyse comparative des références conservées (RMS, silence, clipping, F0 estimée) | Mesures descriptives, pas de preuve biométrique ou de reconnaissance du locuteur. Aucun profil multiréférence fusionné : les 18 caches ne sont pas moyennés. B préservé sans réencodage destructeur |
| E | Lanceur indépendant : texte saisi/import UTF8 local strict, identité via coffre, sampler1/2/3/5, températures officielles0,1–0,6, ÉCO/ÉQUILIBRÉ/AUTO, lecture/réécoute A/B, WAV volontaire, effacement dérivés et compteurs ; restauration des paramètres précédents (graine, sampler, température, intentions/pauses héritées et mode ressources) | UI de laboratoire, pas une certification finale de naturel. A/B exige un seul segment et le même contexte ; WAV exporté IEEE float32, cache privé borné |
| F | SDK JVM séparé sans Android/ONNX, événements typés et contrôle local pause/stop ; schéma versionné ; aucun texte/voix/PCM/UUID dans les événements | FIRST_AUDIO = preuve de présentation estimée par AudioTrack, **pas son humain entendu**. Pas d'alignement phonétique, d'ancre avatar inter-apps ou de connexion CORE |

## Code et préservation

- `voiceFinalSdk` : `VoiceEvent`, `VoiceEventType`, `LocalVoiceControl`, `voice.final01.events/1`. COMPLETE peut inclure des omissions choisies, toujours comptées séparément : `completed` signifie réellement présenté avec succès, `skipped` n'est jamais un succès de parole.
- `voiceFinalRuntime` : planificateur, session bornée, cache privé A/B, quantiles, politiques de ressources, analyse de références. Le moteur instrumenté est **généré depuis l'original qualifié**, avec remplacements bornés/assertés. Arithmétique ONNX, caches, modèle, BOS, sampler et poids inchangés.
- `voiceFinalStudio` : réutilise les sources du studio et du lecteur qualifiés ; aucun fichier ancien modifié. Le contrôleur généré ajoute uniquement une garde conservatrice UNKNOWN et l'effacement des deux réécoutes dérivées lors des actions de suppression/activation du coffre.
- `scripts/voice-final-01/check_scope.py` protège tous les anciens fichiers suivis. Seul ajout dans la configuration existante : trois nouveaux modules. Ni JNI, permissions/signatures historiques, contrats ni archives modifiés.

Le modèle lourd est ouvert/fermé par segment avant la lecture différée. Le retour de close n'est pas une preuve de mémoire native rendue à l'OS. Tout état de fermeture inconnu bloque de nouveaux modèles dans cette application. Les gardes mémoire/thermiques antérieures restent intactes. API26–30 n'est pas forcée en STREAM. Le lanceur FINAL utilise le différé ; le prototype progressif qualifié reste accessible dans les réglages hérités, avec ses gardes inchangées.

Les trois profils de ressources conservent un thread ONNX : ÉCO ajoute1000ms entre segments, ÉQUILIBRÉ n'ajoute pas de repos, AUTO choisit selon batterie/thermique observables. Batterie<10% et thermique>=3 bloquent ; état thermique indisponible reste inconnu. Aucun calcul lourd concurrent, reset au timeout ou nouvelle session après UNKNOWN.

## Benchmark réel, pas une mesure Xiaomi

[Résultats bruts publics](proofs/PUBLIC-BENCHMARK.json) : pack officiel FP32 **déjà présent**, Alba publique CC BY4.0 SHA46264e83cb99115c3d210260e029117566d9c64f20266d10daa78107759ede3e. Encodeur officiel de référence exécuté en1875,87ms (une autre création publique pour le test d’annulation est séparée). Deux phrases françaises (train et nombres), cinq répétitions chacune, deux chemins complets alternés AB/BA, graine42/LSD3/temp0,3/CPU1 : **20 générations réellement exécutées**, 10 par chemin. Pas de téléchargement/reconstruction de poids ni d'enregistrement personnel.

| Mesure Linux | VOICE-05 p50 /p95 | FINAL-01 p50 /p95 |
|---|---:|---:|
|Ouverture/vérification modèle, ms|935.45 /953.79|930.24 /969.27|
|Premier PCM, chargement compris, ms|1533.89 /1649.41|1529.57 /1625.44|
|Total chargement+génération/mesures, ms|4008.13 /8311.30|3653.26 /8244.35|
|CPU processus, ms|4380.00 /8380.00|3980.00 /8260.00|
|RTF (plus petit = plus rapide)|1.298 /1.583|1.294 /1.386|
|PSS **après** job, Kio|1482136.00 /2328667.00|1539495.00 /2410783.00|

Quantiles nearest-rank, n=10 par chemin, corpus étroit de deux phrases, JIT/GC/cache OS non neutralisés. P95 exploratoire, pas une garantie statistique. PSS après job n'est pas un pic ; aucune baisse mémoire ou consommation démontrée. Aucun chiffre d'énergie ou thermique Linux inventé. Les 10 paires de PCM sont identiques **bit à bit** ; cela prouve l'absence de changement pour ces fixtures, pas une qualité humaine validée. Aucun audio publié.

**Pas d'accélération nette.** FlowLM et le décodeur PCM dominent le calcul ; l'encodeur texte et la tokenisation sont très courts sur ce corpus. Le tokenizer réutilisé évite une reconstruction légère ; il ne résout pas la production autoregressive. RTF>1 explique l'impossibilité de promettre un flux continu ici. Le différé attend la totalité d'un segment, puis les ouvertures/calculs se répètent : ce sont des sources de délai mesurées sur Linux. La cause précise des >30s sur POCO demeure à profiler sur l'appareil : elle n'est pas déduite d'une extrapolation Cloud.

Planification officielle supplémentaire : **4600 mots,18600 unités,400 segments**, texte brut reconstruit exactement, chacun<=50tokens ;620.57ms Linux. Ce document n'a pas été synthétisé en entier. Les tests logiques sur milliers de mots ne valent pas plusieurs minutes d'écoute.

Sur Android, les compteurs enregistrent pression→premier PCM et pression→présentation estimée (incluant admission/ouverture), les durées par étape et par segment, PSS échantillonné250ms, CPU processus, batterie/thermique disponibles et p50/p95. Les nombres de valeurs disponibles sont fournis ; absence reste null, pas zéro. L'énergie Android est un compteur grossier, non certifié. Le protocole POCO reste à exécuter après autorisation distincte d'installation.

## Qualité, identité et alternatives

Aucune modification artificielle du signal : cache IEEE FLOAT32 bit exact avant lecture, borné à deux fichiers de20s maximum (~3,84Mo de données), liés au profil/conditionnement/texte/graine/sampler. Réécoute B en FLOAT32 ou PCM16 pour isoler la conversion ; aucune de ces écoutes n'a été effectuée humainement ici. Une erreur audio conserve le cache ; corruption ou contexte modifié interdit la réécoute. Export seulement volontaire via SAF local ; aucune exportation automatique des références.

Imports, suppression, versionnement et archives passent par le coffre existant. Plusieurs MP3 se révisent individuellement ; aucune affirmation « même locuteur » n'est déduite d'un F0 ou RMS ressemblant. Une F0 d'autocorrélation sur40ms peut comporter des erreurs d'octave et dépend du bruit ; elle ne préserve ni ne garantit seule le timbre. Fusionner les caches transformer serait une hypothèse non qualifiée. Le profil B de l'ancienne application n'est pas copié automatiquement ; une restauration locale volontaire de son archive conserve ses octets.

[Étude officielle antérieure Pocket24L/Chatterbox](../mission005g/MOTEURS-SUPERIEURS.md) et [Qwen/Chatterbox](../mission-voice-04/EMOTIONS.md) intégralement relues. Ces constats documentaires sont datés dans leurs preuves, pas une nouvelle qualification de licence ou Android : Pocket24L checkpoint672178676octets/72états et conditionnement incompatible avec18caches ; ChatterboxV3 environ3,206Go de fichiers, MIT selon relevé ; Qwen Base+codec2,512Go/4,54Go selon variante, Apache2 selon relevé. Fichiers≠PSS ; aucun budget CPU/thermique Android démontré. Aucune intégration, conversion, poids alternatif ou port JNI nouveau. Les interfaces d'adaptateurs existantes sont conservées ; tout moteur futur doit avoir son profil/pack lié exactement, licence, tests de parité et budgets propres. B n'est pas un format vocal universel.

Quantification et threads : pack INT8 Alba incompatible explicitement exclu ; aucun mélange FP32/INT8. Une quantification dynamique ou un backend optimisé impose parité composants/caches/BOS puis évaluation acoustique, pas seulement un gain de taille. Les options ONNX/JNI qualifiées restent inchangées ; aucune variante thread2/4 ou modèle quantifié ne se présente comme validée. Ce travail est une préparation mesurée, pas un moteur final interchangeable déjà qualifié avec plusieurs backends.

## Tests et écoute

Tests JVM, builds et lint locaux exécutés ; les comptes finaux et essais API26/35 seront annexés après CI. Le benchmark natif public est exécuté séparément ; il est volontairement sauté en CI sans modèle. Les anciens tests et seuils restent inchangés, notamment l'échec AudioTrack005D historique documenté dans VOICE04 ; aucune affirmation que toutes les gardes historiques de portée sont vertes.

Évaluation humaine en aveugle : **n=0, NOT_RUN**. Aucun score prérempli de naturel, timbre ou émotion. Joie, surprise, tristesse, empathie, colère, etc. restent UNSUPPORTED ; on ne peut pas organiser une étude de reconnaissance sur des émotions que le moteur ne produit pas de manière qualifiée. Voir [protocole](TEST-POCO.md), limites et conditions de clôture.

## Clôture

**Mission non terminée.** Lots A/B/D/E/F livrés à des degrés expérimentaux vérifiables ; C bloqué. Cible première parole perceptible<5s sur POCO, plusieurs minutes sans coupures anormales, émotion audible et identité perceptive stable **non atteintes ou non vérifiées**. Aucune installation personnelle ou connexion intermodule effectuée. Prochaine action : revue de cette APK, puis autorisation distincte des essais physiques, relevé des compteurs sur POCO et écoute aveugle. Une étude d'un moteur plus expressif exige aussi accord de téléchargement/qualification séparé et maintien de B intact.

Portée temporelle : les mesures pression→audio partent du bouton Lire, pas du bouton Préparer séparé. `prepareTotalMs` mesure séparément cette préparation. Les quantiles Android et jobs concernent la tentative courante (`metricsCoverThisRunOnly`), pas les tentatives précédentes ou une étude contrôlée répétée. La progression de session reste conservée en RAM lors d’une pause/reprise.

## Limite mémoire relevée dans la qualification native

Le dernier benchmark, au code78c674b, comporte20 générations complètes et une annulation native supplémentaire à la première sortiePCM. Les traces du benchmark sont finalisées COMPLETED ; l’annulation est CANCELLED avec CLOSE_RETURNED observé et sans EOS/lecture ([preuve](proofs/NATIVE-CANCEL.json)). Les deux tests natifs sont réellement exécutés séparément, pas déduits des sauts CI.

La valeur PSS après job de FINAL01 atteint un p95 d’environ2,30Gio dans cette passe Linux ; VOICE05 dépasse aussi2Gio. Ce n’est pas un pic Android et cela varie avec le processus, le GC et les allocateurs. Aucun gain mémoire démontré : risque de refus d’admission, échauffement ou arrêt par manque de mémoire sur longues narrations. Les gardes2Gio disponibles au départ de chaque segment et256Mio en cours ne sont pas diminuées ; elles ne constituent pas une preuve d’absence d’OOM. Pas de reset, GC forcé ou mémoire déclarée libérée pour contourner le problème. Ce risque maintient A et la qualification physique longue de B PARTIAL.

Revue UI avant livraison : les sélecteurs utilisent les plages officielles communes au runtime. Tout changement de mode, émotion ou sampler après préparation exige une nouvelle préparation ; sélectionner une émotion non neutre ne peut pas réutiliser silencieusement un ancien plan neutre. La restauration échange le snapshot complet des réglages sans toucher à l’identité. Une ancienne passe CI au78c674b avait déjà validé7/7 parAPI ; la correction UI exige sa propre qualification avant livraison. Le moteur neuronal et son instrumentation sont inchangés par ce correctif.

Affichage borné : le lanceur affiche une synthèse des compteurs et les dernières étapes, pas le JSON intégral de4000jobs dans un TextView. Le détail reste exportable volontairement, sans texte, voix ou identité. Cela évite une croissance de mise en page proportionnelle à la narration sur le thread UI.
