# V-BRIDGE-001 — V1 Binder publique testée, V2/V3 arrêtées

## Livraison et source vérifiée

Dépôt `bubth720/JARVIS-VOICE`, branche `feat/voice-bridge-pilot`, [PR#11 brouillon](https://github.com/bubth720/JARVIS-VOICE/pull/11) vers `feat/voice-core-bridge-005k`. Code qualifié par CI : **ea37d5a461b94f64f3a89a2e655a7d47b0d1150d**. Une clôture documentaire suit sans changer ce code. Base005K6a86d84, mission préparée4479afb, implémentation V1 commune242eb65, reprise de qualification370aa1d/63838b2/ea37d5a. Aucun force-push ni fusion.

Modules nouveaux opt-in (`-PincludeVoicePilot=true`) : `voiceBridgePilot` et `voiceBridgePilotClient`. Application autonome `com.jarvis.voice.bridgepilot`, min26/target35, aucune dépendance native/moteur. Service Binder/AIDL explicite, permission signature et contrôle réel BinderUID→package unique→certificat courant/installation, puis autorisation locale visible/révocable60s. Aucun appelant ne s'autorise par son nom, nonce ou bail auto-déclaré. Certificats différents refusés ; appairage inter-signataires réel non activé.

Grant lié à la session et à l'identité, anti-rejeu, réponses corrélées, JSON plat strict4K, refus des champs inconnus/Unicode malformé. Protection de l'écran de consentement contre captures et touches obscurcies. Aucun fichier/profil/PCM ne traverse l'IPC. Toute demande de génération retourne **REFUSED / NATIVE_AUTHORITY_UNAVAILABLE**, sans charger de poids. Les tests n'utilisent que du texte public ; aucun enregistrement utilisateur.

## Tests réellement exécutés

| Niveau | Réussis | Ignorés | Échecs | Preuve |
|---|---:|---:|---:|---|
| JVM historique, nouvelle exécution |103|16|0|119 déclarés, poids/profils non activés pour ce run |
| Robolectric pilote API35 |17|0|0|Simulations Android, pas Binder noyau |
| Binder réel local API35 |13|14|0|Émulateur logiciel, trois APK clientes distinctes |
| Binder réel CI API26 |13|14|0|KVM, permissions/UID/certificats réels |
| Binder réel CI API35 |13|14|0|KVM, permissions/UID/certificats réels |

Par API : client autorisé9/9 ; mauvais package de même certificat2/2 et7 sauts ; autre certificat2/2 et7 sauts. Les sauts ne sont pas comptés comme succès. Les cas incluent refus avant désérialisation, malformed/duplicate/oversize, mauvais grant/hash, rejeu, résultat périmé, révocation, expiration **réelle60s**, pause, mort **réelle d'un Binder dans un second processus** et refus constant de génération. Le client de test n'est pas le CORE installé. Voir [TESTS-ANDROID.md](TESTS-ANDROID.md), [preuve CI](proofs/CI-REPRISE.json) et [preuve IPC locale](proofs/LOCAL-IPC-API35.json).

[Workflow pilote39060156787](https://github.com/bubth720/JARVIS-VOICE/actions/runs/39060156787) : deux API réussies, Lint et audit des4 APK réussis. Build des4 applications et3 APK tests ; Lint serveur0 erreur/1 avertissement d'icône non définie. Audits : signatures dédiées cohérentes, service/permission, pas de réseau/micro/stockage large, poids, archive, PCM ni JNI embarqués. Le seul service du client est un helper de vie **non exporté**, sans autorité de ressources.

Les échecs de mise au point sont conservés : première passe locale9 erreurs de setup (SystemUI ANR/texte transformé en majuscules), suivante8 succès/1 erreur initiale, puis passe complète ci-dessus. La première CI a échoué avant les tests faute de commande rg ; garde qemu remplacée par comparaison Bash équivalente. Une erreur du client Python streaming et le cache Robolectric en lecture seule ont aussi été corrigés. Aucun seuil, durée de grant ni assertion de sécurité abaissé. Ce bilan ne masque pas ces échecs.

Réinstallation/rotation sous grant actif sur le noyau Android : **non qualifiées** ; contrôles de principaux/installation testés en simulation. Qualification de production et installation propriétaire restent exclues.

## Régressions Android réexécutées sur ce commit

F **10/10**, G **14/14**, H **18/18**, I **31/31**, J **24/24** : réussies. Contrats005K et build Android historique : réussis. Les références de runs/jobs et comptes lus dans les journaux CI sont dans CI-REPRISE.json.

D **4/5**, seul échec historique inchangé : `DeviceAudioTests.testThreeSecondsOfContinuousDeferredAudioHasNoUnderruns`, `AudioTrack count=1 frames=72000 submitted=72000, expected0 but1`. Aucun changement dans D ni de son test pour masquer cet échec ; le statut global conserve donc ce contrôle rouge connu.

005D/F/G/H/I/J, bibliothèques, livraisons/signatures/archives sont inchangées. L'archive005J conserve SHA256`82db41ca499077fc062d6300c6ee48f816cca728d346588de5ee2cc3a04260b0`. Le profilB reste exclusivement sur Xiaomi, jamais accessible/importé dans le pilote Cloud. [Préservation](proofs/PRESERVATION-REPRISE.json).

## APK d'essai disponible

[APK de fixture signé — archive GitHub Actions](https://github.com/bubth720/JARVIS-VOICE/actions/runs/39060156787/artifacts/12111098020), fichier intérieur `voiceBridgePilot-debug.apk`. Archive **843857 octets**, SHA256 ZIP **0814a6254f69870838fc8a502c5eacdbb933bbc1500526e26c09b40be2565549** (métadonnées GitHub). Ce hash porte sur le ZIP d'artefact, pas sur l'APK extrait.

[Rapports et audit de l'APK CI API35](https://github.com/bubth720/JARVIS-VOICE/actions/runs/39060156787/artifacts/12111177535), contenant `voiceBridgePilot/build/apk-audit.json`, hashes/signataires exacts des fichiers CI. Le fichier proofs/APK-REPRISE.json concerne le build local indépendant ; sa signature et son hash ne sont pas ceux de l'artefact CI. Les archives CI n'ont pas été reparsées dans le runtime Cloud ; conclusions, assertions et journaux décodés ont été vérifiés via le connecteur GitHub. Aucune URL signée n'est publiée dans ces rapports.

**APK d'authentification/refus uniquement, pas un moteur vocal ni une mise à jour005J.** Signature éphémère de laboratoire, pas une promesse de mise à jour stable sur Xiaomi. Aucun poids/clé privée/audio personnel livré. Aucune installation automatique, aucun transfertB. Garder l'app005J d'origine ; le pilote n'y accède pas. Une future livraison propriétaire devra qualifier sa signature stable et son protocole d'import local avant accord d'installation.

## Gate natif et prochaine étape

CORE sœur : **7426665f2794cba83646008fa0f342d102141201**, C1 livrée, C2 natif bloquée, C3 non commencée. Copies du contrat `voice.bridge.fixture/1` comparées octet pour octet, SHA256 **cfbdff471ed0403113803a0f4c86ec75c57f723cc828f5af3c13fb3ef8042cf2**. Bundle005K9c018ecf… et UNIFIED intacts. [Coordination](COORDINATION-REPRISE.md).

**V2/V3 ne sont pas commencées** : CORE ne peut attester fin native et déchargement Llama/Whisper sur cette base ; aucun bail global réel disponible. Les mocks C2 et un grantV1 n'y suppléent pas. Aucune intégration QualityEngine/FP32, création/import de coffre personnel, référence publique encodée, cachePCM, AudioTrack/focus/foreground35, PSS/thermique modèle ou parité PCM005J revendiquée. E2E moteur public :non validé. XiaomiB :non essayé, non installé.

Suite : mission CORE dédiée pour fin/déchargement réellement observables et récupération des réservations, puis contratV2 profil/bail/provenance et tests croisés avant admission réelle. Revue des signatures installées, importB volontaire local et essai Xiaomi nécessitent l'accord prévu par MISSION.md. La connexion privée n'est pas activée, aucune fusion dans main.
