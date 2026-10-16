# Compatibilité 005I → 005J

APK distincte `com.jarvis.voice.voicestudio005j`, min26 / target35, sandbox et stockage noBackup propres. 005D/F/G/H/I restent installées, inchangées et utilisables. Aucun accès direct à leurs fichiers privés.

Transfert volontaire, entièrement depuis Android : même ZIP FP32 existant, puis archive JVIENC01 créée dans 005I (ou H). AES-256-GCM / PBKDF2 inchangés, authentification avant ZIP/extraction/commit, UUID et hashes vérifiés, aucun profil réencodé, format JVFP1 / 18 états inchangés. Une collision d’UUID refuse l’import sans écrasement. Mot de passe erroné ou tag altéré ne changent pas le coffre. Les étiquettes et métadonnées sont conservées, et l’absence de WAV est indiquée si non inclus ou supprimé après encodage.

Le profil B réel reste connu du propriétaire seulement. Dans 005J, une confirmation locale est requise avant synthèse, même si l’archive portait déjà un profil actif. Aucun basculement de voix ou migration forcée. Garder une sauvegarde chiffrée et 005I pour revenir simplement à l’ancienne application.

Le moteur, la tokenisation, le sampler, les poids et les bibliothèques de 005I sont réutilisés sans modification. Les pauses explicites et leurs refus sont ceux de l’orchestrateur 005I. Pas de pitch/timestretch ni émotion native. Un thread ONNX, une file lourde, admission 2 Gio, thermique, batterie mesurée et lecture STATIC conservés.

La restauration physique Xiaomi 005I→005J doit être essayée par le propriétaire ; une fixture synthétique Cloud n’est pas son B. Si elle échoue, arrêter, conserver 005I et son archive ; ne pas transmettre l’archive personnelle au Cloud.
