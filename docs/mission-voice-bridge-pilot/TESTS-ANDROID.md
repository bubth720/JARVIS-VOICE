# Tests et niveaux de preuve

## Exécuté dans cette session

- JVM existante :119 tests déclarés,103 réussis,16 ignorés,0 échec. Contrat005K29/29 et harness3/3 ; Studio9/10, expression11/12, identité18/19, moteur33/46. Les sauts correspondent à des cas nécessitant les poids/profils réels, non provisionnés ici ; aucun poids privé téléchargé.
- Nouveau pilote :17 tests Robolectric API35 réussis,0 échec/ignoré. Ils couvrent défaut désactivé, consentement visible/révocation/expiry/restart, session/grant/installation exacts, anti-rejeu, concurrence8 requêtes, texte exact et règles005K, JSON/Unicode bornés, ancien résultat/génération, UID/signatures/packages via PackageManager simulé, garde du véritable Stub avant désérialisation, refus natif authentifié et cycle Activity.
- APK endpoint +3 variantes clients construits/signés par clés de test séparées ; APK de tests instrumentés des3 variantes compilés. Audit statique signatures, permission signature/service exporté, min26, absence de réseau/micro/stockage large, absence de poids/profil/PCM/.so. `APK-AUDIT.json` contient hashes et certificats publics.
- Lint : zéro erreur/fatal ; avertissements conservés dans TEST-RESULTS.json (3 endpoint,10 pour chacune des3 variantes clients). Ils concernent principalement ressources UI/icône/règles d'extraction du client de test, pas une validation physique.
- Les contrôles de préservation des anciennes apps/bibliothèques/archives et du contrat commun passent. Voir PRESERVATION.json.

## Non exécuté / non acquis

Aucun émulateur ou appareil utilisé. ADB37.0.1 ne peut pas créer `/home/agent/.android` : montage réellement en lecture seule, y compris après autorisation ciblée d'écriture accordée par l'environnement. SDK35/build-tools existent, mais aucun émulateur/system-image ni accès KVM n'est provisionné. Aucun téléchargement massif d'image n'a été lancé après confirmation du blocage ADB. Réparer le montage/configuration du laboratoire ou fournir un environnement d'émulation approprié est nécessaire.

Les tests Binder instrumentés sont **compilés, non exécutés**. L'acceptation minimale V1 exige une vraie paire d'APK sur émulateur ; elle reste donc NON ACQUISE. Pas de faux positif d'authentification Android à partir de Robolectric ou du contrôle des certificats de fichiers.

Suites Android historiques J24/24,I31/31,H18/18,G14/14,F10/10 : résultats005K historiques, **non réexécutés ici**, sources inchangées. D4/5 est un échec historique inchangé, non reproduit ni corrigé dans cette session. Génération ONNX publique, comparaison PCM bit-identique005J, focus/foreground35/thermique, E2E CORE–VOICE et Xiaomi B : **non exécutés**. V2/V3 non commencées à cause du stop CORE C2.

## Commandes locales reproductibles sans appareil

Java17, Gradle Wrapper8.13 avec checksum du dépôt, SDK35/build-tools35. Signing optionnel via `VOICE_PILOT_KEYSTORE`, `VOICE_PILOT_REFUSED_KEYSTORE`, `VOICE_PILOT_KEY_PASSWORD` provisionnés hors Git ; aliasvoice-pilot. Ne pas employer les clés historiques. Sans clé, APK applicatifs non signés ; construire les APK d'instrumentation exige des identités de test appropriées.

```sh
./gradlew :voiceBridgeContract:test :voiceBridgeHarness:test :studioRuntime:test :expressionRuntime:test :identityRuntime:test :pocketRuntime:test --no-daemon --max-workers=2
./gradlew -PincludeVoicePilot=true :voiceBridgePilot:testDebugUnitTest :voiceBridgePilot:assembleDebug :voiceBridgePilot:lintDebug :voiceBridgePilotClient:assembleDebug :voiceBridgePilotClient:lintAuthorizedDebug :voiceBridgePilotClient:lintWrongPackageDebug :voiceBridgePilotClient:lintWrongSignatureDebug --no-daemon --max-workers=2
./gradlew -PincludeVoicePilot=true :voiceBridgePilotClient:assembleAuthorizedDebugAndroidTest :voiceBridgePilotClient:assembleWrongPackageDebugAndroidTest :voiceBridgePilotClient:assembleWrongSignatureDebugAndroidTest --no-daemon --max-workers=2
python3 scripts/bridge/verify_pilot_scope.py --core-root ../JAVIS-Local-v2
python3 scripts/bridge/audit_pilot_apks.py --build-tools "$ANDROID_HOME/build-tools/35.0.0"
```

## Laboratoire ultérieur : aucune installation automatique

Sur un émulateur explicitement choisi, installer seulement les4 APK fixtures et leurs APK tests avec les certificats dédiés audités, après vérification des applicationId. Aucune commande de déploiement n'est exécutée par ces scripts. Ne pas utiliser Xiaomi,005J ou données réelles. Les tests BinderFixtureTests limitent l'exécution à goldfish/ranchu/generic ; cette garde supplémentaire ne remplace pas la sélection d'un laboratoire autorisé.

Les4 tests instrumentés par variante vérifient permission effectivement accordée/refusée par Android, refus sans grant avant payload malformé, hello/request public corrélé mais refus natif/rejeu/révocation (authorized uniquement), gros Parcel avant parsing (authorized uniquement). Les autres variantes ignorent volontairement ces2 parcours positifs. La fixture VOICE reste visible et son dialogue est confirmé via UiAutomator ; le test client n'ouvre pas une Activity plein écran qui révoquerait l'opt-in VOICE. Tester aussi pause, bind death/process kill, réinstallation/UID réutilisé, rotation et anciennes sessions ; tous ces scénarios système ne sont pas couverts par les4 tests automatisés actuels. Expiry/restart/concurrence sont injectés uniquement sous Robolectric pour le moment.

Ne jamais activer la génération V2 pour « faire passer » la fixture : la réponse attendue est NATIVE_AUTHORITY_UNAVAILABLE. Reprise V2/V3 seulement après preuve native CORE et contrat d'autorité/profil validé ; garder B et les archives privées hors du laboratoire/cloud.
