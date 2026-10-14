# Mission 005D — résultats d'ingénierie

## Livré

Branche dédiée dérivée de `feat/android-pocket-cloning` à a5e2c056a3e98bee7132aa8e50dcf0d5ec18d387. PR brouillon : https://github.com/bubth720/JARVIS-VOICE/pull/5. Aucun merge, modification stable `app/` ou JARVIS CORE.

APK Pocket Probe 0.4.0-mission005d, indépendante et ARM64 ; MP3 corrigé et pack français Alba conservé. Cause : débordement Int dans `i * rate` après ~2 s à 44,1/48 kHz ; NaN non vérifiés à l'import mais refusés ensuite par l'analyse. Calcul Double, contrôle de finitude, erreurs MP01–MP12, buffers/EOS contrôlés, annulation et préservation atomique du WAV/import. Voir `DIAGNOSTIC-MP3.md`.

Compilation locale APK, APK androidTest, lint Pocket Probe et 8 tests du moteur stable réussis. Signature/ARM64/absence de permission réseau, micro, stockage global/alignement ZIP et ELF 16 Ko vérifiés. APK intermédiaire MP3 conservée dans le dossier de travail avant vérification HF. Livraison Android via les artefacts GitHub Actions ; voir `INSTALLATION-ANDROID.md`.

## Tests réellement exécutés localement

25 tests JVM Pocket découverts : **16 réussis, 9 ignorés**, 0 échec. Les 9 tests ignorés nécessitent le pack réel, dont l'inférence et le benchmark : téléchargement local de ses fichiers volumineux bloqué par la politique CDN. Les fixtures frontend légères ont été accessibles. Les tests réels d'inférence sont prévus dans le workflow sans cette restriction réseau cloud.

Nouveaux tests JVM : rééchantillonnage de sinus 44,1 et 48 kHz pendant 5/10/15 s (six cas), sortie finie, longueur exacte, RMS attendu ±0,05 dB, tailles WAV. Échec/annulation pendant sauvegarde préserve l'ancien extrait et supprime le temporaire. Tests existants mono, filtre anti-alias, en-têtes WAV, import/redémarrage/suppression, absence de faux clonage, file PCM et Arrêt conservés.

4 tests Python du téléchargement protégé exécutés : validation SHA256, absence de jeton au CDN, refus d'hôte inconnu, refus auth/corruption et préservation du précédent. Préflight HF exécuté ; garde-fou export exécuté et refus du checkpoint absent. Tests Android MediaExtractor/MediaCodec et AudioTrack compilés : ne pas les confondre avec exécution Android. Un workflow Android 15 x86_64 KVM les exécute séparément de l'APK ARM64 livrée.

## Encodeur et clonage

Authentification HF et métadonnées officielles autorisées (HTTP 200), téléchargement léger via huggingface_hub réussi. Checkpoint redirigé vers us.aws.cdn.hf.co : CONNECT 403 au stockage, sans Authorization HF. Le propriétaire a annoncé ajouter ce domaine ; son activation est à vérifier. Métadonnées officielles SHA/size publiées comme métadonnées, pas comme empreinte mesurée sur des poids téléchargés.

Tokenizer officiel identique au pack épinglé, SHA256 vérifié. Exporteur renforcé, checkpoint exact et fallback interdit, neuf cas synthétiques de parité préparés. **Export ONNX, parité chiffrée, compatibilité 18 caches/BOS/quantification et synthèse clonée end-to-end non validés** faute de poids. Aucun bouton de clonage fonctionnel fictif, aucun pack dérivé gated publié. Voir `VALIDATION-ENCODEUR.md` et `SECURITE-LICENCES.md`.

## Performances et limites

Le total temps des 25 tests Pocket JVM locaux est ~4,54 s ; il ne constitue pas un benchmark Xiaomi. La conversion affichera durée, PSS échantillonnée et thermique ; les caractéristiques de synthèse déjà instrumentées restent inchangées. Aucun gain de latence, zéro underrun ou ressemblance vocale sur Xiaomi n'est revendiqué ici.

Les mesures utilisateur antérieures (voix fluide, zéro underrun, un thread, premier audio ~10 s) restent des observations utilisateur de l'ancienne livraison. Le protocole `TESTS-TELEPHONE.md` vérifie conversion 0/5 et 0/10, préécoute, Arrêt, redémarrage, suppression, avion et non-régression Alba. La ressemblance personnelle ne pourra être évaluée qu'après validation de l'encodeur puis essai local de l'utilisateur.

## Commits et workflows

