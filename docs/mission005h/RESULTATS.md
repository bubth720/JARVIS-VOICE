# Mission 005H — résultats vérifiables

## Livré

P0 : coffre local multi-identités, références et profils ; APK indépendante
`com.jarvis.voice.voiceidentity005h`, version 0.4.0-mission005h-experimental,
versionCode 1. Import MP3/WAV, segments contigus 5–15 s, analyse/écoute,
encodage officiel FP32, profils multiples sans écrasement, neutre principal,
sélection explicite, A/B et réécoute privée sans modèle. Les caches sont séparés
par identité et leur suppression par profil ne touche pas les autres profils.

P1 : archive privée JVIENC01, AES-256-GCM, PBKDF2-HMAC-SHA256 600000, sel et nonce
aléatoires, authentification avant ZIP, limites fermées et restauration atomique
sans remplacement. Aucun export clair ; fournisseurs SAF distants refusés.
Tests et revue de code réalisés, **pas d'audit cryptographique indépendant**.
P2 : types identité/persona/expression et capabilities seulement. Aucun contrôle
émotionnel inventé, moteur supérieur ou accès à JARVIS CORE.

Le propriétaire rapporte un excellent rendu 005G sur Xiaomi. 005H réutilise
`QualityEngine`, l'encodeur, le sampler et les poids **sans modification** ;
ce retour ne valide pas automatiquement la nouvelle bibliothèque ni ses archives.
Aucune amélioration de naturel/fidélité due à l'ajout d'extraits n'est présumée.

## Tests et preuves

