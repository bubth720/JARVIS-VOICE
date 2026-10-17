# JARVIS VOICE — Mission pilote Binder CORE ↔ VOICE (V-BRIDGE-001)

**État initial : mission autorisée pour développement et tests isolés, NON réalisée.** Accord utilisateur du 17 octobre 2026 : transport Bound Service Binder/AIDL explicite, authentification réelle ou appairage local contrôlé, arbitre global CORE, opt-in visible, conservations du repli et du contrat versionné. Ne pas installer, activer ni connecter la voix privée B sur Xiaomi sans nouvel accord.

## Dépôt et contrat commun

- Dépôt `bubth720/JARVIS-VOICE`, branche `feat/voice-bridge-pilot`, base vérifiée `feat/voice-core-bridge-005k` commit `6a86d847ff57fd9f695ee87eb5bff21c0c8862ca`.
- Mission sœur CORE dans `bubth720/JAVIS-Local-v2`, branche `feat/core-voice-pilot`, base `main` commit `226e2e8cc3eebd479805b89d035eb5c1cbcad540`.
- Verrou source `docs/mission005k/CONTRACT-PIN.json` : `005k.1-proposed`, SHA256 **`9c018ecfee1b62eb0c3cea36b580edb9edcbda43f3772135b01191018d5eacad`**. Le document source est verrouillé mais noté initialement `proposed-not-approved` : accord utilisateur ici = choix d'architecture **pour pilote isolé**, pas validation rétroactive des schémas UNIFIED ni de tous les champs manquants.
- Lire intégralement `docs/mission005k/DECISIONS-IPC.md`, `CONTRAT-BRIDGE.md`, `AUDIT-CORE-VOICE.md`, `MISSION-FUTURE-VOICE.md`. Négocier toute extension du contrat (profil/session/bail/texte) avec CORE, distincte et versionnée ; aucun schéma UNIFIED modifié tacitement.
- Préserver `voiceStudio` 005J validée perceptivement sur Xiaomi, `voiceExpression` 005I, modules 005D/F/G/H, bibliothèques `pocketRuntime`, `identityRuntime`, `expressionRuntime`, packs et archives ; aucune altération de signature ni `main`.

## Objectif précis

