# Mission 005D — reprise du 14 octobre 2026

Le CDN Hugging Face est maintenant accessible. Le checkpoint officiel complet
est téléchargé et vérifié, et le véritable conditionnement vocal fonctionne dans
les exports privés et le runtime JVM. La création/sélection d'un profil personnel
reste désactivée dans l'application : la parité stricte de générations indépendantes
n'est pas acquise. Aucun clonage personnel fonctionnel n'est annoncé.

## Accès et checkpoint

HF_TOKEN reste un secret réseau protégé limité à `huggingface.co`. Authentification
et métadonnées HTTP 200, redirection checkpoint HTTP 302 vers `us.aws.cdn.hf.co`,
lecture partielle CDN HTTP 206, téléchargement complet HTTP 200. La requête CDN
est une nouvelle requête, sans Authorization HF et sans suivi de redirection.
Aucun jeton, identité ou URL signée n'est enregistré dans les rapports.

- Dépôt : `kyutai/pocket-tts` ; révision `d0ebc5deb50bc6b225f686782f124dd37e2dca0a`.
- Fichier : `languages/french/model.safetensors` ; taille mesurée : 219029196 octets.
- SHA256 **mesuré** : `dd5fe98f5577ecfd56db9af8057cd05fe20cbc91560042aec8a56cfb01c34995`.
- Poids et graphes dérivés conservés sous `work/`, exclu de Git, espace privé.

Preuves : `HF-ACCESS-REPRISE.json`, `CHECKPOINT-TELECHARGE.json`.

## Parités exécutées

Environnement : pocket-tts 3.3.0, torch 2.6.0+cpu, ONNX 1.23.2,
ONNX Runtime Python/JVM/Android 1.27.0. FP32 CPU, un thread pour les comparaisons.
Le chargement exige taille, SHA256, version, configuration française six couches
et BOS officiels, et interdit le repli vers les poids sans clonage.

| Comparaison | Résultat |
| --- | --- |
| Encodeur+BOS : silence/sinus/bruit, 120000/240017/360000 échantillons | 9/9 ; formes [1,64,1024], [1,127,1024], [1,189,1024] ; maxAbs ≤ 7,16e-7 ; atol/rtol 2e-4 |
| FlowLM FP32, 18 états : 64/127/189 positions et padding non nul, quatre appels successifs | 16/16 ; caches FP32 conformes, offsets/pads exactement égaux |
| Conditionnement encodeur officiel → FlowLM FP32 | 18 états conformes aux trois longueurs ; BOS une seule fois |
| Text conditioner, FlowLM, EOS, Flow, décodeur français du candidat FP32 | Parité stricte, atol/rtol 2e-4 |
| Flow FP32 | 9 entrées, parité stricte |
| Décodeur FP32 | Cinq blocs successifs de 2/4/1/3/8 frames ; audio et états conformes |
| Runtime Kotlin/JVM, véritable encodeur+BOS puis 18 états | Parité contre fixture indépendante produite par PyTorch officiel, tous les états ; annulation testée |

Les exports séparés sont `export_matched_flow.py` et `export_matched_decoder.py`.
Le défaut ONNX des requêtes entièrement masquées avec padding a été corrigé :
PyTorch SDPA produit zéro dans ce cas, alors que la décomposition softmax de
valeurs -inf produisait NaN. Un test synthétique sans poids gated couvre ce cas,
les offsets dynamiques et la continuation BOS. Aucun patch global du comparateur
officiel n'est effectué.

Le pack Alba INT8 connu fonctionnel conserve ses octets et son manifeste. Sa
comparaison avec l'officiel FP32 montre des écarts de quantification, jusqu'à
0,1598 en L2 relatif sur un cache. Offsets/pads et formes sont conformes. Cette
mesure ne prouve pas à elle seule une incompatibilité fonctionnelle INT8 ; elle
ne constitue pas non plus une validation du clonage dans ce pack. Un candidat
FP32 séparé a donc été exporté, sans mélange des graphes Alba.

Preuves : `ENCODER-PARITY-REPRISE.json`, `FLOW-FP32-PARITY.json`,
`DECODER-FP32-PARITY.json`, `ALBA-INT8-CACHE-COMPARISON.json`,
`CANDIDAT-FP32-COMPATIBILITE.json`.

## Génération complète : limite précise