- 809542a : correctif MP3, tests longs/atomiques et diagnostics, APK 0.4.0.
- 556a81c : correction du setup Android CI (paquet tools retiré remplacé par platform-tools).
- Suite : outils HF/export protégés, tests et documentation dans la même PR.

Exécution 38578504298 : premier job codec refusé pendant setup SDK, aucun test Android exécuté dans cette tentative. Corrigé dans 556a81c. Exécution suivante : https://github.com/bubth720/JARVIS-VOICE/actions/runs/38578768025. État des tests/artefacts définitifs ajouté après collecte des résultats CI.

### Résultats CI collectés (556a81c)

Job build-and-inference réussi : **25/25 tests JVM Pocket avec pack réel, aucun ignoré**, compilation des deux APK, lint Pocket et tests stables réussis. Benchmark CPU GitHub (un passage, hors Xiaomi) pour 3,84 s d'audio : différé 1 thread 1661,93 ms / RTF 0,433 ; différé 2 threads 1276,10 ms / RTF 0,332. Ces chiffres ne mesurent pas AudioTrack ou la ressemblance.

Émulateur Android 15 x86_64 : **5 tests réellement exécutés, 4 réussis, 1 échec**. Le test MP3 validité/corruption/annulation et la matrice 12 s stéréo 44,1 kHz, mono, 48 kHz, début 0/durée 5 et 10, début 2/durée 5, import/rechargement/WAV/suppression passent. Arrêt et permissions passent. Le test audio différé observe 1 underrun au lieu de 0 ; **le résultat zéro underrun n'est pas validé**. Ajout d'observations de position/submission pour déterminer si l'événement intervient pendant le contenu ou en fin de flux, sans masquer les compteurs ni assouplir l'assertion. Le comportement du player validé sur Xiaomi est conservé.

Artefacts réellement publiés pour cette exécution :

- APK : https://github.com/bubth720/JARVIS-VOICE/actions/runs/38578768025/artifacts/11901733202
- Pack Alba public connu fonctionnel : https://github.com/bubth720/JARVIS-VOICE/actions/runs/38578768025/artifacts/11902637728
- Rapports JVM/lint : https://github.com/bubth720/JARVIS-VOICE/actions/runs/38578768025/artifacts/11901608229
- Rapports Android (incluant l'échec audio) : https://github.com/bubth720/JARVIS-VOICE/actions/runs/38578768025/artifacts/11901518355

Rétention jusqu'au 28 octobre 2026. Le statut global du workflow est en échec à cause du test audio, malgré la réussite du job de construction et la disponibilité des APK. Les observations terminales restent à inspecter lors de l'exécution suivante. Aucune validation Xiaomi n'est revendiquée.

### Livraison finale du code b7e605c

Workflow : https://github.com/bubth720/JARVIS-VOICE/actions/runs/38580365074. Job build-and-inference réussi (25 tests Pocket avec modèles réels, 8 tests stables, 4 tests Python, compilation, lint, signature/permissions/ARM64/16 Ko). Émulateur : 4/5 tests réussis, même échec strict AudioTrack. Observation exacte : **count=1 frames=72000 submitted=72000**, donc compteur constaté une fois les trois secondes entièrement consommées. Aucune interruption au milieu du contenu n'est démontrée par cette observation ; elle ne valide ni zéro compteur brut ni une écoute perceptive. L'assertion n'a pas été supprimée ou affaiblie ; la CI globale reste en échec.

Artefacts de cette version de code, disponibles jusqu'au 28 octobre 2026 :

- APK finale et APK AndroidTest : https://github.com/bubth720/JARVIS-VOICE/actions/runs/38580365074/artifacts/11901734843
- Pack Alba : https://github.com/bubth720/JARVIS-VOICE/actions/runs/38580365074/artifacts/11902559499
- Rapports JVM/lint : https://github.com/bubth720/JARVIS-VOICE/actions/runs/38580365074/artifacts/11902484705
- Rapports Android avec position à l'échec : https://github.com/bubth720/JARVIS-VOICE/actions/runs/38580365074/artifacts/11902274731

La compilation locale finale (mêmes sources, certificat différent de CI) est vérifiée séparément dans `APK-FINALE-VERIFICATION.json`. Dernière vérification réseau : secret HF ready, politique enforced, domaine custom actif huggingface.co seulement ; stockage toujours refusé. Le clonage reste bloqué, sans poids téléchargés ni parité exécutée. Ce rapport clôt la livraison MP3 et documente les conditions de reprise de l'encodeur ; il ne déclare pas la totalité des critères de Mission 005D acquise.
