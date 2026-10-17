# 005K — baseline mesurée, sans optimisation déployée

Source : harness JVM indépendant, Linux amd64, Java21, ONNX1.27.0 CPU **un thread**, pack FP32 existant, profil réencodé uniquement depuis Alba publique CC BY4.0 à SHA épinglé. Aucun B personnel ni reconstruction de poids. [Données brutes](BASELINE-PUBLIQUE.json). Quatre scénarios : courte, longue, même texte trois phrases OFF/ON500ms ; graine42, LSD3, température.3 identiques entre comparaisons d’un même scénario. Les textes publics complets sont dans BridgeBenchmark.kt.

16 rendus chronométrés : deux sessions fraîches et une rafale de deux appels du même moteur par cas ; plus quatre références directes 005I et quatre appels du VoiceRendererPort public, soit **24 générations complètes**. PCM Float32 et conversion PCM16 identiques à 005I pour chaque comparaison à plan égal ; pas d’attente d’identité entre le rendu OFF et les segments ON, qui ont des contextes différents. Le premier lancement du harness a été refusé par le cache parce que le profil public n’avait pas été activé ; correction de la fixture par activation explicite, aucun bypass du cache ou changement du moteur.

## Temps observés sur ce serveur uniquement

| Cas public | PCM complet, médiane fresh n=2 (ms) | PCM complet, warm n=1 (ms) | Ouverture + intégrité médiane (ms) | PCM (s) | Pic PSS du processus pendant ces essais (Mio) |
|---|---:|---:|---:|---:|---:|
| short | 2805.0 | 1585.2 | 944.5 | 1.28 | 1858.9 |
| long | 4464.3 | 3488.0 | 883.0 | 2.88 | 1959.0 |
| three_sentences_off | 4555.6 | 3283.0 | 936.6 | 2.80 | 1879.4 |
| three_sentences_500ms | 7221.1 | 6436.8 | 906.2 | 6.12 | 2576.0 |

Contrôle d’intégrité indépendant : 760.1 ms ; hash du ZIP original : 157.5 ms ; encodage du profil public de test : 2405.1 ms, hors latence par phrase ; résolution de ce profil : 25.0 ms. Le B existant ne serait pas réencodé par phrase.

`completePcmReadyMs` couvre attente du lock, frontend, construction du moteur, génération/assemblage et fermeture si sessions fraîches. Premier PCM interne environ1.4–1.6s dans les premiers fresh de ces cas, **pas un premier son** : 005J attend le PCM complet, le conserve puis démarre AudioTrack. AudioTrack/son audible/fin perçue sont **null/non mesurés**, pas0ms. Les temps réels de J incluent aussi cache, focus et présentation audio. Le harness mesure séparément cache et conversion, sans prétendre les additionner en une latence matérielle certifiée.

La réutilisation est un traitement expérimental **harness seulement**, moteur fermé en fin de scénario ; son coût initial d’ouverture et le coût de fermeture sont conservés dans `retainedSessionScopes`, et l’ouverture est rappelée sur chaque ligne. Le premier appel retenu ne constitue pas un cache chaud d’états : chaque génération réinitialise ses états comme le moteur officiel. Les nombres warm isolés excluent l’ouverture de la rafale ; ne pas les présenter comme latence totale au démarrage. `modelClosedAfterScope` signifie retour de close() des sessions, **pas preuve de baisse immédiate du RSS ou de libération globale Android**.

## Observabilité et limites

Froid ici = sessions ONNX recréées ; cache de pages OS non vidé, processus/JIT non relancé entre mesures et référence/encodeur déjà exécutés. Ce n’est ni un démarrage à froid Xiaomi ni une garantie de chargement depuis disque froid. n=2/n=1 est exploratoire, sans intervalle de confiance. Aucune amélioration chiffrée de Xiaomi déduite du ressenti utilisateur.

