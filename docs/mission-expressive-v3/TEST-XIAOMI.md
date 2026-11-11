# Un seul essai Xiaomi — laboratoire V3 (après CI validée)

**Ne pas désinstaller 005J.** Il est signé et isolé de `com.jarvis.voice.studio.expressivev3`. Le pilote expérimental est une deuxième application. Son installation n'est ni un remplacement ni une migration.

1. Télécharger uniquement l'APK de l'artefact CI de la branche `feat/voice-expressive-v3-android` **après vérification de la campagne Android, du manifeste, de l'empreinte et de la signature**. Extraire le ZIP, installer le fichier APK. Aucun PC, USB ou Termux requis.
2. Importer en local le ZIP FP32 original, puis une copie de ton archive `.jvi` chiffrée. Ne jamais envoyer l'archive, le modèle, le mot de passe ou l'identifiant privé à GitHub.
3. Dans Mes voix, confirmer explicitement le profil personnel B. 005J conserve sa propre sandbox et ses propres données intactes.
4. Dans Parler → Options, appuyer sur **Préparer et écouter « Je prépare ma réponse. »**. Le calcul lourd doit se dérouler une fois ; écouter la phrase avant stockage chiffré. Si la génération dure plus de 3s de PCM, le cache refuse honnêtement le clip ; il faut employer une phrase plus courte dans une mission future, pas augmenter la limite silencieusement.
5. Activer « Réaction rapide » ; cela vaut seulement pour la session d'app en cours. Envoyer une phrase suffisamment longue (mais <=300 caractères et <=50 tokens par segment). Si le calcul prend >1,2s et que le profil/réglages restent identiques, la courte réaction peut commencer. Le PCM final ne peut démarrer qu'après arrêt et libération du lecteur de réaction.
6. Noter les temps : déclenchement→réaction, déclenchement→première lecture de la vraie réponse, fin complète ; diagnostics Avancé (PSS, CPU, thermique et batterie). Interrompre une fois en cours de route. Rien n'est envoyé.
7. Désactiver et effacer les clips si besoin. Réouvrir 005J et confirmer que B marche toujours comme avant. En cas d'erreur, garder les archives locales et ne pas supprimer 005J.

**Attention :** un clip préparé pour d'autres réglages/profil ne sera pas joué. Les tests CORE–VOICE intégrés et les émotions natives exigent des missions distinctes et une preuve Android réelle. Un temps court jusqu'au filler n'est pas une diminution de la latence de génération.
