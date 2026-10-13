# Licences et conditions — vérification du 13 octobre 2026

Les licences du code, des poids et des références vocales sont distinctes. Le fait qu'un moteur soit open source ne rend pas toutes les voix ou tous les modèles librement réutilisables.

| Élément | Vérification | Conséquence |
|---|---|---|
| Code Pocket TTS 3.3.0 | Texte de la licence MIT lu dans le dépôt officiel. Version PyPI installée ; tag v3.3.0 = `3dbee45d343d7dddd0d105468d17f8dcba14db3e`. | Conserver les notices MIT lors d'une redistribution du code concerné. |
| Poids français avec clonage | La configuration du paquet installé cible `kyutai/pocket-tts`, révision `d0ebc5deb50bc6b225f686782f124dd37e2dca0a`, fichier `languages/french/model.safetensors`. Métadonnées de cette révision consultées : CC BY 4.0 ; accès après acceptation des conditions (`gated: auto`). | Attribution requise lors de la redistribution ; accès authentifié nécessaire pour télécharger les poids. Les conditions interdisent notamment le clonage sans consentement licite explicite et les usages trompeurs/illégaux. |
| Poids français sans clonage | Configuration du paquet : `kyutai/pocket-tts-without-voice-cloning`, révision `8843db76457a91db32077edf8dfcd1c0e3e755fd`. Carte de cette révision consultée : CC BY 4.0, accès public sans compte. | Attribution et conditions d’usage à respecter. Ce secours ne valide pas le clonage personnel. |
| Référence française Estelle | Référence `unmute-prod-website/developpeuse-3.wav`. La carte des voix annonce CC0 pour les enregistrements propres de ce dossier, hors trois exceptions listées qui ne concernent pas ce fichier. | Référence autorisée pour le test. Son utilisation ne remplace pas le MP3 personnel de l’utilisateur. |
| MP3 personnel | L'utilisateur indique que son utilisation pour le clonage est autorisée ; le fichier n'est pas encore fourni. | Utiliser uniquement la référence autorisée. L'extrait et le profil restent privés. |
| Code sherpa-onnx | Métadonnées du dépôt officiel : Apache-2.0. | Vérifier les notices lors de son intégration future. Cette licence ne définit pas celle des modèles. |
| Ancien export Pocket ONNX sherpa du 26 janvier 2026 | Archive officielle int8 téléchargée uniquement pour étude ; digest SHA256 du release vérifié. `LICENSE` contient CC BY 4.0, tandis que `README.md` indique « It is for non-commercial ». | Ambiguïté constatée : demander une clarification pour cet artefact avant redistribution/utilisation commerciale. Ne pas transférer ses conditions supposées aux nouveaux poids français. Cet export n'a pas été exécuté. |
| PyTorch CPU 2.8.0 | Métadonnées installées : BSD-3-Clause. | Conserver les notices si des bibliothèques sont redistribuées. |
| NumPy / SciPy / psutil | Métadonnées NumPy : licences BSD/MIT et notices tierces ; SciPy et psutil : famille BSD. | Respecter les notices des distributions exactes. |
| ffmpeg local | `ffmpeg -L` indique GPL v2 ou ultérieure pour le binaire disponible ici. Le projet l'appelle comme outil externe, sans le distribuer dans l'APK. | Vérifier la configuration et les obligations du binaire si une future version le redistribue. |

Sources à consulter :
- [Pocket TTS v3.3.0](https://github.com/kyutai-labs/pocket-tts/tree/v3.3.0) et [licence du code](https://github.com/kyutai-labs/pocket-tts/blob/v3.3.0/LICENSE).
- [Carte des poids avec clonage](https://huggingface.co/kyutai/pocket-tts) et [carte du secours sans clonage](https://huggingface.co/kyutai/pocket-tts-without-voice-cloning).
- [Références vocales et leurs licences](https://huggingface.co/kyutai/tts-voices).
- [Release officiel sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models).

Archive ONNX étudiée : `sherpa-onnx-pocket-tts-int8-2026-01-26.tar.bz2`, SHA256 `2f3b88823cbbb9bf0b2477ec8ae7b3fec417b3a87b6bb5f256dba66f2ad967cb`. Aucun poids ni audio issu de cette archive n'est inclus dans le dépôt.

Le téléchargement initial des modèles n'envoie pas le MP3 ou le texte à Hugging Face. Une fois les poids et le tokenizer disponibles dans le cache, la synthèse est locale. Ne pas désactiver TLS ni contourner une demande d'accès/acceptation des conditions d'un modèle.

Attribution prévue pour les modèles : « Kyutai — Pocket TTS, poids français utilisés sans entraînement supplémentaire, licence CC BY 4.0 », avec le lien de la révision concernée et la mention des transformations éventuelles lors d’un export futur. Les obligations des voix de référence doivent être vérifiées séparément.

L’accès réseau aux métadonnées est redevenu fonctionnel pendant cette session. Les gros fichiers sont redirigés vers `us.aws.cdn.hf.co` ; ce domaine a été ajouté au brouillon d’environnement. Aucun jeton Hugging Face existant n’a été trouvé. La déclaration sécurisée `HF_TOKEN` est enregistrée pour l’accès personnel au modèle avec clonage ; elle ne contient aucune valeur.
