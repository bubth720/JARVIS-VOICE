# Émotions — résultat partiel et absence de faux effet

## Capacités livrées

`voice04.emotional-intent/1` définit NEUTRAL, JOY, SURPRISE, SADNESS, CALM, SERIOUS, CURIOSITY. Ce contrat est local, sans extension Binder/CORE. La disponibilité est fixée par le moteur ; un label, un score ou une intention ne donne aucun droit acoustique. Pas d'intensité fictive. Les boutons non neutres sont désactivés et expliqués. Aucune instruction de style ajoutée au texte audible.

| Voie | Catégorie | Preuve actuelle / décision |
|---|---|---|
| Neutre Pocket 6L FP32 | ACOUSTIQUE de référence | PCM identique au moteur de référence pour la fixture publique LSD1/3/5 ; ce n'est pas une émotion |
| Surprise, joie, tristesse, calme, sérieux, curiosité Pocket | NON SUPPORTÉ | Pas d'entrée native validée pour ces commandes ; pas d'écoute démontrant leur contrôle |
| Anciennes intentions SURPRISED/HUMOROUS/etc | MÉTADONNÉE | Mapping éventuel conservé séparément ; jamais activé comme émotion |
| Pauses explicites VOICE-02 | DSP silence uniquement | Chronologie/pauses préservées ; aucune preuve de joie/surprise |
| Modification de pitch/rythme/énergie après PCM | DSP recherche non implémentée | Aucun filtre artificiel livré pour masquer le défaut ou prétendre une émotion |
| Références expressives d'un même locuteur public | Recherche bloquée | Alba neutre épinglée disponible ; pas de corpus autorisé même locuteur/émotions étiquetées qualifié ici. Aucun enregistrement B demandé ni envoyé |

Aucune écoute humaine de cette mission n'a été réalisée. Aucun score de naturalité, ressemblance ou émotion reconnaissable n'est attribué. Le défaut métallique ne peut être déclaré amélioré sur une métrique numérique seule.

## Modèles alternatifs : étude documentaire vérifiable

Consultation des sources officielles et métadonnées Hugging Face le 23 octobre 2026 UTC. Poids **non téléchargés**, aucune inférence, conversion ONNX ou installation Android. [Revisions, tailles et hashes annoncés](proofs/MODEL-METADATA.json) : métadonnées de stockage, pas SHA vérifiés sur des fichiers téléchargés et pas mesure de RAM.

| Candidat | Français / clonage / commande | Licence constatée | Fichiers sélectionnés (octets) | Limite de conclusion |
|---|---|---|---:|---|
| Qwen3-TTS 0.6B Base + speech tokenizer | Français et clonage ; pas de commande instruction déclarée pour Base | Apache-2.0 code + modèle | 2 511 637 364 | ~2,34 Gio de fichiers, pas un budget PSS. Pas de qualification Android/CPU ni d'émotion explicite clonée |
| Qwen3-TTS 1.7B Base + speech tokenizer | Français/clonage ; pas de commande instruction déclarée pour Base | Apache-2.0 | 4 539 706 836 | ~4,23 Gio de fichiers, activations/caches/runtime en plus ; hors admission prudente actuelle sans port et mesures |
| Qwen3-TTS 1.7B CustomVoice + speech tokenizer | Instructions de style, français ; timbres prédéfinis, pas import JVFP1 B | Apache-2.0 | 4 515 695 644 | Contrôle d'une voix prédéfinie ≠ clonage personnel contrôlable ; aucun résultat Xiaomi |
| Chatterbox Multilingual V3 (T3 V3 + S3Gen + VE + conds) | Français `fr`, clonage ; `exaggeration` numérique et CFG, pas un sélecteur garanti joie/surprise/tristesse | MIT code + modèle | 3 206 961 772 | ~2,99 Gio de fichiers, tokenizer/Perth et mémoire d'exécution en plus ; aucune mesure CPU/PSS Android |

Qwen recommande un parcours PyTorch/GPU BF16/FlashAttention ; sa publicité de latence 97 ms ne mesure pas notre CPU un thread. CustomVoice 1.7B offre des instructions ; Base offre le clonage. **Ne pas réunir abusivement ces deux capacités en promettant des émotions pilotables pour B.** Le parcours VoiceDesign→Base réclame un autre conditionnement et ne préserve pas automatiquement l'identité personnelle.

Chatterbox `mtl_tts.py` charge V2 par défaut ; V3 exige un choix explicite. Il expose `exaggeration` (conditionnement `emotion_adv`) mais cette amplitude n'est pas une classe émotionnelle. Le README indique qu'une augmentation peut accélérer la parole ; cela ne prouve pas une surprise perceptive. Watermark Perth conservé dans une éventuelle recherche, jamais retiré. Le chemin `from_pretrained` télécharge par défaut : il n'est pas intégré à notre APK hors ligne.

Les licences permissives constatées n'autorisent pas à redistribuer des enregistrements d'autrui ou à présumer le consentement à cloner. Aucun poids alternatif expédié. Les tailles justifient de réserver d'abord l'étude d'inférence à un mini-PC ; elles ne démontrent ni impossibilité absolue sur tout Android ni faisabilité sur ce Xiaomi. CPU, latence, PSS, consommation, thermique et qualité restent **non mesurés** pour ces candidats.

Sources officielles :

- [Qwen README](https://github.com/QwenLM/Qwen3-TTS/blob/main/README.md), blob `8287bac4e611b907688a74b90a1d39cf7dbeec58` ; [licence](https://github.com/QwenLM/Qwen3-TTS/blob/main/LICENSE), blob `c347eb5ec781d2702623e8e2b5923fa1a766a64d`.
- [Chatterbox README](https://github.com/resemble-ai/chatterbox/blob/master/README.md), blob `b0086550bfae600e6e0c1e65b7387935c4a5bb5d` ; [licence](https://github.com/resemble-ai/chatterbox/blob/master/LICENSE), blob `c1e82fe06523f528c975719352c223a82c8f28ce` ; [code multilingue](https://github.com/resemble-ai/chatterbox/blob/master/src/chatterbox/mtl_tts.py), blob `ec5ebff418f6abf283127c4de6bbe99580f29e69`.

## Protocole d'écoute futur, aucun résultat prérempli

D'abord corpus public autorisé d'une seule personne, plusieurs textes français et graines fixes : neutre / surprise / joie / tristesse. Présenter les audios sous codes aléatoires sans labels, ordre contrebalancé, identité/texte identiques. Pour chaque essai : émotion perçue (y compris « aucune/indéterminée »), naturel, timbre, intelligibilité, artefacts métalliques et clics. Noter n réel, accords et désaccords, latence, durée, pics/écrêtage et énergie disponible. Une mesure de F0/vitesse ne remplace pas cette écoute.

N'activer une émotion qu'après effet numérique observé **et** reconnaissance humaine sans dégradation. Si seul le débit change, garder la mention effet logiciel. Les essais personnels B restent sur téléphone, ultérieurs et volontaires ; aucun résultat B/Xiaomi inventé. La clôture complète de VOICE-04 reste impossible sans mesure physique du premier son et au moins une émotion reconnue.