CPU = compteur du **processus** Linux, pas une mesure d’énergie. PSS échantillonné250ms (JVM, JNI, GC, moniteur, cache et contrôles de parité compris), maxRSS = maximum depuis le démarrage du processus ; ne pas comparer ces pics à ceux d’une opération Android isolée. Le pic jusqu’à environ2.5Gio impose une qualification sur téléphone avant toute rétention. Le garde Linux prend le minimum MemAvailable et cgroup si limité, refuse sous2Gio. Cela ne simule pas les senseurs/protections Android : thermique et batterie indisponibles, aucune qualification mobile.

Le chargement de QualityEngine inclut vérification et ouverture des quatre sessions ; les temps par session ne sont pas exposés par l’API existante et restent null. L’extraction ZIP n’est pas rejouée : pack déjà installé, aucune reconstruction inutile. Le lock JVM du harness ne représente pas un arbitre entre APK. Main/flow/decoder sont les compteurs existants par GenerationMetrics ; aucun point de mesure ajouté au backend 005J.

## Opportunités classées, non appliquées à J

| Priorité | Constat mesuré / proposition | Risque et condition |
|---|---|---|
| P0 | Réécoute PCM existante : lecture du cache environ9–20ms sur ces premières lignes ; pas de modèle recalculé | Déjà disponible dans J, cache privé borné et lié au profil/contexte ; ne pas réutiliser le mauvais texte/profil par inférence |
| P1 conditionnelle | Vérification et ouvertures coûtent environ0.9s par appel ; tester réutilisation courte de ressources vérifiées | Backend commun à modifier uniquement par mission approuvée ; invalidation pack/profil/grant, fichiers ouverts/intégrité, cancellation, ferme effective et mémoire à qualifier. Jamais validation par simple mtime, ni résidence permanente sur téléphone |
| P1 | Main FlowLM + décodeur Mimi dominent le calcul, le flow est plus petit dans ces cas | Profiler buffers/allocations et sessions sur Android avant patch ; exigence PCM bit-identique et aucun changement de sampler/poids. Ne pas réduire LSD/température/qualité pour afficher un gain |
| P2 | Conversion PCM16 observée sous1ms, cache écrit/lu bien plus court que génération | Pas de filtre ni optimisation de conversion prioritaire sur cette preuve. Copies natives de gros caches déjà évitées dans QualityEngine |
| Refusé ici | Streaming/premier son précoce, INT8/quantification, threads supplémentaires, modèles toujours résidents | Change qualité, finalisation ou budgets ; hors mission, aucun déploiement ni seuil abaissé |

Pour un vrai E2E, libérer d’abord LLM/Whisper et arbitrer globalement ; garder un modèle VOICE résident entre les tours peut contredire cette obligation. Une rafale VOICE bornée sous bail unique serait une hypothèse à approuver, pas une politique automatiquement permise. Le gain possible et la RAM doivent être mesurés ensemble sur la cible.

## Reproduire dans un espace privé de développement

JDK17+ et chaîne Gradle existante ; variables vers **pack existant** et Alba publique épinglée uniquement, pas de fichiers utilisateur. `BRIDGE_BENCHMARK_OUT` reste un rapport sans audio/UUID privés. Commande :

```sh
POCKET_EXPERIMENTAL_MODEL_DIR=/private/existing-pack IDENTITY_PUBLIC_ALBA_WAV=/private/public-alba.wav BRIDGE_EXISTING_ZIP=/private/original-pack.zip BRIDGE_BENCHMARK_OUT=work/public-baseline.json ./gradlew :voiceBridgeHarness:benchmark --no-daemon --max-workers=2
```

Ne pas exécuter ce harness avec le profil B. CI ne télécharge pas les poids. Les mesures natives Cloud restent distinctes des tests de contrat mock et de tout essai Android/IPC.

RTF dans le JSON = temps de calcul / durée PCM, pauses incluses : un silence ajouté augmente le dénominateur. Ne pas appeler cela une accélération entre OFF et ON ; seuls les essais de même scénario sont comparables.