Référence **publique autorisée** : Alba MacKenna, `alba-mackenna/casual.wav`,
dépôt `kyutai/tts-voices` révision `323332d33f997de8394f24a193e1a76df720e01a`,
CC BY 4.0. Seules dix secondes converties en mono 24 kHz sont utilisées.
Aucun enregistrement personnel n'a été demandé, reçu ou utilisé.

Les deux chaînes FP32 indépendantes reçoivent le même bruit aléatoire et génèrent
trois nouvelles phrases françaises. Les sorties sont finies et non silencieuses ;
EOS est détecté au même frame dans les trois cas. Toutefois, le contrôle
supplémentaire strict atol/rtol 2e-4 appliqué à la chaîne complète échoue :

| Phrase | EOS PyTorch/ONNX | maxAbs audio | L2 relatif audio |
| --- | --- | --- | --- |
| Bonjour, ma voix fonctionne hors ligne. | 21/21 | 0,0010082 | 0,0005105 |
| Le téléphone conserve mes fichiers privés. | 22/22 | 0,0034473 | 0,0009351 |
| Je peux arrêter la lecture à tout moment. | 19/19 | 0,0001135 | 0,0000564 |

Les premiers écarts hors tolérance sur les sorties intermédiaires apparaissent
aux pas 5 et 12. Le maximum observé sur un latent est 0,004869. Le diagnostic
avec les mêmes latents précédents et les mêmes entrées du décodeur passe sur
les trois phrases, avec maxAbs audio ≤ 3,17e-6. Ces résultats indiquent une
amplification des écarts numériques dans la récurrence ; ils n'indiquent pas
une rupture du BOS, des offsets ou des interfaces de cache. La localisation
exacte de chaque contribution de calcul reste à approfondir.

**Ce seuil de chaîne complète est un contrôle supplémentaire de cette reprise** :
le protocole précédent spécifiait explicitement 2e-4 pour l'encodeur, sans fixer
un seuil d'audio indépendant. Il est conservé sans relâchement ; son échec ne
prouve pas que les sorties sont perceptivement différentes ou que le clonage
est inutilisable. Il empêche ici de déclarer la parité globale stricte acquise.
Le diagnostic à entrées identiques ne remplace pas la génération indépendante.

Preuves : `GENERATION-INDEPENDANTE.json` (échec strict conservé),
`GENERATION-ENTREES-IDENTIQUES.json` (mode diagnostic clairement identifié).
Les WAV synthétisés de la référence publique restent privés, et aucune
validation acoustique ou de ressemblance personnelle n'est revendiquée.

## Implémentation Android préparée et préservée

`CandidateVoiceEncoder` exécute réellement Mimi+BOS et FlowLM, sur PCM mono 24 kHz
de 5 à 15 secondes. Il exige les graphes épinglés par SHA256, travaille à un thread,
interrompt les opérations ONNX via le jeton d'annulation, contrôle toutes les formes,
la finitude et les offsets/pads, et extrait les **18 vrais états compacts**.
Il est interne au runtime, testé en JVM, sans réseau ni écriture d'enregistrement.
Ce n'est ni un WAV renommé, ni un état Alba utilisé à la place d'un profil.

Ce candidat n'est pas branché à l'UI, n'est pas accepté par `ModelPack`, et ne
crée/persiste/sélectionne aucun profil personnel dans l'APK. Le manifeste indique
`androidImportEnabled=false`. Une persistance atomique/versionnée du profil,
la sélection dans le générateur, les essais Android privés et la validation des
ressources du pack complet restent à faire après résolution de la validation globale.
Le seul graphe FlowLM FP32 pèse 352812159 octets : son coût mémoire sur Xiaomi
n'a pas été mesuré.

Le code MP3 validé sur Xiaomi par l'utilisateur, `MainActivity`, Alba, le pack
accepté, les protections thermiques, le mode économique, la lecture différée,
Arrêt et le secours explicite sont inchangés. `app/` stable reste inchangé.
La propriété MP3 personnelle et les exclusions de sauvegarde restent sur téléphone.

## Tests et livraison

- 27/27 tests Pocket JVM, dont les deux tests du candidat réel/annulation, sans saut.
- 8/8 tests de l'application stable.
- 4/4 tests Python du téléchargement protégé ; 3/3 tests Python de l'export/padding
  et du refus de checkpoints invalides, sans poids gated ni réseau.
- APK ARM64 et APK AndroidTest compilées ; lint réussi.
- Signature APK, permissions, alignements ZIP/ELF 16 Ko vérifiés ; aucune permission INTERNET.
- Aucun émulateur ni téléphone attaché : les tests instrumentés ne sont pas exécutés
  dans cette reprise. La validation Xiaomi du MP3 est celle rapportée par l'utilisateur.

