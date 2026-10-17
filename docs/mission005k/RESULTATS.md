# VOICE — Mission005K : résultats et frontières

Implémentation des contrats/adaptateurs et mesures, **pas de pont opérationnel**. Branche `feat/voice-core-bridge-005k`, [PR#9 brouillon](https://github.com/bubth720/JARVIS-VOICE/pull/9) vers `feat/voice-studio-005j`, aucun merge. Base préservée cce9a45, mission préparée521e613. Retour propriétaire : J et B fonctionnent sur Xiaomi, interface validée, vitesse ressentie meilleure ; aucune nouvelle mesure physique fournie ou déduite.

## Livrables réalisés

- [Audit CORE/VOICE et refs](AUDIT-CORE-VOICE.md), [sources exactes](SOURCES.json), [décisions IPC à approuver](DECISIONS-IPC.md).
- Module indépendant voiceBridgeContract : types privés/erreurs fermées, validation stricte de cinq enveloppes proposées, autorisation/identité/temps et réservation **simulée**. Façade VoiceRendererPort et adaptateur de test séparé, port désactivé par défaut.
- [Contrat et mapping détaillés](CONTRAT-BRIDGE.md), [hash commun proposé](CONTRACT-PIN.json). Schémas UNIFIED inchangés ; aucune acceptation d’ADR étendue automatiquement.
- [Baseline performance](PERFORMANCE-BASELINE.md) et [16 lignes brutes mesurées](BASELINE-PUBLIQUE.json), comparaison native publique vérifiée sur24 générations totales, Float32/PCM16 identiques à005I à plan égal.
- [Protocole Xiaomi sans PC ni nouvelle app](TEST-XIAOMI.md), [mission future CORE](MISSION-FUTURE-CORE.md), [mission future VOICE](MISSION-FUTURE-VOICE.md). Propositions, pas des missions lancées/activées.

## Tests locaux réexécutés

[TESTS-LOCAUX.json](TESTS-LOCAUX.json) : nouveaux contrats29/29 + adaptateur3/3 = **32/32**, aucun échec/saut. Régression Studio10/10, expression12/12, identités19/19, moteur35 réussis/11 sautés (46 déclarés). Total108 réussis/11 sautés, zéro échec. JVM Linux21, ONNX1.27.0 ; le pack privé existant et Alba **publique épinglée** sont utilisés uniquement localement pour les tests natifs. Commande ciblée : `:voiceBridgeContract:test :voiceBridgeHarness:test :studioRuntime:test :expressionRuntime:test :identityRuntime:test :pocketRuntime:test --rerun-tasks`. Aucun poids téléchargé/reconstruit.

Tests nouveaux : schémas fermés/dupliqués/syntaxe/Unicode/taille/versions/nombres stricts, profil/pack/modèle/session/corrélation/annulation/consentement, délai/restant local/ancien résultat, capability privée/refusée/expirée, API24/25, refus et erreurs de réponse, disponibilité inconnue, repli non automatique, annulation idempotente, fin native distincte de fin playback/déchargement, mort/quarantaine, jeton de génération périmé, concurrence8 threads et tombstones bornées128. Date UTC sans java.time dans le contrat pour ne pas imposer API26 par cette validation ; le harness Linux n’est pas une dépendance d’une app Android.

Les tests de contrat mock ne prouvent ni identité Binder ni arbitrage interprocessus. Le harness public appelle le moteur officiel mais ne contacte pas CORE. Aucun test IPC Android de nouvelle fixture : **non implémenté**. E2E CORE réel : **non implémenté/non testé**. Mesures Xiaomi005K : **non exécutées** ; retour utilisateur005J conservé comme tel. Ni APK bridge ni installation à demander.

## Phases et preuves

A `807fbf1` : audit en lecture seule de CORE226e2e8, UNIFIED41f5350 et LLM1756e29, branches réellement vérifiées ; aucune modification de ces trois checkouts/dépôts.

B `57f31c8` : contrat proposé/port désactivé/adaptateur, première suite30/30. C `8890809` : baseline et32/32 après durcissement des cas inconnus/refus/rejeu ; anciens JVM réexécutés, empreintes verrouillées et CI nouvelle. La fixture initiale avait omis l’activation de son profil public : cache refusé correctement, puis activation explicite ajoutée sans contourner la protection.

La non-régression Android F/G/H/I/J est déclenchée dans GitHub Actions ; ses résultats nouveaux seront consignés après lecture des artefacts. L’échec historique D4/5 connu est suivi séparément, jamais désactivé. Pas de résultat CI ou de signature installé sur téléphone inféré d’un build.

## Préservation

[PRESERVATION.json](PRESERVATION.json) + `scripts/bridge/verify_scope.py` : apps D/F/G/H/I/J, CORE VOICE historique, trois bibliothèques et studioRuntime/deliveries inchangés par rapport àcce9a45. Aucune dépendance bridge ajoutée à une ancienne app. Aucun manifeste/permission réseau/micro/service dans les nouveaux modules JVM. ZIP signé005J toujours15259381 octets, SHA25682db41ca499077fc062d6300c6ee48f816cca728d346588de5ee2cc3a04260b0 ; aucune nouvelle APK. Clé de signature ancienne non touchée. Aucune archive, donnée, UUID/SHA de B, mot de passe ou enregistrement personnel utilisé/transmis.

Pack privé original conservé,234932686 octets et SHA2569436555c6965e3480c06f51fedced0f512a8316e482a7e54df0f4ee83d0a01cd vérifiés par le harness, sans publication de poids. Les seuls hashes PCM du rapport concernent Alba publique autorisée. Champ AudioTrack/premier son/fin audible/thermique/batterie=null signifie non mesuré, pas0.

## Blocages avant intégration réelle

CORE précharge/garde le LLM sans méthode publique normale de déchargement ; timeout coroutine ne prouve pas fin native. Pas d’arbitre global, signatures/UID installés CORE inconnus, API24 vs26 et senseurs à qualifier. Le contrat v1 ne lie pas encore complètement bail/profil/session/réponse et ne définit pas la résolution privée du contenu ; compromis proposés sans modifier UNIFIED. La réutilisation de sessions pourrait éviter une partie des ~0.9s d’ouverture mesurées sur ce CPU, mais le PSS du harness atteint~2.5Gio et aucune extrapolation Xiaomi ni veille permanente n’est autorisée.

**Prochaine action propriétaire** : examiner DECISIONS-IPC.md et les deux fiches, puis valider séparément transport, stratégie de confiance/signatures, arbitre/preuves natives et portée API/fallback. Ne rien installer ni migrer B pour cette revue. Cette décision conditionne les missions de raccordement, pas la publication des travaux005K déjà autorisés.