Code testé : `1cf2b4c37a975cba4e3e9b02e144f43d2b16334d`.
[Workflow 005H réussi](https://github.com/bubth720/JARVIS-VOICE/actions/runs/38736211051).

| Validation | Réussis | Ignorés | Échecs |
|---|---:|---:|---:|
| JVM identityRuntime, local avec pack existant | 19 | 0 | 0 |
| JVM identityRuntime, CI sans poids privés | 18 | 1 | 0 |
| JVM pocketRuntime, local avec pack existant | 35 | 11 | 0 |
| JVM pocketRuntime, CI sans poids privés | 33 | 13 | 0 |
| Android 35 x86_64, 005H | 18 | 0 | 0 |
| Android 35 x86_64, non-régression 005F | 10 | 0 | 0 |
| Android 35 x86_64, non-régression 005G | 14 | 0 | 0 |

Les sauts ne sont pas assimilés à des succès. Le cycle modèle privé est ignoré
par le CI public et exécuté dans l'espace privé avec une référence publique.
Build ARM64, AndroidTest, audits et lint passent : zéro erreur, 17 avertissements
conservés (15 SetTextI18n, ChromeOsAbiSupport, UsableSpace).

Le vrai cycle natif JVM utilise deux extraits contigus publics Alba CC BY 4.0 :
0–5 et 4–9 secondes, une seconde de recouvrement, **aucun collage**. SHA public
épinglé `46264e83cb99115c3d210260e029117566d9c64f20266d10daa78107759ede3e`.
Deux profils effectivement encodés ont des conditionnements canoniques différents.
Même phrase, graine 42, LSD 3 : A 46080 échantillons (1,92 s), B 57600 (2,40 s),
finis, sans écrêtage, RMS 0,131914/0,123295. Répéter A est bit-identique.
Le cache restitue chaque PCM exact, sans utiliser le profil de l'autre variante.
Cela prouve le parcours de deux profils, **pas une ressemblance ou qualité humaine**.
Voir [PUBLIC-CYCLE.json](PUBLIC-CYCLE.json), sans audio ni états vocaux.

Les tests JVM couvrent schéma fermé, corruption, doublons, sélection/reload,
consentement, isolation, 2+ profils, provenance/pack, quotas, compatibilité JVFP1,
échec avant commit et annulation d'encodage. Un ordre différent des mêmes tenseurs
JVFP1 est reconnu comme le même conditionnement et refusé comme faux A/B distinct.

Les tests Android couvrent imports MP3 synthétiques stéréo 44,1 kHz / mono 48 kHz,
WAV, stockage noBackup, reprise d'activité et index, isolation/suppression,
refus de mauvais URI/fournisseur distant et restauration sans consentement,
cache A/B, garde RAM/thermique, frontières d'annulation d'encodage et lecteur
STATIC PCM16/FLOAT32, phrases courtes, Arrêt et PCM invalide. Aucun poids privé
dans l'émulateur. Le milieu d'encodage y utilise un callback synthétique contrôlé ;
aucune mesure de terminaison ONNX sur Xiaomi n'est revendiquée.

Archive : aller-retour JVM et Android, fixture JVM déchiffrée par Android avec
mot de passe ASCII **et accents/emoji**, mauvais mot de passe, tag altéré,
troncature, coût KDF invalide, traversée/noms/dossiers/doublons ZIP, limites/SHA,
pack incompatible, collisions d'UUID, erreur d'écriture et rollback. Les index
précédents restent inchangés sur les refus et échecs injectés. Les répertoires
et fichiers sont synchronisés avant nettoyage ; aucune panne matérielle réelle
n'a été simulée. Les manipulations réelles du sélecteur SAF restent à essayer sur
Xiaomi ; ses gardes sont testées par appels de callback.

[CI-RESULTATS.json](CI-RESULTATS.json) distingue CI, local, sauts et limites.
[LOCAL-RESULTATS.json](LOCAL-RESULTATS.json) conserve l'audit de l'APK et du pack.

## APK et préservation

APK signée : **29323184 octets**, SHA256
`1c2bc0a1d1b0b8699c3328aef5e6a1c121af5d4c0f84e02de4e2f1db92d1dc28`.
Certificat stable SHA256
`aba9ee43361fd342e119a33ae5a628e63199b3b6ef4f14d3a3619f6d5678d89e`,
identique à 005F/G. La clé existante n'a pas été régénérée ; stockage privé Cloud
0700/0600, aucune clé dans Git/CI/artefact. Les prochaines mises à jour 005H devront
garder ce certificat/applicationId et augmenter versionCode. Aucune désinstallation
ni migration de la sandbox 005G. 005H crée ses nouveaux profils depuis les références
locales, sans PC, Termux, ADB ou débogage sans fil.

L'archive de livraison `deliveries/mission005h/005h-experimental-apk.zip` contient
**uniquement JARVIS-VOICE-005H-experimental.apk**, aucun pack ni profil. Le CI audit
l'APK livrée, sa signature épinglée, version, ARM64, manifestes, absence de poids,
archives/clé/audio embarqués et alignements ELF/ZIP 16 Ko. Le codeCommit de
`delivery.json` est un ancêtre vérifié de la livraison.

[Téléchargement direct du ZIP APK](https://github.com/bubth720/JARVIS-VOICE/raw/3efafd517906e51611e8cdf361fa2a1dca137a7f/deliveries/mission005h/005h-experimental-apk.zip).
Extraire puis installer `JARVIS-VOICE-005H-experimental.apk`. Le ZIP public a été
retéléchargé depuis ce commit : 11393808 octets, SHA256
`4a9f42541406a4510d737482619d65931c3cc08f7306f73f94894d9f00526432`.
L'APK extraite conserve exactement sa taille, son SHA256, son certificat et ses
alignements. Voir [VERIFICATION-TELECHARGEMENT.json](VERIFICATION-TELECHARGEMENT.json).
L'[artefact GitHub Actions signé](https://github.com/bubth720/JARVIS-VOICE/actions/runs/38737418123/artifacts/11974787597)
contient la même APK et les rapports (jusqu'au 14 novembre 2026). Le job build/audit
de cette livraison réussit, et son artefact a lui aussi été téléchargé et comparé
au SHA256 de l'original. Les tests de code ci-dessus restent liés à `1cf2b4c` ;
les commits de livraison et de preuves ne changent aucun code d'application.

Le pack original reste **234932686 octets**, SHA256
`9436555c6965e3480c06f51fedced0f512a8316e482a7e54df0f4ee83d0a01cd`.
Les dix fichiers extraits correspondent toujours au manifeste embarqué inchangé.
Aucun modèle reconstruit ou poids gated publié, aucun son/profil personnel utilisé
ou envoyé dans Cloud. Source des modules app/005D/005F/005G/pocketRuntime et APK
005F/G inchangées, vérification automatique du diff contre `9429a9e`.

[005F réussit](https://github.com/bubth720/JARVIS-VOICE/actions/runs/38736210996),
[005G réussit](https://github.com/bubth720/JARVIS-VOICE/actions/runs/38736211015).
Le workflow 005D conserve son échec connu AudioTrack terminal : 4/5 tests,
`count=1 frames=72000 submitted=72000`, attendu 0 obtenu 1 ; build/inférence passent.
Cet échec reste visible et aucun code 005D n'a été modifié pour le masquer.

## Limites et essai propriétaire

Un seul modèle lourd à la fois dans 005H, un thread ORT, garde 2 Gio disponibles,
thermique, mémoire et Arrêt conservés. CPU/PSS/batterie/température sont affichés
sur l'appareil ; aucun chiffre Cloud n'est assimilé à une mesure Xiaomi.
Pas de nouvelle promesse de latence, consommation ou amélioration perceptive.
Le premier échec CI était l'absence de FFmpeg avant préparation des MP3 synthétiques,
corrigée dans le workflow ; aucun échec d'application n'en a été déduit.

Voir [TEST-XIAOMI.md](TEST-XIAOMI.md) et [MIGRATION-EXPORT.md](MIGRATION-EXPORT.md).
Les sauvegardes se testent sans supprimer l'identité personnelle principale.
**Aucune validation physique 005H sur Xiaomi revendiquée.**
[PR brouillon distincte #6](https://github.com/bubth720/JARVIS-VOICE/pull/6), branche
`feat/voice-identity-005h` vers `feat/android-pocket-cloning-005d`. Aucune fusion
dans main ou PR #5.
