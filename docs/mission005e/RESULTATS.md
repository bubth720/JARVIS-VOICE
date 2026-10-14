# Mission 005E — prototype Android expérimental

Une application distincte `voicePrototype` permet maintenant l'import privé du
pack FP32, l'extraction locale d'un WAV, la création réelle d'un profil, sa
persistance versionnée, sa sélection, la génération d'une nouvelle phrase et sa
suppression. Identifiant : `com.jarvis.voice.voiceprototype005e`. Le statut
**EXPÉRIMENTAL** est affiché ; aucune ressemblance personnelle ou validation Xiaomi
n'est annoncée. Pocket Probe 005D et `app/` ne sont pas modifiés.

## Résultats distingués

Les preuves numériques strictes 005D restent inchangées dans `docs/mission005d`.
La tolérance 2e-4 n'est pas abaissée. Le pack contient uniquement les graphes FP32
cohérents du checkpoint officiel français, un text conditioner réexporté depuis
les poids officiels, le tokenizer officiel vérifié et un état Alba **recalculé**
depuis l'encodeur officiel. Aucun état ni graphe INT8 Alba n'est repris.

Comparaison complète : deux références publiques autorisées, trois phrases
françaises inédites, graines 0/42/123, soit **18 paires PyTorch/ONNX**. Les chaînes
récurrentes s'arrêtent indépendamment ; l'ONNX n'est pas tronqué au EOS de PyTorch.

- Fonctionnel : **18/18** sorties finies, non silencieuses, EOS atteint avant la
  limite ; EOS au même frame dans les 18 paires. Durées de 1,44 à 2,80 secondes.
- Numérique strict audio : **7/18** passent ; les 11 échecs sont conservés.
  L2 relatif audio jusqu'à 0,05688. Il ne s'agit pas d'une validation numérique globale.
- Conditionnement : changer la référence modifie les latents pour les neuf
  combinaisons phrase/graine. Les proxies acoustiques (niveau, écrêtage, pitch)
  sont documentés ; cela ne prouve pas à lui seul la ressemblance d'un locuteur.
- Intelligibilité automatique : Whisper **tiny** observe 40,97 % de WER agrégé ;
  Whisper **small** observe 9,03 %. Les deux rapports et leurs transcriptions sont
  conservés. Small retrouve exactement 14/18 phrases, mais deux essais Selfie sur
  « Demain… Paris » présentent 57 % et 100 % de WER. Des erreurs résiduelles Alba
  sont également présentes. La seconde mesure ne remplace pas la première et ne
  transforme aucun échec numérique en réussite.
- Perceptif/humain : **non validé**. Une ASR, surtout sur de courts extraits,
  constitue un proxy d'intelligibilité, pas une preuve de ressemblance ou de qualité.

Sources : Alba MacKenna `casual.wav`, CC BY 4.0 ; `voice-donations/Selfie.wav`, CC0,
dépôt `kyutai/tts-voices` révision `323332d33f997de8394f24a193e1a76df720e01a`.
Seules des références publiques autorisées et des signaux synthétiques sont utilisés
dans le cloud. Aucun enregistrement personnel n'est demandé ou reçu.

Preuves : `EVALUATION-FP32.json`, `REFERENCES-PUBLIQUES.json`,
`INTELLIGIBILITE-TINY.json`, `INTELLIGIBILITE-SMALL.json`.

## Profils et intégrité

Le format **JVFP1** diffère du preset INT8 PTVB1. Il exige les 18 clés exactes,
des caches compacts finis de forme [2,1,frames,16,64], les mêmes offsets sur six
couches, padding nul et un identifiant cryptographique du pack. Toute modification
de poids exige un nouveau profil et une APK avec le manifeste correspondant.

L'activation d'un profil utilise un pointeur atomique vers un dossier versionné
après écriture/synchronisation/validation ; une création annulée préserve le profil
précédent. Son SHA256 est contrôlé au chargement. Consentement explicite requis.
Le profil, le WAV, les temporaires et les modèles résident dans `noBackupFilesDir`.
Le MP3 source temporaire est effacé après extraction, sans conserver son URI.
Un nouvel extrait ne remplace le profil qu'après une nouvelle création réussie.

L'import ZIP n'accepte que la liste fixe des fichiers du manifeste intégré : SHA256
et tailles exacts, refus d'entrées inconnues, doublons, traversée de chemins et
pack INT8. Staging/validation précèdent l'activation ; les erreurs d'import et
annulations ne remplacent pas le pack existant.

