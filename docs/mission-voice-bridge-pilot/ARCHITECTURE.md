# V-BRIDGE-001 — fixture indépendante, synthèse non raccordée

Base réelle : `feat/voice-bridge-pilot`4479afb0c5604bb82ae8d84dd0267c4d6e7cb953, au-dessus de005K6a86d847ff57fd9f695ee87eb5bff21c0c8862ca. Les fichiers de MISSION.md sont les instructions exécutées ; l'API de lecture du ticket#10 est refusée dans cet environnement.

## Modules nouveaux

- `voiceBridgePilot` : APK `com.jarvis.voice.bridgepilot`, min26/target35 ; Activity de consentement, service Binder explicite, permission signature et autorisation locale volatile. Aucun moteur/pack/profil/PCM n'est embarqué ou lu. V1 n'est pas une APK de synthèse005J.
- `voiceBridgePilotClient` : APK publics adversariaux, variantes authorized (`com.jarvis.core.voicepilot.fixture`), wrongPackage (`com.jarvis.core.voicepilot.unknown`), wrongSignature (`com.jarvis.core.voicepilot.untrusted`). Ce client de laboratoire n'est pas l'application CORE et n'est pas une intégration C3. Les tests instrumentés sont limités aux émulateurs.
- Les modules ne sont inclus qu'avec `-PincludeVoicePilot=true`. Aucune ancienne app ne dépend d'eux. Bibliothèques et applications005D/F/G/H/I/J, archives et contrats005K restent byte-identiques.

`PilotActivity` affiche l'identité/certificat du client effectivement installé puis exige une confirmation locale. `PilotIdentity` résout chaque véritable UID Binder en package unique/signature et timestamps d'installation/mise à jour. `PilotAuthorization` maintient une seule session/grant éphémère, révocable, non réémissible après consommation. `VoicePilotService` authentifie avant désérialisation AIDL puis avant décodage JSON ; le payload ne fournit jamais l'identité de l'appelant.

Hello authentifié ne révèle aucun profil et annonce native_generation_available=false / global_admission_available=false. Toute request valide renvoie REFUSED/NATIVE_AUTHORITY_UNAVAILABLE, corrélée à sa clé complète. Aucun appel ONNX, AudioTrack, TTS, stockage vocal ou réseau n'existe dans V1. Status/cancel sont des reçus de refus, jamais des preuves d'arrêt d'un moteur.

## Phases et arrêt

**V1 préparée et testée localement, acceptation Android inter-APK non acquise.** APK et tests instrumentés compilés ;17 tests sous Robolectric, pas de noyau Binder entre APK exécuté. Ne pas déclarer V1 terminée au sens de MISSION.md.

**V2 et V3 non commencées** : CORE C2 est bloquée. Sur la branche sœur issue de main226e2e8, Llama reste résident et sa libération masque les exceptions ; aucun fournisseur ne prouve la fin JNI et le déchargement Llama/Whisper. La consigne de mission demande un « arrêt explicite si CORE ne peut prouver fin native/déchargement ». Le service reste donc incapable d'admettre un moteur. Aucun booléen resourceFree, timeout ou décès Binder ne peut contourner cette règle.

Le coffre, l'import/restauration volontaire, le résolveur de profil exact, la génération via QualityEngine, cache PCM, focus/AudioTrack et événements vocaux V2 ne sont pas implémentés. Les protections et le comportement des anciennes bibliothèques sont préservés ; cela ne démontre pas un rendu public bit-identique par le pilote. Pas d'émotion ou streaming revendiqués, pas de repli automatique.

La paire de fixtures d'authentification ne prouve pas l'arbitrage global, l'absence de modèle dans un processus non participant ou une nouvelle capacité de CORE. Aucun profil B, identifiant privé, enregistrement utilisateur ou archive personnelle accédé. Aucune installation Xiaomi.

## Qualification après reprise

Code qualifié ea37d5a : vrai Binder confirmé sur émulateursAPI26/35, trois variantes clientes (certificats identiques/différents), grants/révocation/expiry/mort Binder ; rapports sous proofs/CI-REPRISE.json. Consentement local protégé parFLAG_SECURE/filtrage des touches et contrôles UI à identifiants fixes. Le helper LifetimeService du client est non exporté et ne prouve aucune fin native. V2/V3 et XiaomiB restent exclus. Voir RESULTATS.md pour les limites et l’APK de laboratoire.
