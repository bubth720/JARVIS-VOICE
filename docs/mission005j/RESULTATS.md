# Mission 005J — résultats vérifiables

VOICE Studio est implémenté en application indépendante `com.jarvis.voice.voicestudio005j`, version `0.6.0-mission005j-experimental` / code 1. Accueil Parler, Mes voix, assistant sept étapes et Avancé séparés, clair/sombre, consentement explicite après rotation, import/restauration locaux et sélection volontaire. Le moteur et les trois bibliothèques 005I sont inchangés ; quatre générations complètes restent bit-identiques avec/sans pauses. Aucune amélioration sonore ou validation Xiaomi revendiquée.

Branche `feat/voice-studio-005j`, base immuable `f917ef15ce74a045564ddabdc407ccde4b595ba6`, [PR #8 brouillon](https://github.com/bubth720/JARVIS-VOICE/pull/8) vers `feat/voice-expression-005i`. Aucune fusion. Code de l’APK : `4240e631869cd04746e0de48f368586bd59f0bd2` ; les ajouts de livraison/CI ultérieurs ne changent pas le code applicatif.

## Télécharger depuis Android

[Téléchargement direct du ZIP signé 005J](https://raw.githubusercontent.com/bubth720/JARVIS-VOICE/493b485ba3b094d3902bb99d361284a47b93b142/deliveries/mission005j/005j-experimental-apk.zip) · [taille, SHA256 et certificat](../../deliveries/mission005j/delivery.json) · [protocole Xiaomi sans PC](TEST-XIAOMI.md).

Garder 005I installée et votre sauvegarde. Importer le ZIP FP32 **déjà présent sur votre téléphone**, puis le `.jvi` chiffré local de 005I. Confirmer le bon profil B en comparant localement UUID/SHA/pack. Aucun réencodage, aucune migration forcée, aucun transfert de B au Cloud. Le propriétaire doit encore vérifier l’audibilité réelle, les pauses et la sélection sur Xiaomi.

## Tests

| Environnement | Suite | Réussis | Sautés | Échecs |
|---|---|---:|---:|---:|
| Local, pack privé existant + Alba publique | Studio JVM | 10 | 0 | 0 |
| Local, même pack | expression JVM | 12 | 0 | 0 |
| Local, même pack | identités JVM | 19 | 0 | 0 |
| Local | moteur JVM | 35 | 11 | 0 |
| CI sans poids privés | Studio JVM | 9 | 1 | 0 |
| CI sans poids privés | expression JVM | 11 | 1 | 0 |
| CI sans poids privés | identités JVM | 18 | 1 | 0 |
| CI sans poids privés | moteur JVM | 33 | 13 | 0 |
| Émulateur API35 x86_64 | Studio Android | 24 | 0 | 0 |
| Émulateur API35 x86_64 | 005F / 005G / 005H / 005I | 10 / 14 / 18 / 31 | 0 | 0 |
| Émulateur API35 x86_64 | 005D historique | 4 | 0 | 1 |

Comptes issus de XML JUnit téléchargés, sans compter une seconde fois les rapports HTML. [Local](TESTS-LOCAUX.json), [CI 005J](CI-005J.json), [non-régression avec runs/artefacts](CI-NONREGRESSION.json). Les sauts CI correspondent aux fixtures/modèles privés absents ; aucun poids n’est téléchargé en CI.

Les 24 tests J couvrent navigation/état vide, deux MP3 dans une identité/deux profils, refus 3/4 s et admission 5 s, fenêtres MP3 à 0/1 s et données tronquées, consentement/retour/reprise, coffre/crypto et `.jvi` JVM authentifié, activation explicite, Parler/Réécouter/Arrêt, pauses ambiguës, fournisseur distant refusé, ZIP invalide sans perte, isolation/permissions, thèmes et accessibilité élémentaire. Coexistence I/J et conservation d’un marqueur privé synthétique I vérifiées après tests et réinstallation de J dans l’émulateur.

Le backend d’encodage/génération et le transport des documents sont injectés **uniquement par l’APK de test** ; vrai coffre/codec MP3/crypto/AudioTrack/UI. Cela ne valide ni l’encodeur ONNX réel sur Android ni les autorisations SAF physiques Xiaomi. L’APK livrée contient uniquement le backend officiel et ContentResolver, aucun modèle/voix/fixture de démonstration. Le pack réel est exercé séparément en JVM.

## Parité du rendu

[PARITE-PUBLIQUE.json](PARITE-PUBLIQUE.json) : pack FP32 existant, sans reconstruction, référence Alba CC BY 4.0 à hash épinglé. Quatre couples 005I/J : LSD1/graine7 neutre, LSD3/graine42 intention CONVERSATION sans pauses, puis deux phrases/250 ms et trois phrases/500 ms. PCM flottant bit-identique avant conversion PCM16, erreur absolue max 0, valeurs finies et aucun écrêtage sur ces cas. Temps mesurés sur CPU Cloud, pas sur Xiaomi. Aucun filtre/pitch/timestretch, seuil ancien ou état FP32 changé. Cela n’efface pas les limites de parité indépendante PyTorch stricte documentées précédemment et ne remplace pas une écoute humaine.

## Phases et défauts corrigés

- A `244ca8f` : coque Material3 native, navigation et brouillons privés ; JVM 4/4, builds réussis.
- B `275e2a1` : assistant connecté, ajouts incrémentaux, choix et archives ; JVM 7/7, builds/lint réussis.
- C `cebb575`, `4b8f840`, `4240e63` : parité réelle, instrumentation, correctifs de reprise/restauration et du recadrage MP3, audits et livraison.

Premier run Android [38829303967](https://github.com/bubth720/JARVIS-VOICE/actions/runs/38829303967) : 20/23. Correction d’une case de consentement restaurée par la hiérarchie Android après rotation ; synchronisation du test de navigation sur l’UI idle. Second run [38830109413](https://github.com/bubth720/JARVIS-VOICE/actions/runs/38830109413) : 22/23 ; MP3 synthétique stéréo 44,1 kHz, portion 0–5 s, refus strict **118981/120000 échantillons**. Le recadrage par horodatages MediaCodec ne correspondait pas aux indices du PCM livré. Correction dans le lecteur **005J seulement** : sélection de frames décodées contiguës après traitement du codec, exactitude et EOF complet exigés, aucun padding/répétition/assemblage. Quatre cas MP3 44,1/48 kHz à 0/1 s donnent désormais chacun 120000 échantillons ; le MP3 tronqué est refusé. Aucun fichier 005I modifié.

Run applicatif [38877740628](https://github.com/bubth720/JARVIS-VOICE/actions/runs/38877740628), source `4240e63` : 24/24, coexistence/sentinelle et captures réussies. Le workflow a ensuite échoué sur sa seule commande finale : variable de résultat perdue entre shells distincts d’android-emulator-runner. Le script CI est désormais exécuté dans **un seul shell**, sans transformer les erreurs de tests en succès. Le run de livraison [38878834587](https://github.com/bubth720/JARVIS-VOICE/actions/runs/38878834587), commit `493b485`, est entièrement **vert** : build, audit du ZIP signé, 24/24 Android, coexistence et captures. Les applications et bibliothèques n’ont pas changé entre les deux runs.

005D conserve exactement son échec connu `DeviceAudioTests.testThreeSecondsOfContinuousDeferredAudioHasNoUnderruns`, `AudioTrack [count=1 frames=72000 submitted=72000] expected 0 but was 1`. Ni son test ni son lecteur n’ont été modifiés.

## Préservation et limites

[PRESERVATION.json](PRESERVATION.json) : applications D/F/G/H/I, CORE et bibliothèques inchangées par rapport à la base ; helpers AudioTrack/mesures/protections identiques hors package. Seul le nouveau lecteur MP3 J possède le correctif décrit ci-dessus. Un thread ONNX, file unique, admission 2 Gio, thermique/mémoire, lecture STATIC, caches PCM bornés et Arrêt conservés.

ZIP privé original conservé : 234932686 octets, SHA256 `9436555c6965e3480c06f51fedced0f512a8316e482a7e54df0f4ee83d0a01cd`. Aucun profil B réel, enregistrement personnel, poids privé, jeton, URL signée ou clé publié. Aucune émotion native ou amélioration perceptive attribuée à l’interface. Les mesures physiques PSS/CPU/batterie/thermique de 005J et son essai Xiaomi restent à effectuer par le propriétaire.

## Application signée

Version 0.6.0-mission005j-experimental, versionCode 1, applicationId `com.jarvis.voice.voicestudio005j`. Code source `4240e631869cd04746e0de48f368586bd59f0bd2`. Clé stable des prototypes, certificat SHA256 `aba9ee43361fd342e119a33ae5a628e63199b3b6ef4f14d3a3619f6d5678d89e` ; clé et mot de passe jamais publiés. Les prochaines mises à jour 005J devront conserver cet identifiant/certificat et augmenter versionCode. 005I reste installée indépendamment.

APK : 34123677 octets, SHA256 `febbe6bf815d3ba792734bf7312cdad665ed89a6b4d8fcd2ff3fb3a5c35cb18f`. ZIP contenant seulement `JARVIS-VOICE-005J-experimental.apk` : 15259381 octets, SHA256 `82db41ca499077fc062d6300c6ee48f816cca728d346588de5ee2cc3a04260b0`. Aucun pack FP32 n’est distribué avec cette livraison. Coût binaire total de l’interface/l’application : environ 32.54 Mio ; ne constitue pas une mesure de PSS sur Xiaomi.

Audit : signature vérifiée, natifs ARM64 ONNX 1.27.0 et hashes identiques, alignement 16 Ko, min26/target35, manifeste FP32 embarqué exact, absence de permissions Internet/micro/stockage large, d’audio/poids/archives/clefs et de backend de test dans le DEX de l’APK livrée. Bibliothèques UI Apache2/MIT attribuées ; modèles privés exclus.

Lint local : 0 erreur, 17 avertissements de portage ChromeOS x86 absent (APK Xiaomi ARM64), surdessin, API d’espace disponible conservatrice, action d’engrenage toujours visible, recommandations KTX et chaînes françaises non internationalisées. Aucun contrôle de sécurité désactivé.

Lint CI en ligne : 0 erreur, 21 avertissements ; quatre supplémentaires signalent target35 et trois dépendances épinglées ayant une version plus récente. Cible35/min26 et versions validées conservées conformément à la mission.

[Téléchargement public de l’APK seule vérifié](TELECHARGEMENT-VERIFIE.json) : HTTP200, taille/SHA du ZIP puis SHA/signature/alignement de l’APK réextraits et revérifiés. Les poids et données personnels sont exclus.

## Captures contrôlées

Vues [claire](visuels/005j-empty-light.png) et [sombre](visuels/005j-empty-dark.png) de l’émulateur API35, bibliothèque vide uniquement. Captures revues, sans voix personnelle ; FLAG_SECURE reste actif dans l’APK livrée. Le code d’instrumentation seul lève temporairement ce drapeau sur cette fenêtre vide.