Le runtime fonctionne à **un thread**, sans réseau ni secours automatique, avec
décodeur par blocs bornés et lecture seulement après la génération complète.
Arrêt interrompt AudioTrack et demande la terminaison des opérations ONNX.
L'annulation pendant le chargement natif est vérifiée avant/après chaque session ;
sa latence ne peut pas être annoncée comme nulle. Thermique sévère, `lowMemory`
ou RAM système disponible <256 Mio déclenchent l'arrêt. Un minimum conservateur
de 2 Gio disponibles est demandé avant encodage/génération ; son adéquation sur
Xiaomi reste à mesurer.

## Mesures et tests disponibles

Cloud Linux CPU, un thread : RTF ONNX complet (hors création de sessions) de
0,816 à 1,039 sur les 18 essais. La comparaison simultanée PyTorch+ONNX a atteint
2861 Mio RSS ; ce n'est pas le besoin mémoire isolé de l'APK.

Cycle JVM réel sur fixture synthétique : chargement+encodage ≈1598 ms,
chargement générateur ≈728 ms, génération ≈3029 ms pour 3,04 s d'audio (RTF ≈0,996).
Le maximum historique de ce processus JVM de tests est ≈1639 Mio ; il comprend
les autres tests et n'est pas une mesure Android PSS isolée. Ce sont des mesures
CPU cloud, pas des performances Xiaomi. Voir `MESURES-JVM.json`.

- 32/32 tests Pocket JVM passent, sans saut dans le cloud privé ; 8/8 tests stables.
- Le test réel crée, persiste, recharge et utilise un profil issu de l'encodeur
  FP32, puis le supprime ; ce n'est pas une substitution de preset.
- Annulation/remplacement, consentement, corruption, mauvais pack, format INT8,
  traversée ZIP, WAV privé et sélection sont testés.
- APK ARM64 et APK AndroidTest compilées ; lint réussi ; intégrité/signature,
  alignements ELF/ZIP 16 Ko et absence de permissions réseau/micro sont vérifiés.
- Quatre tests Android de confidentialité/lancement et du premier import sont prévus en CI sans poids
  gated ; la chaîne complète avec modèles sur Android et le Xiaomi reste à tester.

L'APK affiche des mesures réelles de durée, CPU processus, PSS échantillonné,
thermique, température batterie, charge µAh et énergie nWh **si les compteurs sont
supportés**. Aucun chiffre de consommation/thermique physique n'est inventé :
ces mesures sur téléphone ne sont pas disponibles dans le cloud. Le protocole
[TEST-XIAOMI.md](TEST-XIAOMI.md) permet de les relever et de comparer les voix.

## Pack privé et licences

Le pack est construit et reproduit dans l'espace privé, environ 225 Mio ZIP et
plus de 500 Mio décompressé. `PACK-PRIVE-PROVENANCE.json` et le manifeste intégré
documentent les empreintes. **Ni le pack ni les poids gated ne sont publiés dans
GitHub/Actions. L'APK publique ne contient aucun poids, profil ou enregistrement.**

La fiche officielle du checkpoint annonce CC BY 4.0, avec attribution, lien de
licence et indication des modifications, et les conditions gated de consentement
explicite/légal et d'absence d'usages trompeurs, illicites ou attentatoires à la vie
privée. Cela ne devient pas une autorisation de publier un pack sans restrictions.
La fiche officielle et son empreinte ont été vérifiées en 005D. Les modalités
retenues sont un import personnel depuis stockage local/USB, sans publication.

`prepare_experimental_pack.py` n'utilise aucun réseau ni token ; il exige le
checkpoint officiel exact, les rapports de composantes, les hashes des exports,
le tokenizer et la référence publique épinglés. Il refuse par défaut un résultat
différent du manifeste de l'APK. Les générateurs/poids restent privés, seuls les
outils et rapports numériques sont dans Git. Le code Kyutai/ONNX Runtime est MIT,
les adaptations et notices sont attribuées dans le pack et l'APK.

Commande privée, après les exports 005D validés :

```sh
export HF_HOME="$PWD/work/hf-cache"
python scripts/pocket/prepare_experimental_pack.py \
  --checkpoint work/private-model/model.safetensors --exports work/private-export \
  --tokenizer work/alba-pack/fr/tokenizer.json --bundle work/alba-pack/fr/bundle.json \
  --reference work/mission005e/alba.wav --work-dir work/mission005e \
  --output work/mission005e/JARVIS-005E-FP32-PRIVATE-REPRODUCED.zip
```

Ne jamais fournir un enregistrement personnel à cette commande. L'état de
comparaison public est recalculé automatiquement ; les profils personnels sont
créés exclusivement sur le téléphone.
