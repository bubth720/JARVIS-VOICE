# JARVIS VOICE — VOICE-04 : latence perçue, réponses longues, émotions réellement audibles

**Statut de ce document : mission prête pour Codex, PAS une fonctionnalité implémentée ni une APK installable.**
Demande utilisateur du 24 octobre 2026 : sur son Xiaomi, VOICE Studio est agréable et fonctionnel mais la réponse vocale peut dépasser **30 secondes**, les **50 tokens** limitent fortement les phrases, et l'utilisateur souhaite des émotions naturelles (notamment SURPRISE), sans perdre la voix B. Ces chiffres sont un *constat utilisateur*, **pas des métriques instrumentées**.

## 0. Références et contraintes strictes

- Dépôt : `bubth720/JARVIS-VOICE`
- Branche dédiée : `feat/voice-04-speed-longform-emotions`
- Base vérifiée : `feat/voice-02-intensive` au commit `3ffb28011506abcc94b5d4ee71d6c79fc278333b` (VOICE-01 + VOICE-02).
- Ne PAS réutiliser le nom **VOICE-03**, déjà attribué au futur pont CORE–VOICE sous contrôle CORE-02/UNI-01.
- Sources à consulter avant chaque patch : `pocketRuntime/.../QualityEngine.kt`, `expressionRuntime`, `voiceSdk`, `studioRuntime`, `voiceStudio`, `docs/intensive-2026-10-22/REPORT-VOICE-01.md`, `REPORT-VOICE-02.md`, `docs/mission005k/PERFORMANCE-BASELINE.md`, `docs/mission005i/EXPRESSION-CAPABILITIES.md`.
- Conserver en référence de repli **APK signée 005J** et profil personnel B de l'utilisateur. La branche VOICE-02 comporte des modifications SDK/Styles non encore qualifiées physiquement ; ne pas présenter ses builds CI debug comme mise à jour 005J installable.
- Ni main, ni CORE, ni UNIFIED, ni pilote Binder V1 ne sont modifiés ; aucune fusion automatique, aucune installation Xiaomi sans nouvel accord.
- Hors ligne strict : aucun compte, serveur, réseau, téléchargement automatique, upload de MP3/WAV/archives `.jvi`, de profil B, d'audio PCM ni de logs contenant le texte privé. Pas de clés dans le repo.
- Préserver `JVIENC01`, `JVFP1`, UUID/SHA des identités, moteur ONNX/Pocket French 6L FP32 actif, 18 états de profil, modèles et signatures. Ne PAS présumer que B est compatible avec un autre moteur ; un essai alternatif ne peut pas modifier B.
- Android actuel : minSdk26, ARM64, CPU 1 thread ONNX, sécurité thermique/batterie, admission RAM ~2 Gio, une opération lourde à la fois, PCM mono 24 kHz borné, arrêt/annulation, AudioFocus et libération prudente.
- Attention : l'arbitre CORE ↔ VOICE interprocessus manque encore de preuve native, donc **aucun cache de modèles durable inter-tours CORE ni pont activé**.

## 1. Diagnostic précis — ne pas confondre trois plafonds

1. **Latence** : `QualityEngine.generate(... onPcm)` fournit déjà des blocs Float32 pendant le décodage ; le Studio existant attend la fin du PCM complet avant de lancer AudioTrack STATIC. C'est une opportunité de réduire **le délai jusqu'au premier son**, distincte de la durée totale de génération. Plusieurs sessions ONNX sont rouvertes à chaque construction. Linux 005K relevait ~0,9s pour ouverture/intégrité et premier PCM interne vers 1,4–1,6s sur serveur, **non extrapolable** au Xiaomi ; le propriétaire rapporte >30s selon son essai. L'implémentation peut être limitée par le CPU Android et le temps réel.
2. **50 tokens** : plafond **par segment** du frontend actuel, non un paramètre à augmenter aveuglément. D'autres garde-fous indépendants existent : entrée typiquement <=300 unités UTF-16, 2–3 phrases complètes séparées manuellement, PCM total <=480000 échantillons/20s, SDK VOICE-01 file 1–3 demandes et sortie 20s, refus d'EOS incertain. Pour produire 150–250 tokens, il faudra chaîner **plusieurs segments/requêtes bornés**, sans conserver tout le PCM ni contourner silencieusement les limitations.
3. **Émotion** : VOICE-02 implémente contextes/pause/timeline (Athéna DAILY 500ms, WORK 250ms, URGENT 0) mais Pocket 6L **n'expose aucune commande native d'émotion validée**. Les labels SURPRISED/HUMOROUS/etc sont des métadonnées. Ne jamais présenter un champ "surprise" ou simple silence comme une émotion audible.

