# Sécurité, confidentialité et licences

Le MP3 personnel n'a jamais été demandé ni utilisé dans Codex. Les fixtures MP3 sont des sinus créés par FFmpeg (12 s, stéréo 44,1 kHz, mono 44,1 kHz, stéréo 48 kHz). WAV et MP3 privés restent sous noBackupFilesDir. Pas de permission INTERNET, RECORD_AUDIO ou stockage global. Sauvegarde et transfert Android exclus. La suppression efface les fichiers privés ; aucun profil n'est créé dans cette version.

HF_TOKEN est utilisé exclusivement dans l'en-tête Authorization vers huggingface.co via le proxy. Aucune sauvegarde dans un login local, code, commit, log, artefact ou PR. Les rapports ne contiennent ni identité de compte ni URL signée. Le CDN recevrait uniquement l'URL signée, jamais Authorization HF. Aucun élargissement de politique ou contournement du proxy n'est réalisé par les scripts.

Le modèle officiel Kyutai Pocket TTS à la révision d0ebc5deb50bc6b225f686782f124dd37e2dca0a est annoncé CC-BY-4.0. La fiche officielle récupérée présente également les conditions gated : consentement explicite et légal pour le clonage ; interdiction d'usages illicites, trompeurs, frauduleux ou portant atteinte à la vie privée, notamment présentation d'une synthèse comme un véritable enregistrement. Empreinte de la fiche lue : c01f4a60dca73aa844473181096618fe9b3bf6a148986c8c368190ba03627054. Source : https://huggingface.co/kyutai/pocket-tts/blob/d0ebc5deb50bc6b225f686782f124dd37e2dca0a/README.md.

CC-BY-4.0 demande attribution, lien vers la licence et indication des modifications pour une redistribution. La livraison actuelle ne redistribue aucun poids complet gated ni encodeur dérivé. Une autorisation de lecture HF ne vaut pas validation automatique d'un canal public de distribution ; les conditions et l'accès devront être réexaminés avant publication éventuelle d'un pack complet.

Le pack Alba existant et ses notices/licences épinglées sont conservés (`docs/pocket-probe/LICENSES.md`, notices APK). Aucune autre voix non commerciale n'est ajoutée. ONNX Runtime et le code Kyutai sont MIT. Attribution netai369 pour l'exporteur maintenue, texte MIT dans `scripts/pocket/NETAI-LICENSE.txt`.

GitHub Actions ne reçoit pas le secret Codex : seulement code, signaux synthétiques, pack Alba public déjà épinglé, APK et rapports techniques. Aucun poids gated ni dérivé protégé ne figure dans les chemins upload-artifact. `app/` stable et JARVIS CORE ne sont pas modifiés.
