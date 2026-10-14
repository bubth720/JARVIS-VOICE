# Résultats — Mission 004, 14 octobre 2026

**APK compilé ; inférence Kotlin/JVM vérifiée ; aucune exécution sur téléphone physique dans le cloud.** Les résultats suivants ne valident pas encore Android 15 sur le téléphone de l'utilisateur, la qualité à l'écoute, la consommation énergétique ni la température.

Le code du module `app` est identique à `49a946b`. La branche de travail est `feat/android-pocket-probe`, issue de la branche Android stable `feat/android-tts-foundation`. JARVIS CORE n'a pas été ouvert ni modifié ; aucune fusion dans main.

## Modèle réellement utilisé

Modèle français officiel Kyutai 6 couches, Pocket TTS 3.3.0, poids publics révision `8843db7…`. Export communautaire ONNX INT8 Alias-Alias révision `71a4d6d…`, avec son tokenizer français JSON officiel et ses interfaces de streaming de schéma 3. Les fichiers du pack upstream ont été téléchargés et leurs tailles/SHA-256 vérifiés, pas simplement mentionnés dans une documentation.

Quatre graphes : text conditioner, FlowLM principal, tête flow LSD, Mimi decoder. Les noms, types, dimensions et états ont été inspectés puis utilisés dans des appels réels ONNX. L'encodeur Mimi **n'est pas présent**. `CloningCapability.available=false` et aucun MP3 n'est importé. La voix publique Alba inclut déjà le conditionnement BOS. Le pack livré supprime les sept autres profils publics ; graphes et tokenizer sont inchangés. Attribution et licences accompagnent le pack et l'APK.

La comparaison locale avec les composants du modèle officiel PyTorch CPU v3.3.0 donne :

| Composant, un échantillon de diagnostic | Erreur L2 normalisée INT8 / officiel |
| --- | ---: |
| Embedding du texte | 0 |
| Conditionnement FlowLM principal | 0,0821 |
| Logit EOS | 0,0102 |
| Tête flow | 0,0169 |
| Décodeur Mimi | 0,0814 |

Ces différences sont mesurées sur un échantillon contrôlé, pas sur un corpus d'évaluation complet. Elles ne prouvent pas que la quantification conserve toute la qualité. Voir [model-parity.json](model-parity.json) et le diagnostic optionnel `scripts/pocket/compare_official.py`. Pocket TTS officiel s'est chargé en mode public sans clonage, depuis le cache hors ligne ; aucune référence personnelle ni accès aux poids gated n'a été utilisé.

## Inférence réelle avec le code Kotlin

Linux x86-64, CPU AMD EPYC 9V74, quota cloud 4 vCPU, ONNX Runtime JVM 1.27.0, 2 threads, modèle français INT8 et état Alba. Le même `PocketEngine` est appelé par l'APK ; AudioTrack et les API de mesure Android ne sont pas exécutés par ce test.

| Phrase publique | Audio | Premier PCM après chargement | RTF |
| --- | ---: | ---: | ---: |
| « Bonjour, je suis JARVIS. Cette voix fonctionne sur ce téléphone. » | 3,84 s | 182 ms | 0,308 |
| « Oui. » | 0,88 s | 84 ms | 0,293 |
| « Le café est prêt, vous pouvez arrêter la lecture. » | 2,48 s | 108 ms | 0,268 |

Mesures d'un passage de vérification, variables selon charge du cloud. Une transcription indépendante avec Whisper tiny CPU retrouve les trois phrases, y compris « Oui ». C'est un contrôle automatique supplémentaire : aucune appréciation humaine de l'audio ni test de ressemblance n'est revendiqué. Aucune extrapolation des temps ou de la RAM au téléphone.

## Contrôles réalisés

- **11 tests Pocket**, aucun échec ni test ignoré lors du passage avec le vrai pack : corpus français de 20 cas comparé au frontend officiel, fixtures de l'export, état Alba/6 couches, inférence non silencieuse avec EOS, annulation depuis un autre thread et reprise, import réel, refus d'un pack corrompu avec conservation du précédent, chemins ZIP interdits, limites du texte et métriques.
- **8 tests du routeur Android stable** passent ; le module Android stable est inchangé. La CI refait ces contrôles sur une machine distincte.
- Assemblage APK et Android Lint réussis. Les avertissements de localisation concernent le prototype français ; l'absence d'ABI ChromeOS est volontaire pour ce prototype ARM64.
- Vérification de signature, identifiant distinct, min SDK 26 / cible 35, ABI ARM64 exclusive et absence de permissions INTERNET, micro ou stockage général.
- Les deux bibliothèques ONNX Runtime Android 1.27.0 sont vérifiées par hash, ELF AArch64 et segments LOAD alignés sur **16384** ; elles sont non compressées et l'APK passe `zipalign -c -P 16 4`. Aucun pont JNI supplémentaire aligné sur 4 Ko n'est embarqué.
- Les modèles sont importés dans le stockage privé. Sauvegarde cloud et transfert Android des données sont exclus. Aucun audio, MP3, état personnel, jeton, clé de signature privée ou WAV de test n'est suivi dans Git ou envoyé par la CI.

La vérification 16 Ko est **statique**, pas une exécution dans un noyau Android à pages de 16 Ko. Un téléphone à pages de 4 Ko peut valider ARM64/Android 15, mais ne valide pas à lui seul les pages de 16 Ko.

## Limites à tester avant intégration

L'APK contient des binaires ONNX Runtime précompilés ; les optimisations CPU peuvent avoir des comportements propres au SoC. Le projet Android tiers étudié signale des problèmes avec certaines versions sur un Snapdragon 8 Elite ; notre runtime 1.27.0 n'est pas déclaré validé sur ce téléphone. Une erreur Pocket est affichée, sans substitution silencieuse ; un plantage natif peut fermer le processus.

Phrases limitées à 300 caractères, 50 jetons, 20 secondes d'audio. Le premier prototype ne découpe pas de longs textes. L'annulation interrompt les opérations ONNX et la lecture ; la création initiale de sessions natives peut finir avant de rendre la main. Le contrôle thermique utilise l'état Android, pas une température CPU inaccessible universellement.

La RAM affichée est le PSS maximal observé du processus ; il dépend du téléphone, des caches et de la présence du client Android TTS. Les mesures de premier PCM et de lancement AudioTrack ne mesurent pas exactement le premier son audible. Le mode économique réduit les threads à 1 ; il ne garantit pas une économie chiffrée avant mesure.

**Prochaine validation : installer l'APK et son pack, suivre TEST-PHONE.md, puis fournir les résultats et le modèle du téléphone.** Import MP3, encodage d'une référence et intégration dans JARVIS VOICE restent différés.
