# VOICE-07 — préparation exécutable, activation BLOQUÉE

Issue#16 entièrement lue et lancée par l'utilisateur. Branche `feat/voice-07-voice-delivery`, base publiée/qualifiéeVOICE06 `0e9e31553e15cc24b64f9edc8196344cf2b2d5f6` (code326c98f,16/16 parAPI26/35). Le code ajoute des bibliothèques/helpers **non raccordés**, pas une connexionCORE active.

## Contrats et conditions réellement vérifiés

- CORE02 : [e86107b8213b0aeaf9d22025cf6bef91a8e544a5 / REPORT-CORE-02](https://github.com/bubth720/JAVIS-Local-v2/blob/e86107b8213b0aeaf9d22025cf6bef91a8e544a5/docs/intensive-2026-10-22/REPORT-CORE-02.md), rapport intégralement lu. Bus local désactivé par défaut, connecteursVOICE désactivés/non disponibles ; UUID métier≠requêteJNI/bail. SPEECH_REQUESTED/RESPONSE_CHUNK disponibles mais pas émis comme faux streaming.
- UNI01 : [d947dadf73f8dec91543a3f86a1aa2ffef0b0d2f / REPORT-UNI-01](https://github.com/bubth720/JARVIS-UNIFIED/blob/d947dadf73f8dec91543a3f86a1aa2ffef0b0d2f/docs/intensive-2026-10-22/REPORT-UNI-01.md), rapport et schémaresource-lease lus. Générateur/validateurs, pas d'intégration Android ni preuveJNI. `reserves_android_ram=false` ; un JSONvalide ou release_confirmed déclaré ne prouve pas une fin native.
- PilotePR11/005K : contratV1SHAcfbdff471ed0403113803a0f4c86ec75c57f723cc828f5af3c13fb3ef8042cf2 et refusNATIVE_AUTHORITY_UNAVAILABLE conservés. V1 n'autorise que le packagefixturecom.jarvis.core.voicepilot.fixture, **pas le vraiCORE**. Les références de bail du payload n'accordent aucune autorité.
- Les certificats des APK réellement installées chez le propriétaire ne sont pas vérifiables dans ce Cloud. Ne pas présumer leur égalité ni utiliser les clés éphémères de fixture pour modifier leur confiance.

Les dépôtsCORE/UNIFIED ont seulement été lus/fetchés à ces commits : aucun fichier de leur arbre de travail modifié, ni commit/push dans ces dépôts.

## Code opérationnel livré

`voiceBridgeClient` : bibliothèque clienteJVM séparée, requêteV2publique et enveloppe de progrès strictes, transport injecté, file logique1,128IDs anti-rejeu, rejet de résultat tardif/mauvaise corrélation et modeTEXT_ONLY par défaut. DistributionCLI modèle-free, exécutable par Gradle ou son script fourni. Le transport de la démo est **synthétique en mémoire** ; il n'est pas une preuve de Binder, d'audio ou deCOREconnecté.

Nouveaux helpers internes du pilote : SpeechJobGateway/SpeechJobDispatcher/NativeSpeechLeaseTracker. Le gateway réutilise **réellement** PilotIdentity/PilotAuthorization/PilotWire, valide version/champs/publicscope/IDs/génération et consomme un vrai grant de la simulation de test ; aucune autorité native n'est inventée. Factorynon raccordée àonTransact. Les seules réponses sontREFUSED ou CANCELLED_LOGICAL_ONLY ; UNKNOWNnatif demeure bloquant.

Le service, la permission, les identités/signatures, l'AIDL, le codeonTransact et les anciennes classes de confiance restent **inchangés**. L'unique modification de configuration du pilote est une dépendance Java/Kotlin vers le client sans moteur. Aucune activité/service/provider supplémentaire, aucun modèle/SDKvocal/profile/PCM importé dans le pilote. Les changements sensibles ne sont pas implémentés ou activés.

[PropositionV2 distincte](V2-PROPOSAL.md) : aucune modification des contratsUNIFIED/v1 ou de la wireV1. Aucun transport implicite de texte privé ou dePCM. Les cuesavatar sont vides puisque la parole n'a pas lieu.

## Tests, démonstration et mesures

Exécutionlocale réelle :4tests clientJVM +6tests gatewayRobolectric +17tests pilote antérieurs, **27réussis,0échec/saut**. Cas : autorisation/refus, identité, expiration/révocation, texte privé interdit, V1fermée, rejeu entre réarmements, génération périmée, deux appels concurrents, statut/cancel idempotent, libération logique≠native, disconnect pendant transport réel inter-thread et ancienne réponse rejetée. Un appel encore en transport reste exclusif même après disconnect jusqu’à son retour ; son résultat est invalidé et ne peut changer le mode client.

Robolectric simuleAndroid/Binder : il ne remplace pas une preuvekernel. La CI exécute la **suite kernel V1 existante** surAPI26/35 (UID/certificat/permission,60s réelle,mortBinder dans un autre processus,3clients). Le nouveauV2 n'étant pas raccordé àl'IPC, ne pas annoncer des tests BinderV2.

CLI effectivement exécutée : `SYNTHETIC_IN_MEMORY_TRANSPORT_ONLY REFUSED NATIVE_AUTHORITY_UNAVAILABLE; audio=false; nativeReleaseVerified=false; coreConnected=false`, durée réelle du chemin mémoire imprimée enms (pas une latenceTTS). Génération de la distributionZip réussie. AucunAudioTrack, modèle, PSS/énergie/thermique voix ou essai physiqueCORE/Xiaomi exécuté par ce gateway.

```sh
./gradlew -PincludeVoicePilot=true :voiceBridgeClient:test :voiceBridgeClient:run :voiceBridgeClient:distZip :voiceBridgePilot:testDebugUnitTest --no-daemon --max-workers=2
python scripts/voice07/check_scope.py
# CI/labo seulement, clés éphémères séparées et émulateur explicitement choisi :
ANDROID_SERIAL=emulator-5554 bash scripts/bridge/test_pilot_android.sh
```

Les gardes historiques de portée restent inchangées ; elles peuvent refuser les extensions de missions ultérieures. La garde07 protège les octets de tous anciens fichiers critiques, contrats, APK/archives, JNI, permissions et signatures, tout en autorisant les nouveaux helpers et l'unique dépendance. Aucun changement d'assertion ou de seuil pour masquer005D ; ses résultats historiques restent dansVOICE04.

## STOP avant activation et prochaine action

**BLOQUÉ pour connexion réelle/rendu inter-applications.** Causes distinctes : (1) aucun protocoleV2 approuvé/codec/transaction, (2) vraiCORE non autorisé par l'allowlistV1, (3) égalité des signataires installés non qualifiée, appairageinter-signataires non approuvé, (4) fin/déchargement/admission natifs globaux non attestés par CORE02/UNI01, (5) aucune autorisation distincte de rendu audio inter-APK ou d'installation personnelle.

Prochaine action : revue du contratV2 et du diff d'adaptateur proposé, puis missionCORE de preuves natives et plan de signature/appairage local à faire valider. Ne pas changer de certificat, autoriser un package, contourner la permission ou démarrer un autre moteur pour franchir ce gate. Une fois approuvé seulement : codecfermé, onTransact authentifié avant unmarshalling, contexte/grant lié aux profils, bail serveur réellement observé, refus de réponse tardive, rendupublic/fin/déchargement prouvés en laboratoire. B privé reste sur téléphone,005J/05/06 et archives intacts. Aucune fusion main ni installation automatique.

## Échec CI conservé et correction de la fixture

Premier passage du code745cc45, run40089961814 : API26 réussie ; API35 échoue sur absentGrantStaleKeyAndIdempotentCancelNeverGrantResources, hello refusé par SecurityException avant les assertions du scénario. Les huit autres cas autorisés passent ; les variantes négatives API35 ne sont pas exécutées après cet échec.

La fixture historique cliquait sur Autoriser sans attendre l’état armé et partageait le processus serveur entre tests malgré les révocations asynchrones de onUnbind. Le log ne permet pas de départager ces deux courses. Correction exclusivement dans le setup Android de test : redémarrage du processus de fixture avant chaque test, puis attente de l’état visible armé après confirmation. Aucun retry de requête, attente d’expiration raccourcie, assertion supprimée ou changement du service/de la confiance. Les 9 scénarios et les comptes attendus restent identiques. La garde07 autorise exactement ces deux additions dans ce seul fichier de test ; tous les autres fichiers du client pilote restent protégés. La nouvelle qualification ci-dessous valide les deux API ; le premier échec reste conservé dans CI-INITIAL.json.

## Qualification finale et livraison

Code qualifié : **0b1d879f36e2aa61269a3841cc3e1305b394a5a0**, précédé du client/gateway b0a79a6 et de la correction d’exclusivité 745cc45. [CI 40090529698](https://github.com/bubth720/JARVIS-VOICE/actions/runs/40090529698) réussie sur les deux API : JVM/Robolectric et bibliothèques préservées, kernel Binder V1, lint et audit des quatre APK de fixture. [Preuve CI](proofs/CI.json).

| Émulateur | Client autorisé | Mauvais package | Mauvaise signature | Total |
|---|---|---|---|---|
| API 26 | 9 réussis, 0 saut | 2 réussis, 7 sauts | 2 réussis, 7 sauts | 13 réussis, 14 sauts, 0 échec |
| API 35 | 9 réussis, 0 saut | 2 réussis, 7 sauts | 2 réussis, 7 sauts | 13 réussis, 14 sauts, 0 échec |

Les sauts sont les scénarios autorisés inapplicables aux deux identités négatives ; ils ne sont pas comptés comme des succès. Fin réelle du grant de 60 secondes et mort Binder dans un second processus testées. **Aucun test kernel V2 ni rendu vocal inter-applications revendiqué.** Les 27 tests locaux client/pilote et les 118 succès / 16 sauts des autres bibliothèques sont distincts de ces essais kernel ; des suites locales Gradle étaient réutilisées UP-TO-DATE. [Snapshot](proofs/PRESERVATION-TESTS.json).

[Distribution client CLI sans modèle](https://github.com/bubth720/JARVIS-VOICE/actions/runs/40090529698/artifacts/12559280968) : artefact extérieur de 1 652 080 octets, SHA256 **92988c101aac5355d4f2dccb9f7ae4a639afc13cbb37bd2f84d1b528bb8009e1** selon métadonnées GitHub. Il contient voiceBridgeClient.zip et les documents présents au commit de code. L’archive CI n’a pas été reparsée indépendamment dans ce Cloud ; la distribution construite localement a été inspectée, extraite et exécutée séparément ([preuve locale](proofs/CLI-LOCAL.json)). Ses octets ne sont pas présumés identiques au ZIP CI.

CLI CI : refus synthétique en mémoire mesuré à 0,798 ms et 1,174 ms ; pas une latence TTS, Binder ou Xiaomi. Aucun APK pilote signé éphémèrement proposé pour installation personnelle. Le [guide de raccordement](CORE-CLIENT-GUIDE.md) fournit le point d’appel et les conditions de revue pour l’agent CORE, sans modifier son dépôt.

Les workflows historiques Binder/005K/SDK échouent à leurs gardes de portée sur cette extension : [étapes vérifiées](proofs/LEGACY-SCOPE.json). Ils ne sont pas annoncés verts ; la CI dédiée et la garde07 sont rapportées séparément. L’échec initial API35 est conservé dans [CI-INITIAL](proofs/CI-INITIAL.json), sans modification des refus de sécurité.

Préservation supplémentaire vérifiée : ZIP FP32 privé original toujours 234 932 686 octets, SHA2569436555c6965e3480c06f51fedced0f512a8316e482a7e54df0f4ee83d0a01cd ; jamais publié ni reconstruit. Profil B et archives personnelles n’ont pas été importés dans le Cloud. Les fichiers critiques de toutes les anciennes applications restent identiques à la base qualifiée.

Statut **PARTIAL / BLOCKED pour connexion vocale réelle**. Livrables non sensibles exécutables acquis, [PR #19 brouillon](https://github.com/bubth720/JARVIS-VOICE/pull/19), aucune fusion ni installation personnelle. Prochaine action : revue V2, preuve native CORE/UNIFIED et plan de confiance des signatures avant autorisation distincte de raccordement.