Développer **une application Android pilote indépendante** (package d'essai proposé : `com.jarvis.voice.bridgepilot`, à qualifier), jamais une mise à jour masquée de 005J. Elle reçoit localement une demande vocale CORE authentifiée, vérifie les permissions/ressources/profil, utilise la bibliothèque de synthèse hors ligne et retourne des états fiables sans exporter fichier vocal, tenseur, profil JVFP1, .jvi ni PCM. Pour tests, utiliser UNIQUEMENT des textes et profils PUBLICS autorisés. Le B personnel de l'utilisateur reste dans 005J et sa sauvegarde locale, sans transfert vers le Cloud ; la sandbox de 005J est inaccessible au pilote et ne doit jamais être contournée.

## Phases courtes et vérifiables

### V1 — Endpoint AIDL *fixture* et preuve d'appelant

- Inspecter `voiceBridgeContract`, `voiceBridgeHarness`, `voiceStudio` et les gates 005K. Créer un module APK nouveau, min26/target35/ARM64 selon contraintes réelles ; activité visible avec contrôle explicite, service `Bound Service` à composant explicite. Pas de network/mic/stockage large ni service résident permanent.
- Service exporté seulement si nécessaire aux tests/IPC et **authentification systématique avant tout traitement**. Contrôler `Binder.getCallingUid()` avant toute suppression de l'identité appelante, vérifier l'association UID→package, signatures/certificats publics et autorisation locale exacte, non seulement package name, token textuel ou permission auto-déclarée. Compte tenu des signataires réels inconnus, refus par défaut ; tests fixture sur paires de certificats identiques et distincts.
- Si signatures distinctes sur appareil, préparer un **appairage visible et révocable** conditionné à des certificats réellement vérifiés ; ne pas implémenter une acceptation automatique ou un simple PIN/nonce fourni par le client comme authentification. La permission signature seule n'autorise pas des signataires différents.
- DTO du transport borné avec version/nombre d'octets, identité attestée localement, grant éphémère, anti-rejeu session/corrélation, expiry avec horloge locale, réponse et référence de cache opaque. Pas de profil/corpus/URI/.jvi dans la charge IPC, ni logs sensibles.
- Adapter `voiceBridgeContract` sans casser les adaptateurs 005K. Le DTO `005k.1-proposed` et les schémas UNIFIED sont des **sources à discuter, pas des permissions en soi**. Tout changement de contrat nécessite accord des deux équipes et hash épinglé identique.

**Acceptation V1** : au minimum test Binder **entre deux APK distinctes sur émulateur** ; appels acceptés uniquement après autorisation contrôlée ; refus client inconnu, mauvais UID/signature, grant absent/révoqué/expiré, duplicata/réinstallation, anciens résultats, payloads longs ou JSON mal formé. Pas d'authentification simulée présentée comme preuve Android.

### V2 — Résolution du profil local et sortie vocale contrôlée

- Nouveau coffre propre au pilote, ZIP FP32 et restauration .jvi locale volontaire par l'utilisateur **ultérieurement**, non activée sur son appareil sans nouvel accord. Jamais copier des données privées de `voiceStudio`. Pour fixture utiliser profil public cloné avec consentement.
- Résoudre seulement l'identité/profil explicitement activés dans la sandbox pilote ; UUID/SHA fichier/SHA canonique/pack/engine compatibles et autorisés, pas de basculement silencieux vers un autre profil. Identifiant moteur canonique `pocket-tts-french-6l-fp32-v1`, différent de l'alias interne de QualityEngine.
- Conserver `QualityEngine`, LSD/température/seed, les pauses 2–3 phrases contrôlées, cache PCM privé borné, 50 tokens/segment, <=20 s PCM, correct arrêt AudioTrack. Pas d'émotion native revendiquée, pas de streaming sans preuve.
- Entrée texte max300 UTF-16 et enveloppes fermées ; texte exact, aucune troncature. Audio focus et cycle de vie exigent état distinct GENERATING / GENERATED / PLAYING / PLAYBACK_COMPLETED / CANCELLED / UNKNOWN ; un état final AudioTrack n'affirme pas l'audibilité humaine.
- Le moteur demeure fermé/refusé lorsque la preuve de ressources globales CORE ou son authentification est incertaine. Ne pas garder le pack résident entre plusieurs demandes par simple optimisation ; PSS/thermique Xiaomi non mesurés.
- CORE min24 et VOICE min26 : refus clair API24/25 et compatibilité ancienne TTS conservée dans CORE.

**Acceptation V2** : tests par API de fixture, moteur/profil public, sortie PCM à paramètres égaux bit-identique au baseline 005J, essais de garde mémoire et annulation, erreurs utiles et repli uniquement explicite. L'intégration native Android réelle de poids gated ne doit pas être déclarée testée par des callbacks synthétiques.

### V3 — Coordination et démonstration E2E *publique* en émulateur

- Ne commencer qu'après échange de commits SHA et hash de contrat avec la branche sœur CORE.
- Fournir preuve de bail global non auto-déclaré : CORE ne transmet pas lorsque LLM/Whisper est actif/résident, VOICE ne démarre pas sur état inconnu ; invalider après décès CORE/service, timeout et nettoyage incomplet ; jamais deux modèles lourds simultanément.
- Tester de bout en bout sur deux APK pilotes / fixtures, message public : CORE produit ou injecte un texte, Binder authentifié, VOICE génère et rend, accuse événements généré / lecture / fin / arrêt. Les limites de la fixture (pas modèle natif, pas appareil) doivent être visibles.
- L'application pilote reste **désactivée par défaut** hors opt-in de test ; pas de service de fond permanent, pas de lecture silencieuse déclenchée par n'importe quelle application.
- Ne PAS installer une nouvelle APK sur l'appareil du propriétaire sans accord additionnel et protocole d'import B local. Ne pas déployer de modification dans 005J, aucun PIN/secret exporté. Test du vrai Android Binder, pas seulement mocks JVM.

**Acceptation V3** : tests JVM + Android IPC sur émulateur contre client CORE fixture, adversaires/retours tardifs, rollback, Android35 focus/foreground/thermal, non-régressions J24/24, I31/31, H18/18, G14/14, F10/10 ; documenter échec historique D4/5 inchangé. Déclarer séparément « fixture IPC », « E2E moteur public » et « Xiaomi B ». Aucune fusion.

## Livrables Codex

- `docs/mission-voice-bridge-pilot/ARCHITECTURE.md`
- `docs/mission-voice-bridge-pilot/CONTRAT-ET-CONFIANCE.md`
- `docs/mission-voice-bridge-pilot/CONTRACT-SHA.md`
- `docs/mission-voice-bridge-pilot/TESTS-ANDROID.md`
- `docs/mission-voice-bridge-pilot/RESULTATS.md` : module, branche, commit, tests, sauts, régressions, sécurité, blocages et suite.
- Application pilote autonome et APK d'essai uniquement si effectivement bâtie, auditée et vérifiée ; **ne pas déclarer pilote 005J prêt ou installé**.
- PR brouillon `feat/voice-bridge-pilot` → `feat/voice-core-bridge-005k`. Pas de merge.

**Consigne Codex :** implémenter successivement V1→V2→V3, un commit par phase, avec arrêt explicite si CORE ne peut prouver fin native/déchargement, si politique de signature pas validée ou si contrat des deux branches divergent. Préserver toutes les applications existantes. Aucun accès aux enregistrements de l'utilisateur.
