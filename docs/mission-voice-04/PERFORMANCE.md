# Performance : calcul, première présentation et limites

## Instrumentation livrée

Horloge monotone relative par opération. PRESSED → ADMITTED → PACK_VERIFIED/TOKENIZER_READY → NATIVE_OPEN_STARTED et quatre ouvertures distinctes → FIRST_PCM → FIRST_WRITE → PRESENTATION_ESTIMATED → EOS → PLAYBACK_COMPLETED → CLOSE_RETURNED/FINISHED. La fermeture moteur peut précéder ou suivre le début de lecture suivant le mode ; les événements ne sont pas artificiellement réordonnés. Les durées d'ouverture sont séparées des dates relatives. Les événements absents sont `null`, pas zéro. Les traces des jobs longs portent aussi un offset depuis l'appui global et le nombre de jobs déjà terminés au début de la tentative.

Le rapport manuel JSON accepte uniquement compteurs numériques, enums et constantes : ni texte, hash de texte, UUID/profil, route personnelle, audio ni chemin. Statistiques sur PCM source avant conversion : samples, pic, RMS, silence |x|<0,0001, non-finis, écrêtage potentiel. Statistiques cumulées conservées même après un flux incomplet ; une statistique partielle n'est pas une génération entière. AudioTrack : submitted/head, écritures/nulles et underruns ; état, route de type numérique et volume sont aussi visibles dans le diagnostic local. Une progression matérielle estimée n'est pas une écoute humaine.

PSS Android échantillonné250ms, CPU processus, mémoire disponible/gardes existantes, thermique et température batterie, compteurs charge/énergie quand disponibles. Ce ne sont pas des mesures certifiées d'énergie. Retour de close ≠ libération JNI/RSS attestée. Une fermeture inconnue met le parcours en quarantaine ; absence de chargement natif ne crée pas de fausse quarantaine.

## Résultats natifs Linux publics réellement acquis

CPU ONNX **un thread**, modèle FP32 existant vérifié, Alba publique CC BY4.0 au SHA épinglé, jamais B. Pas de reconstruction du modèle. [Parité](proofs/PARITY.json) : PCM bit-identique au QualityEngine inchangé, phrase publique «Bonjour à vous.», graine42, LSD1/3/5, température0,3. Trois comparaisons complètes, pas d'AudioTrack.

[Texte naturel178 tokens](proofs/PUBLIC-LONG.json), LSD3/graine42/temp0,3 : une exploration de six jobs, moteur rouvert/fermé par job, sans cache résident. Les valeurs incluent ouverture/vérification dans les dates FIRST_PCM/EOS. Aucun son produit sur ce serveur.

| Job | Tokens | PCM (s) | Premier PCM interne (ms) | EOS (ms) | Politique |
|---|---:|---:|---:|---:|---|
|1|25|4,72|1686,1|6652,8|Différé|
|2|28|4,40|1572,2|6367,7|Différé|
|3|29|5,04|1697,3|7470,4|Différé|
|4|33|5,12|1552,2|7315,5|Différé|
|5|32|4,56|1527,0|6394,3|Différé|
|6|31|4,96|1442,7|7127,7|Différé|

Durée scope44170,8ms, CPU processus47480ms, PSS maximum échantillonné2128,3Mio. Le scope comprend **l'encodage de la fixture publique**, JVM/JNI/GC/sampler de mesure ; la réutilisation de B sur téléphone ne réencoderait pas B. Le CPU processus inclut d'autres threads de runtime et ne contredit pas un thread ONNX. Thermique, énergie, AudioTrack et écoute humaine sont `null`/absents sur Linux. Le maximum PSS n'est pas un budget Android qualifié ; aucune protection mobile abaissée.

Les six hashes PCM se reproduisent identiquement entre les deux explorations locales. Les premières mesures au commit C sont conservées dans l'historique Git ; elles ne constituent pas un avant/après comparable de vitesse. n exploratoire, pas de médiane Xiaomi. Le premier job peut être présenté avant le calcul de la narration entière ; aucune mesure de premier son réel sur Xiaomi n'en est déduite.

## Lecture progressive : gate et résultat partiel

Option volatile **OFF par défaut**, uniquement Diagnostic/Texte long ; Parler/SDK historique reste en différé. Prélecture configurable par API interne1ou2s (UI2s), ring fixe4s/96000samples, PCM source1job<=20s. Le producteur subit une backpressure, jamais perte/répétition/remplissage artificiel. Débit initial requis calcul/audio<=0,65 et plus grand intervalle<=la moitié de la prélecture ; ce garde conservateur ne prédit pas le débit futur. Tout ralentissement/underrun ultérieur provoque une interruption explicite, jamais un repli après un premier son ou un succès tronqué.

Les six jobs natifs publics sont trop lents pour ce gate : **tous restent différés avant toute sortie**. Aucune accélération acquise par streaming revendiquée. L'option ne réduit ni LSD, ni température, ni nombre de tokens, ni queue EOS. Optimisation d'ouvertures/allocations/caches natifs durable : non appliquée faute de preuve suffisante mémoire/arbitre inter-applications.

Première CI : API26 échoue1/6 sur UNDERRUN_STREAM pendant la finalisation. API35 échoue3/6, focus refusé car l'instrumentation n'avait pas d'Activity visible. Correction du setup API35 ; **API26–30 restent explicitement exclus du STREAM**, repli différé avant création AudioTrack. La garde d'underrun n'a pas été retirée. Résultats de la nouvelle qualification dans RESULTATS.md ; le défaut005D historique est distinct et n'est pas masqué.

## Suite physique requise

Voir [BASELINE.md](BASELINE.md) et [TEST-XIAOMI.md](TEST-XIAOMI.md). Sur même Xiaomi/profil/texte/graine/LSD/température/route, comparer trois essais espacés si admission autorise : différé puis expérimental. Consigner premier PCM, première présentation estimée, premier son réellement entendu (observation manuelle séparée), fin, complétude, PSS/thermique/énergie et underruns. Ni objectif<=10s ni qualité/émotions annoncés sans cette étape.