Preuves : `TESTS-REPRISE.json`, `APK-REPRISE-VERIFICATION.json`.

**Conserver l'APK Xiaomi déjà validée.** L'APK compilée localement dans cette
reprise est diagnostique, garde la création/sélection de profil désactivée, et
utilise un certificat debug différent. Elle n'est pas une mise à jour installable
par-dessus l'APK validée. Ne pas désinstaller cette dernière pour installer le
diagnostic et perdre les données privées. L'APK validée reste disponible via
[l'artefact original](https://github.com/bubth720/JARVIS-VOICE/actions/runs/38580365074/artifacts/11901734843).
Le pack Alba livré contient les mêmes fichiers épinglés, sans encodeur ni poids gated.
La signature et le SHA256 d'origine restent dans `APK-FINALE-VERIFICATION.json`.

La fiche officielle authentifiée annonce CC BY 4.0 et les conditions gated
déjà documentées (consentement explicite et légal, absence d'usages trompeurs ou
illicites). SHA256 de la fiche :
`c01f4a60dca73aa844473181096618fe9b3bf6a148986c8c368190ba03627054`.
Une redistribution autorisée exige attribution, licence et indication des
modifications. Aucun checkpoint gated, graphe dérivé candidat ou enregistrement
n'est livré/publié dans cette reprise ; le pack clonage n'est pas validé/importable.
GitHub Actions conserve uniquement le pack public Alba déjà autorisé et les tests
publics ; les validations gated s'exécutent dans le cloud privé.

La PR #5 reste en brouillon sur `feat/android-pocket-cloning-005d`, sans fusion
ni modification de `main`.

## Reproduire les validations privées

Utiliser uniquement le secret réseau protégé déjà injecté ; aucun login HF ou
jeton en argument. Installer pocket-tts 3.3.0, torch CPU 2.6.0, ONNX et ORT 1.27.0
dans un environnement privé. Exécuter depuis la racine du dépôt :

```sh
mkdir -p work && chmod 700 work
export HF_HOME="$PWD/work/hf-cache"
python scripts/pocket/check_official_access.py --report work/hf-access.json
python scripts/pocket/download_official_checkpoint.py --destination work/private-model/model.safetensors --report work/private-model/provenance.json
python scripts/pocket/export_matched_encoder.py --checkpoint work/private-model/model.safetensors --output-dir work/private-export
python scripts/pocket/export_matched_flow.py --checkpoint work/private-model/model.safetensors --output-dir work/private-export
python scripts/pocket/export_matched_decoder.py --checkpoint work/private-model/model.safetensors --bundle work/alba-pack/fr/bundle.json --output-dir work/private-export
python scripts/pocket/validate_cloning_pack.py --checkpoint work/private-model/model.safetensors --model-dir work/alba-pack/fr --encoder work/private-export/mimi_encoder_with_bos.onnx --fp32-dir work/private-export --report work/private-export/fp32-pack-validation.json
python scripts/pocket/validate_matched_generation.py --checkpoint work/private-model/model.safetensors --reference work/public-alba.wav --model-dir work/private-export --public-pack work/alba-pack/fr --report work/private-export/generation-validation.json
# Le test indépendant termine actuellement avec code 1 ; conserver son rapport.
python scripts/pocket/validate_matched_generation.py --checkpoint work/private-model/model.safetensors --reference work/public-alba.wav --model-dir work/private-export --public-pack work/alba-pack/fr --teacher-forced --report work/private-export/teacher-forced-validation.json
python scripts/pocket/prepare_conditioning_fixture.py --checkpoint work/private-model/model.safetensors --output-dir work/candidate-fixture
python -m unittest discover -s scripts/pocket/validation_tests -v
POCKET_CLONING_MODEL_DIR="$PWD/work/private-export" POCKET_CLONING_FIXTURE_DIR="$PWD/work/candidate-fixture" POCKET_MODEL_DIR="$PWD/work/alba-pack/fr" ./gradlew :pocketRuntime:test
```

Préparer le pack Alba avec `prepare_model.py` et acquérir seulement la référence
publique autorisée épinglée ci-dessus, après vérification de sa licence. Ne jamais
remplacer cette référence par un MP3 personnel dans le cloud. Les graphes candidats
restent privés et ne sont jamais envoyés aux workflows publics.