## 2. Phase A — Instrumentation et temps avant premier son [P0]

### Code
- Mesurer séparément, horloge monotone locale : press Parler → admission → vérification pack → chargement 4 sessions ONNX → préparation tokenizer → premier bloc PCM interne → premier buffer AudioTrack réellement écrit → premier début de présentation estimé → EOS → fin de playback → libération effective ; garder distinctions FIRST_PCM/PLAYBACK_STARTED/PLAYBACK_COMPLETED et ne pas afficher "audible" automatiquement.
- Tracer par opération (textHash local optionnel non exporté ou taille seulement, profil id expurgé) uniquement les durées, température Android, available memory/PSS échantillonnée quand accessible, énergie/batterie contextuelle, moteur, LSD/temp, nombre réel de tokens, nombre de chunks, Xruns/errors. Aucun texte/audio/UUID, aucune transmission.
- Fournir dans Avancé un écran **Diagnostic de vitesse** (court, deux phrases, ~150–200 tokens avec segmentation expérimentale) et export manuel JSON redigé ; ne pas forcer test lourd ni exiger d'envoyer les rapports privés.
- Baseline cloud/public fixture et protocole **physique** pour l'utilisateur (à exécuter après installation volontaire).

### Acceptation A
- Tests monotonicité et métriques non négatives/absentes (null ≠ 0), cancellation et cas refusé, CPU/PSS/thermique affichés honnêtement. Aucun chiffre Xiaomi inventé.
- `docs/mission-voice-04/BASELINE.md` : comparaison avant/après **sur matériel identique** après retour utilisateur, idéalement médianes sur 3 répétitions espacées et écart pour phrases courtes/longues ; conserver n réel et temps individuels.

## 3. Phase B — Faire entendre la voix plus tôt [P0]

### Prototype autonome, sans casser 005J
- Tester **AudioTrack MODE_STREAM** alimenté par le callback `QualityEngine.onPcm`, *tampon circulaire borné*, marge de prélecture configurable/mesurée pour éviter underruns, producteur-consommateur à backpressure, flush/stop/focus/cancel sûrs. Option "Lecture progressive expérimentale" **désactivée par défaut** ; ancien mode différé reste choix de référence.
- Le callback PCM ne garantit pas qu'EOS sera atteint : si génération échoue après des blocs déjà audibles, terminer explicitement en "interrompu/incomplet" et ne pas compter un succès. Aucune lecture de contenu mal formé, NaN, tronqué ou dont le plan n'a pas été validé préalablement.
- Supprimer clics/transitions parasites sans dégrader les échantillons source ni altérer PCM généré ; contrôles sample rate 24k, profondeur/buffers, AudioFocus, API26 et API35 et pertes d'audio focus.
- Si la génération est trop lente pour l'audio en temps réel ou si thermiques/RAM ne permettent pas l'alimentation fiable, refus de la fonction progressive ou repli **avant tout premier son** dans le mode différé, jamais double playback. Qualifier les underruns réels, ne pas masquer l'unique défaut 005D historique.
- Étudier optimisation **non destructive** de temps d'ouverture/intégrité et allocations/temporels ONNX dans portée d'une session admise, cache uniquement à empreinte forte et invalidation immédiate. Aucune rétention permanente ni deux ONNX lourds simultanés. **Toute modification du moteur** doit fournir preuve de PCM identique en mode neutre à entrée, profil public, graine, LSD et température identiques, ou être classée moteur expérimental distinct.
- Ne PAS accélérer en réduisant silencieusement LSD, tokens, qualité, température ou en ignorant l'EOS. Offrir un mode rapide uniquement s'il est explicitement sélectionné et évalué en qualité.

### Acceptation B
- Tests JVM/Android locaux avec producteurs synthétiques lents, rapides, exception mi-flux, cancellation, perte focus, rotation, destruction, mémoire basse, fermeture native inconnue ; stress >100 cycles sur fixtures sans fuite ; comparatifs PCM source et track lu quand mode sans transformation.
- Objectif de recherche : viser **premier son significativement avant la fin du calcul** et diminution perceptible vs +30s; objectif indicatif <=10s sur courte phrase *seulement si réaliste sur appareil* ; **ne promettre aucun nombre avant mesure Xiaomi**. La latence totale d'une narration ne peut être confondue avec le premier son.

## 4. Phase C — Lecture de textes longs [P0/P1]

