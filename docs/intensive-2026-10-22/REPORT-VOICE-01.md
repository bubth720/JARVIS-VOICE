# VOICE-01 — SDK vocal local livré

Date : 23 octobre 2026. Module VOICE, dépôt `bubth720/JARVIS-VOICE`.
Branche : `feat/voice-01-intensive`. Base : `04c4ca1c050c16e097d27b08831544b66121128c`.
Fiche intégralement lue au commit de plan `31a0087381db14a9d2256e6e64373ef45b38b616`.
Le SHA de livraison est celui de cette branche ; il est fourni au coordinateur après commit.

## Code réellement livré

- `voiceSdk/` : SpeechRequest/Queue/Session/Repository, états corrélés, file PCM bornée,
  AudioFocusController d'observation, métriques, refus typés et AndroidTtsFallbackGate.
- `VaultQualitySpeechEngine` + VoicePackResolver : coffre et profils exacts existants,
  préparation puis ExpressionRenderer/QualityEngine existants ; aucun second moteur.
- StudioController/SpeakScreen : option SDK volatile, désactivée par défaut, exécutée
  sur le worker et sous les protections Studio existants, sortie PcmPlayer existante.
  Aucun nouvel audio de réécoute enregistré par le parcours SDK.
- settings/build Gradle et CI `.github/workflows/voice-sdk.yml` : module compilable,
  tests/démo/build/lint sans poids ni appareil. README SDK et reçu machine joints.

Le SDK sérialise ses appels mais ne devient pas une autorité native concurrente.
L'hôte conserve son admission. Le statut readonly de quarantaine bloque ensuite
les opérations lourdes et lectures Studio, y compris historiques : désactiver le
SDK ne contourne pas un nettoyage inconnu. Aucun seuil ni IdentityAdmission modifié.
Une erreur de fermeture supprimée par Kotlin use pendant une annulation/refus
reste bloquante ; un vrai test AutoCloseable synthétique couvre cette frontière.
Pas de reset automatique. RELEASED désigne la session SDK, pas la mémoire Android.

File : 1–3 demandes explicites, même binding, total maximal 480000 échantillons / 20 s.
Les limites existantes 1–3 segments, 1–50 tokens et pauses 0–1500 ms sont conservées.
Aucun découpage automatique ni streaming natif prétendu. Chaque rendu utilise le
chemin complet différé existant ; les chargements successifs peuvent être lents.
La perte de focus, les réponses tardives et les sorties partielles sont refusées.
Le TTS Android n'est jamais substitué implicitement à une voix privée ; son gate
retourne NON_VERIFIED sans sélection explicite et métadonnées locales observées.
Aucun fallback TTS n'est activé dans Studio par cette livraison.

## Vérifications réellement exécutées

Commandes depuis la racine, avec Java17, SDK35 et proxy/CA hérités du cloud :

```sh
./gradlew :voiceSdk:test :voiceSdk:demo :studioRuntime:test :expressionRuntime:test :identityRuntime:test :pocketRuntime:test :app:testDebugUnitTest :voiceStudio:assembleDebug :voiceStudio:lintDebug --no-daemon --max-workers=1
./gradlew :voiceSdk:test :voiceSdk:demo :voiceStudio:assembleDebug :voiceStudio:lintDebug --no-daemon --max-workers=1
```

La seconde commande a été réexécutée après la revue concurrence/nettoyage, jusqu'à
la version finale avec échecs suppressed. En cloud, préfixe effectivement utilisé :
`bash /workspace/JAVIS-Local-v2/tools/identity-cloud-env.sh` (outils existants lus,
aucun fichier CORE modifié). Le reçu enregistre cette commande exacte.

| Suite JVM | Réussis | Échecs/erreurs | Sautés |
|---|---:|---:|---:|
| voiceSdk | 25 | 0 | 0 |
| studioRuntime | 9 | 0 | 1 |
| expressionRuntime | 11 | 0 | 1 |
| identityRuntime | 18 | 0 | 1 |
| pocketRuntime | 33 | 0 | 13 |
| app | 8 | 0 | 0 |

Total : **120 tests recensés, 104 réussis, 0 échec/erreur, 16 sautés**.
Les 16 sauts historiques exigent des packs/profils publics absents ; aucune assertion
n'a été désactivée et aucun poids téléchargé. Ils ne figurent pas dans les checks
réussis du reçu de qualification logicielle. SDK : 25/25, aucun saut.

Build Studio et Lint : réussis, **17 avertissements, aucune erreur**.
Audit `scripts/pocket/verify_experimental_apk.py` : succès, API26/target35, ARM64,
signature vérifiée, alignement 16K, sans permission INTERNET ni poids inclus.
APK locale de build, jamais installée : SHA-256 `f1bce55ac3e24549d51df85e70c1532770324c0be91a983ae41c2151e75e4e94`.

Démo `:voiceSdk:demo` : vrai adaptateur → `MODEL_UNAVAILABLE` avant chargement ;
ports synthétiques → deux demandes, 480 échantillons présentés au faux output,
états PREPARING/PLAYING/IDLE/RELEASED. La sortie dit explicitement
`NO_NATIVE_GENERATION_OR_AUDIBILITY_VERIFIED`. Ce n'est pas une voix réelle.

`git diff --check` passe. Comparaison ciblée depuis la base : app, pocketRuntime,
identityRuntime, expressionRuntime, studioRuntime, contrats/harness/pilotes Binder
et deliveries restent inchangés. Recherche de motifs de secrets sur les nouveaux
fichiers : aucun résultat. Aucun poids, clé, profil ou audio privé ajouté à Git.

**Contrôle de portée historique en échec, conservé sans modification** :
`python scripts/bridge/verify_pilot_scope.py` retourne 1, message
`historical apps/contracts modified`. Cette garde du pilote interdit toute
modification voiceStudio ; l'opt-in Studio autorisé VOICE-01 modifie précisément
ce dossier. La nouvelle CI protège les moteurs, identités et fixtures inchangés
sans retirer l'ancienne garde. Ne pas annoncer que toutes les gardes passent.

## Limites, régressions possibles et suite

Aucune synthèse ONNX réelle, écoute humaine, latence matérielle, qualification
Xiaomi, installation, ni preuve absolue de libération JNI exécutée. Le problème
historique AudioTrack005D n'est pas déclaré corrigé. Le SDK ne migre ni profil,
ni archive, ni donnée ; il n'active pas Binder. Aucun contrat v1, service exporté,
signature ou permission Android modifié ; aucune fusion main.

La reprise après annulation est logique, avec nouvelle demande explicite seulement
après retour de l'appel ; le SDK ne rejoue pas une demande consommée. Les erreurs
moteur/nettoyage inconnues restent bloquantes dans cette composition jusqu'au
redémarrage du processus, sans prétendre attester l'état mémoire du système.

Prochaine mission : **VOICE-02 après livraison vérifiée de ID-01**. Les émotions
acoustiques restent indisponibles tant que le moteur ne les prend pas réellement
en charge ; réutiliser LocalProsodyPlanner. Toute activation inter-APK, nouveau
contrôle natif ou installation personnelle exige une autorisation distincte.
