# Étude Android et serveur personnel

La version Android 49a946b, validée par l'utilisateur sur téléphone (lecture, Arrêt, mode avion), reste inchangée. Le prototype Python/PyTorch s'exécute sur ordinateur. Il ne peut pas être ajouté tel quel à l'APK.

## Première intégration envisagée après validation CPU

1. Conserver `AndroidSpeechEngine` comme moteur local du téléphone.
2. Ajouter un moteur Kotlin communiquant avec le serveur personnel authentifié, sans API vocale commerciale.
3. Référencer le profil par UUID, jamais par un chemin du serveur.
4. Lire le WAV avec un lecteur Android et faire apparaître le moteur actuellement utilisé.
5. Traiter génération, lecture, erreurs et annulation comme des opérations asynchrones. Le routeur actuel ne gère le repli que lorsqu'une requête est refusée avant acceptation ; il faudra étendre son contrat avant l'ajout réseau.
6. En mode avion ou lorsque l'ordinateur est inaccessible, garder la voix Android locale actuelle. Une voix clonée locale au téléphone nécessite une seconde étape ONNX validée.

Le prototype ne permet pas encore l'import depuis l'APK, la gestion distante des profils ou une annulation de génération après déconnexion HTTP. Ces fonctions restent à développer après validation de la voix personnelle et choix du protocole Android. Le serveur renvoie actuellement le WAV entier, pas un flux audio progressif.

## Accès au serveur sans exposition publique

Le lancement `jarvis-pocket serve` écoute seulement sur `127.0.0.1`, avec un secret personnel et un seul worker. Aucun service public, port de routeur ou tunnel public n'est créé. Le serveur réel a été lancé avec le modèle public français et testé par requête authentifiée : WAV mono 24 kHz de 4,56 secondes obtenu en 1,72 seconde. L’écoute sur 127.0.0.1 a été vérifiée et le processus a été arrêté après le test.

Pour le premier essai depuis Android, prévoir un câble USB et `adb reverse tcp:8765 tcp:8765`. L'ordinateur reste accessible via le loopback du téléphone à travers ADB. Il faudra une configuration réseau Android ciblée pour ce test de développement, le transfert sûr du secret et des délais d'attente bornés. Ne pas activer globalement les communications HTTP non chiffrées dans une version distribuée.

L'accès ultérieur sur réseau privé nécessitera une décision distincte : VPN/TLS, autorisation des appareils et règles de pare-feu. Le simple fait d'écouter sur `0.0.0.0` n'est pas une solution proposée ici.

## Pocket TTS dans sherpa-onnx

Le dépôt officiel sherpa-onnx fournit bien :
- une configuration `OfflineTtsPocketModelConfig` en Kotlin ;
- un moteur natif Pocket TTS et des callbacks de génération ;
- une API de conditionnement avec `referenceAudio` et `referenceSampleRate`.

Cependant, l'exemple Kotlin examiné utilise le bundle `sherpa-onnx-pocket-tts-int8-2026-01-26` et un texte anglais. Les releases Pocket trouvés sous `tts-models` sont les exports de janvier 2026, en float et int8. Aucun bundle français compatible n'a été identifié dans cette liste examinée. Le modèle PyTorch français récent utilise un tokenizer JSON, tandis que cet export utilise un tokenizer SentencePiece, `vocab.json` et `token_scores.json`. Un MP3 français utilisé comme référence ne transforme pas un modèle anglais en modèle français.

**La prise en charge du français directement sur Android reste non confirmée.** Pour la valider :
1. Vérifier/exporter les poids français exacts, le tokenizer et les graphes attendus par sherpa-onnx.
2. Vérifier les licences de chaque artefact ; l'ancien bundle étudié comporte une ambiguïté entre LICENSE et README.
3. Comparer les phrases françaises PyTorch et ONNX sur ordinateur : accents, ponctuation, chiffres, intelligibilité et terminaison.
4. Tester l'export compatible sur un vrai téléphone : première sortie audio, vitesse, RAM, taille des modèles, batterie et chauffe.
5. Vérifier le format de conditionnement. Les profils safetensors de ce prototype contiennent des caches PyTorch et ne sont pas directement déclarés compatibles avec sherpa-onnx. L'API Kotlin examinée utilise une référence PCM ; il faut conserver la référence privée ou créer un convertisseur testé.

Sources : [exemple Kotlin officiel](https://github.com/k2-fsa/sherpa-onnx/blob/master/kotlin-api-examples/test_pocket_tts.kt), [exports officiels](https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models), [configuration française de Pocket TTS 3.3.0](https://github.com/kyutai-labs/pocket-tts/blob/v3.3.0/pocket_tts/config/french.yaml).