- Ajouter un parcours "Texte long" sans toucher au contrat fermé 005K, à l'enveloppe IPC CORE ni au max50 par segment. Scanner par le tokenizer Pocket existant, segmentation phrase-aware **sans réécriture** des caractères ; maintenir une correspondance lossless entrée → séquences, y compris espaces, accentuation, citations ; aucune omission / répétition / réordonnancement. Respecter les règles FrenchFrontend originales qui normalisent certains signes, ne pas inventer une parité orthographique acoustique.
- Si texte contient sigles, décimales, parenthèses, abréviations ou ruptures ambiguës, proposer à l'utilisateur de corriger les points de coupe dans UI ; ne pas couper silencieusement un nom propre ni accepter un bloc dont EOS n'est pas fiable. Prévisualiser le nombre de segments et l'estimation prudente.
- Chaîner 2–6 segments / requêtes de <=50 tokens chacun **avec un budget voix temporaire borné par job** (PCM 20s/480000 samples max PAR JOB, jamais contournement d'un dépassement d'une génération) ; file mono, sortie progressive ou différée selon politique, éviter de conserver tout PCM d'un texte entier en RAM. Ne pas garder un moteur résident entre jobs sauf une autorisation temporaire de session et libération vérifiable avec garde ressources ; si non sûre, rouvrir à chaque job et mesurer.
- Préférer cible fonctionnelle 150–200 tokens utilisateur pour scénario test (c'est un **objectif**, pas un nouveau plafond garanti). Texte très long >capacité/budget admission => proposer partitions en lots explicitement consentis, aucun "illimité" promis.
- Tâche unitaire distincte du 005K contract : ne pas étendre `CoreVoiceRequestV1` 300 chars ou `voice.bridge.fixture/1` en catimini. Pour le SDK local, versionner `LongFormSpeechRequest` interne et les limites adaptées ; les anciennes API gardent leur comportement/refus.

### Acceptation C
- Preuves avec textes 50/51/100/150/200 tokens calculés au vrai tokenizer sur fixtures autorisées ; déterminisme et ordre, zéro perte/doublon, pause naturelle au bon endroit, fin ou refus complet, interruption durant chunk n, reprise explicite sans rejouer les précédents, isolation des profils, aucun PCM total non borné en RAM.
- Tester streaming + long text combinés, et mesurer avant/après temps premier son, fin complète, underrun, mémoire/température sur Xiaomi **après** accord d'installation.

## 5. Phase D — Émotions audibles, pas simples étiquettes [P1/P2]

### D1 — UI et capacités honnêtes
- Définir `EmotionalIntent` versionné : NEUTRAL, JOY, SURPRISE, SADNESS, CALM, SERIOUS, CURIOSITY ; style INTENSITY 0–2 au plus si moteur capable, pas d'échelle factice. Conserver mapping éventuel vers SURPRISED / HUMOROUS etc sans casser le format. En UI "Émotions (expérimental)" et **seulement** les émotions acoustiquement effectives disponibles. Si non disponible, griser et expliquer pourquoi ; aucune fausse démo.
- Ne pas analyser automatiquement la vie ou l'état émotionnel de l'utilisateur, aucune personnalité LLM modifiée ; CORE produit le contenu et reste autorité finale. Les prompts de style ne doivent jamais entrer dans le texte audible comme paroles par erreur.

### D2 — Voies de recherche séparées (ne pas toutes imposer)
1. **Moteur Pocket 6L existant** : tester sur voix publique des références d'une même personne aux styles explicitement distincts, pour savoir si le conditionnement du profil affecte réellement la prosodie. Les profils restent distincts et non fusionnés. **Aucune preuve actuelle que ces références permettent de contrôler surprise/joie**. Pas de nouvel enregistrement B demandé avant résultats probants.
2. **Modulation logicielle mesurée** : recherche d'une variation légère de rythme/intonation/énergie post-PCM avec DSP local, explicitement marquée comme *effet logiciel* et non véritable émotion native. Ne pas altérer les archives, profils B ni modèle ; artefacts métalliques, timbre/identité, clics, intelligibilité = critère d'échec.
3. **Moteur expressif optionnel** : étude offline comparative **Qwen3-TTS** (clonage et contrôles selon variantes) et **Chatterbox Multilingual** (français/expression), voir <https://github.com/QwenLM/Qwen3-TTS>, <https://github.com/resemble-ai/chatterbox>. Tester licences/model size/RAM/CPU/qualité/clonage/contrôle émotionnel exact. Leur existence ne prouve PAS exécution Android acceptable. Par défaut réserver les candidats lourds au futur mini-PC si le Xiaomi chauffe, dépasse la RAM ou prend plus de temps. Un profil B JVFP1 n'est **pas portable automatiquement** vers ces moteurs ; nouveaux échantillons/reconditionnement locaux volontaires et consentis, B préservé.
- Prioriser une vraie commande acoustique si validée; aucune surcharge permanente ou installation de poids à l'insu de l'utilisateur, aucun modèle différent expédié avec 005J.
- A/B humain sur mêmes phrases neutre/surprise/joie/tristesse, *d'abord avec voix publique autorisée*, mêmes texte et identité, écoute aveugle, évaluation (émotion reconnaissable, naturalité, fidélité du locuteur, intelligibilité, latence, énergie). Les jugements du propriétaire avec B ne se feront que sur téléphone et après accord ; aucune métrique perceptive inventée. Test important : si surprise modifie surtout la vitesse et pas l'intonation perçue, ne pas vendre "surprise".
- Si aucune voie réussit, livrer les éléments réellement testés, laisser les émotions natives non disponibles et signaler blocage plutôt que feindre le résultat.

### Acceptation D
- Vérifier que les émotions apparaissant activables ont **un effet mesurable et un effet audible validé** par une écoute humaine; les autres désactivées ; 005J neutre inchangé, fallback manuel immédiat ; B identique au stockage.
- Test perceptif utilisateur requis pour qualification finale et comparaison Android réelle. **Ne jamais annoncer « émotions fonctionnelles sur Xiaomi » depuis CI/Cloud seul**.

## 6. Ordre d'exécution Codex et tests

- **Lot A** instrumentation + UI diagnostics → commit/tests ; pas de déploiement.
- **Lot B** prototype de lecture progressive + gates → commit/tests ; garde mode différé comme défaut.
- **Lot C** segmentation/lecture longue + contrats internes versionnés et tests d'intégrité → commit/tests.
- **Lot D** études expressivité + éventuel code expérimental A/B si justifié par mesures, avec gates de qualité → commit/tests. Une étude qui conclut "non faisable sur Xiaomi" est une conclusion valable.
- Le développement peut s'arrêter à chaque gate ; documenter les blocages précisément. **Les tests logiciels seuls ne prouveront pas un gain de vitesse ni des émotions réelles sur Xiaomi.**
- Suites historiques F/G/H/I/J, VOICE-01/02, tests moteur/contrat/pilote, négatifs 005D, API26/35 ; tests mémoire/thermique/autonomie et vrais AudioTrack si émulateur ou appareil disponible. Conserver aussi l'échec historique 005D dans le rapport; ne pas modifier les assertions pour le masquer.
- Pas de changement `main`, pas de PR non brouillon. PR cible `feat/voice-02-intensive` et rester **draft**. Ne pas re-signer 005J avec clé différente ni effacer les données.
- Livrable APK de test **distincte ou mise à jour compatible 005J uniquement si certificat et versionCode sont qualifiés**. Sinon livrer des artefacts de laboratoire clairement non installables comme mise à jour; ne jamais demander à l'utilisateur de désinstaller 005J.
- Toute activation sensible Binder/CORE, déplacement de B, publication de voix, ajout de poids ou test physique sur le téléphone nécessite accord distinct.

## 7. Livrables attendus

- `docs/mission-voice-04/ARCHITECTURE.md` : relations entre QualityEngine, `onPcm`, SDK, Studio, lecture, budgets, isolation
- `docs/mission-voice-04/PERFORMANCE.md` : temps premier son vs génération totale et PCM, preuve PSS/thermal où réellement mesurée, budget batterie
- `docs/mission-voice-04/TEXTE-LONG.md` : segmenter/limites, tokenizer exact, garantie pas de perte
- `docs/mission-voice-04/EMOTIONS.md` : matrice des capacités **ACOUSTIQUE / DSP / MÉTADONNÉE / NON SUPPORTÉ**, modèles évalués, preuves d'écoute si obtenues
- `docs/mission-voice-04/TEST-XIAOMI.md` : tests simples en français sans PC, sauvegarde préalable, comparaison, annulation et retour arrière
- `docs/mission-voice-04/RESULTATS.md` : phases terminées ou bloquées, branches, commits, exact comptage tests réussite/échec/sauts, CI/build/PR, source des métriques, risques et prochaine action.
- Renseigner limites et différences entre **CODE ÉCRIT**, **TEST JVM**, **BINDER/ANDROID ÉMULATEUR**, **ONNX PUBLIC RÉEL** et **ESSAI PHYSIQUE B XIAOMI**.

## 8. Critères de clôture honnêtes

Mission VOICE-04 ne peut être déclarée complètement terminée que si : (i) attente réduite au premier son sur téléphone mesurée et sans nouveau risque mémoire/thermique, (ii) texte long réellement complet sans écrêtage/omission et avec garde de mémoire, (iii) au moins une émotion audible réellement reconnue par test humain sans dégrader B, sinon annoncer résultat PARTIEL et plan de moteur alternatif. Priorité absolue à la voix B, aux données locales et à la stabilité de 005J.
