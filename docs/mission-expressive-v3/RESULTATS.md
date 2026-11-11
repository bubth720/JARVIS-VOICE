# JARVIS VOICE — EXPRESSIVE V3 ANDROID (lab isolé)

**Statut initial : code intégré au projet Android, validation CI et physique à renseigner après exécution. Ne jamais transformer un build en preuve de naturel.**

## Périmètre réellement développé

- Branche `feat/voice-expressive-v3-android` issue de `mission/m50-integrated-beta-20261029` à `642d9c19bcba35e5ffe686403d50c65aa9dc992a`.
- `-PvoiceExpressivePilot=true` construit une application distincte `com.jarvis.voice.studio.expressivev3` ; sans cette propriété, identifiant historique 005J inchangé.
- StudioController continue d'utiliser **le moteur officiel** `StudioBackend`/`ExpressionRenderer`/`QualityEngine`/ONNX et le profil activé explicitement. Pas de backend de test dans l'APK, pas de réencodage de B ni d'accès à 005J.
- Dans l'écran Parler → Options : préparation volontaire puis écoute d'une courte phrase `Je prépare ma réponse.`. La préparation fait une **vraie synthèse Pocket** si le propriétaire a déjà importé localement le pack et un profil, puis n'enregistre le PCM que si l'écoute a réussi.
- L'audio pré-généré est chiffré avec AndroidKeyStore AES-256-GCM, dans `noBackupFilesDir`. L'authentification lie identité, profil, SHA du conditionnement, version moteur, pack, texte et réglages graine/LSD/température ; mauvaise voix/réglage/ciphertext = refus. Trois phrases sont prévues dans le contrat (`THINK`, `CHECK`, `SPEECH`) ; seule `SPEECH` est utilisée automatiquement dans l'écran Parler, jamais un faux état de CORE.
- Opt-in **temporaire**, désactivé après relancement ; seuil local à 1200 ms de génération en cours et cooldown 20 s. Aucune promesse « dans deux secondes ». Réaction lue sur un worker audio léger distinct sans charger un autre modèle. La génération continue sur le worker original.
- Lorsque le PCM final est prêt, demander l'arrêt du clip, attendre la fermeture de son AudioTrack dans une fenêtre de 2500 ms ; si cela n'est pas prouvé, **refuser la lecture finale plutôt que superposer deux voix**. Arrêt utilisateur, annulation, perte de focus, suppression de voix ou audio invalident la réaction. Pas de lecture TTS Android fallback automatique.
- Les protections natives, seuils RAM, thermique, AudioFocus/AudioTrack, EOS et paramètres de génération ne sont pas affaiblis. Les métriques existantes d'OperationMetrics couvrent CPU/PSS échantillonné 250ms, thermique, batterie, durée et diagnostics AudioTrack.

## Ce que ce code ne résout pas encore

- **Il ne réduit pas le calcul neuronal Pocket TTS.** Il peut seulement diminuer l'attente silencieuse perçue si un clip chiffré existe et si un lecteur Android a réellement démarré.
- **Il ne raccourcit pas un LLM CORE qui réfléchit 30 secondes**. La connexion CORE–VOICE et l'arbitre natif ne sont toujours pas autorisés/qualifiés. Une temporisation CORE exige de vrais événements de progression CORE et une adaptation opt-in séparée.
- **Il ne crée pas des émotions natives Pocket ni des soupirs organiques.** Le projet autonome EXPRESSIVE V2 reste une préparation logicielle. La hauteur F0, la vitesse syllabique, le rire et la compassion acoustiques ne sont pas prouvés ; les étiquettes émotionnelles ne doivent pas être annoncées comme sonores.
- Le profil B personnel est inconnu au développeur et **inaccessible depuis le laboratoire**. Le propriétaire peut restaurer **une copie** de son .jvi et du ZIP FP32 dans la nouvelle sandbox, sans désinstaller 005J.
- La signature issue de GitHub Actions est une signature de laboratoire, pas celle de 005J ; ne jamais installer comme mise à jour de 005J. Pas d'installation physique ni de mesure Xiaomi à ce jour.

## Tests et preuves attendues

- JVM : `QuickReactionTimingTest` contient 14 tests de refus/bornes, en plus des suites historiques.
- Android : `StudioQuickClipTests` teste le vrai AndroidKeyStore, authentification de bon profil et bonnes options, ciphertext altéré, effacement et longueur.
- CI dédiée : `.github/workflows/voice-expressive-v3.yml`, build Android ARM64, lint, audit d'APK, JVM et instrumentation API35 ; artefact APK seulement après réussite du job.
- Les tests Android AndroidKeyStore synthétiques ne prouvent pas la qualité vocale Pocket ni la confidentialité absolue d'un appareil compromis.

## La campagne finale de tests utilisateur

**Ne pas demander maintenant des tests physiques fragmentés.** Une fois les tests CI verts, la signature/permissions vérifiées et le parcours d'import documenté, un essai unique sur Xiaomi mesurera : temps pression→réaction audio, temps pression→PCM final, temps pression→son final, durée totale, PSS, CPU, température/batterie avant-après, lecture simultanée (doit être impossible), coupure après Arrêt, conformité du profil B. Son ressenti et la reconnaissance humaine des émotions seront recueillis, non préremplis.

## Non-fusion

**Ne jamais fusionner dans main ni remplacer l'APK 005J** sans validation du propriétaire. Régression potentielle : extensions partagées de StudioController, mais le nouveau `applicationId` limite les risques sur téléphone. Les anciens garde-fous CI d'empreintes peuvent refuser ces ajouts ; distinguer cet échec de la CI dédiée et ne pas désactiver les contrôles historiques.
