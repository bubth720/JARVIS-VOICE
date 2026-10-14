# Mission 005 — lot B : référence privée, clonage bloqué

## Disponible

L'APK 0.3.0-reference conserve le lot A et ajoute : sélection MP3 par le sélecteur Android, consentement confirmé, copie privée limitée à 32 Mio, inspection audio/mpeg (5 s à 3 min, mono/stéréo), essai de décodage des premières 5 secondes avant remplacement d'une référence existante, conversion locale MediaExtractor/MediaCodec PCM16 ou float, extrait ajustable de 5 à 15 s, filtrage sinc à 64 coefficients avant rééchantillonnage mono 24 kHz, WAV mono PCM16 privé, écoute de l'extrait et suppression.

Le début est ajustable en secondes. Pour préserver les réservoirs MP3, le décodage part du début du fichier mais ne conserve en mémoire que l'extrait sélectionné. Un délai maximum de 120 s protège contre un fichier bloqué. Le bouton Arrêt annule l'import, la conversion et l'écoute ; un traitement annulé ne remplace pas l'extrait précédent. La référence et le choix début/durée sont rechargés après redémarrage.

Les indicateurs RMS dBFS, écrêtage et fraction d'échantillons faibles sont des aides. Ils ne détectent pas automatiquement musique, personnes multiples, bruit ou droits d'utilisation. L'écoute reste indispensable. Aucun traitement coûteux d'entraînement ni serveur n'est ajouté.

Les fichiers restent sous `noBackupFilesDir/voice-reference` ; sauvegarde Android et transfert de données désactivés. Pas de permission INTERNET, micro ou stockage global. Aucun URI, nom original ou PCM n'est journalisé. Le ZIP français est distinct de la référence personnelle. Un WAV de référence **n'est pas un profil vocal**.

## Encodeur : contrôles et blocage

Le pack Alias-Alias utilisé reste épinglé à `71a4d6d11a76e26b98709fa163477c77d16bdbdb`, dérivé Alba seulement. Il ne contient aucun encodeur.

Candidat examiné : `lookbe/pocket-tts-onnx-v2`, révision `b291531fa4d9a036679d6b5176308fb613687aa7`, `french/mimi_encoder.onnx`, SHA256 `c1a28913f671f3d6734336dd5ec1b96d184db496753400cb8ee7fab261d4432b`, 39 284 392 octets. Sa fiche décrit encore `french_24l`, son BOS externe et son tokenizer SentencePiece diffèrent du pack schema 3 courant.

Interface observée : `audio`, float32 [1,1,audio_len] → `latents`, float32 [batch,frames,1024]. Une exécution avec la référence **publique** Alba de Kyutai (9,984 s, convertie 48→24 kHz par FFmpeg) produit [1,125,1024], puis [1,126,1024] après BOS. Les six caches ont des dimensions conformes et des offsets 126, mais les erreurs relatives L2 contre le profil Alba épinglé sont 0,469 / 0,473 / 0,627 / 1,081 / 0,891 / 0,888. Cette comparaison est un diagnostic, avec un rééchantillonnage différent de la création du profil d'origine ; elle ne constitue pas une preuve de parité. Rapport : [encoder-candidate-report.json](encoder-candidate-report.json).

Un contrôle indépendant compare le BOS externe au BOS du modèle français officiel **3.3.0** public : mêmes dimensions [1,1,1024], erreur maximale absolue 0,20166015625, erreur relative L2 2,704101. Il ne s'agit donc pas simplement d'un nom d'entrée à adapter. Sans provenance vérifiée et parité de conditionnement, ce candidat n'est pas installé dans l'APK et aucun profil personnel n'est créé.

Le chemin de validation attendu est l'encodeur du checkpoint complet officiel :
`hf://kyutai/pocket-tts/languages/french/model.safetensors@d0ebc5deb50bc6b225f686782f124dd37e2dca0a`.

L'accès à ces poids est contrôlé par Hugging Face. Aucun secret HF_TOKEN n'est configuré dans cet environnement. `scripts/pocket/export_matched_encoder.py` prépare une conversion CPU depuis pocket-tts==3.3.0, avec BOS intégré une seule fois et comparaison PyTorch/ONNX sur trois longueurs synthétiques. Il **refuse** l'export si le chargement retombe sur les poids publics sans clonage. Ce garde-fou a été exécuté et le blocage constaté. La conversion elle-même n'a pas été réalisée ni validée faute d'accès aux poids complets.

Pour débloquer la prochaine étape, le propriétaire doit accepter les conditions sur https://huggingface.co/kyutai/pocket-tts et configurer un jeton de lecture dans un secret **HF_TOKEN** de l'environnement Codex (domaine autorisé huggingface.co). Ne pas coller le jeton dans une conversation, le code ou les logs. Cela ne nécessite ni PC, serveur, API commerciale, ni MP3 dans le cloud. La conversion n'utilise que des signaux synthétiques ; le MP3 personnel restera exclusivement sur le téléphone.

Après accès : valider provenance/poids, BOS, projection 512→1024, parité sur plusieurs durées, conditionnement six couches (18 états cache/offset/pad), profil avec empreinte exacte du pack, génération française de phrases nouvelles à partir de références publiques autorisées, puis validation sur appareil et de ressemblance par le propriétaire. Un changement de tokenizer, checkpoint ou format invalide les profils. L'APK refuse pour l'instant de sélectionner une voix personnelle ou de créer un profil. Aucun export « safetensors » fictif n'est proposé.

## Licences

Le modèle officiel et les dérivés sont CC-BY-4.0 avec conditions officielles complémentaires relatives à l'usage autorisé et à la non-tromperie. Kyutai/ONNX Runtime : MIT ; la préparation d'export s'inspire du code MIT de netai369, attribution et texte intégral dans `scripts/pocket/NETAI-LICENSE.txt`. Les notices M004 restent applicables. Alba publique est CC-BY-4.0 ; les états de voix non commerciaux restent exclus du pack. L'accès au modèle complet nécessite l'acceptation personnelle de ses conditions.

## Tests et limites

23 tests JVM Pocket passent sans saut (11 antérieurs avec modèles réels, 6 file PCM, 1 benchmark et 5 conversion/qualité/persistance privée), plus 8 tests du moteur stable. Les tests de rééchantillonnage contrôlent le gain à 1 kHz et le rejet d'une composante 18 kHz avant passage à 24 kHz. Les tests de stockage vérifient le redémarrage, le remplacement corrompu/annulé et la suppression ; ils utilisent des octets synthétiques et un inspecteur simulé, pas un vrai décodeur MP3.

Une reconnaissance CPU Whisper tiny int8 des sorties de phrase complète à 1 et 2 threads retrouve le texte français initial ; cela vérifie un signal intelligible automatiquement, pas la fluidité perceptive, la ressemblance ou une exécution Android.

Quatre tests instrumentés Android sont **compilés mais non exécutés** : vrai décodage d'un MP3 synthétique généré par FFmpeg, corruption/annulation, lecture différée de 3 s sans sous-alimentation, arrêt et permissions hors ligne. La CI ne possède pas de téléphone. Les tests création/rechargement d'un profil et génération clonée restent bloqués par l'encodeur ; le test de capacité vérifie qu'ils restent indisponibles. Le protocole manuel sur Xiaomi doit précéder toute annonce de fluidité ou de clonage fonctionnel.
