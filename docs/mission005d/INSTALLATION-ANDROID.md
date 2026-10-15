# Installer depuis Android, sans PC

Artefact installable déjà disponible : https://github.com/bubth720/JARVIS-VOICE/actions/runs/38580365074/artifacts/11901734843 (job de build réussi ; un test audio séparé a échoué, détails dans RESULTATS.md).

1. Ouvrir la PR https://github.com/bubth720/JARVIS-VOICE/pull/5 → Checks → exécution **Pocket Probe**, job **build-and-inference** réussi → Artifacts → **jarvis-pocket-probe-apk** (connexion GitHub requise, conservation 14 jours).
2. Télécharger et extraire le ZIP enveloppe. Installer **pocketProbe-debug.apk**, version **0.4.0-mission005d**, nom **JARVIS Pocket Probe**, applicationId **com.jarvis.voice.pocketprobe**. L'APK androidTest est réservée aux tests d'ingénierie. Ne pas installer l'artefact jarvis-voice-debug pour cette mission.
3. Si Android refuse la mise à jour pour signature différente, la désinstallation de **Pocket Probe seulement** efface pack et référence privés. Conserver le MP3 original sur le téléphone avant toute désinstallation ; réimporter ensuite pack et MP3. JARVIS VOICE stable et CORE sont indépendants. Ne pas désactiver Play Protect globalement.
4. Conserver le pack français Alba actuel. Si nécessaire, prendre **pocket-fr-6l-alba-model** dans la même exécution ; extraire l'enveloppe GitHub, puis importer le ZIP modèle intérieur intact via le bouton de Pocket Probe. Ce pack ne contient pas d'encodeur.
5. Vérifier mode économique activé, lecture progressive désactivée. Importer le MP3 autorisé déjà sur le téléphone, convertir début 0/durée 5, puis 0/10 et écouter. Suivre `TESTS-TELEPHONE.md` en mode avion.

L'APK ARM64 finale sans modèles est compilée localement (29152343 octets), signature, permissions et alignement 16 Ko vérifiés dans `APK-FINALE-VERIFICATION.json` ; le rapport `APK-VERIFICATION.json` concerne l'intermédiaire MP3 conservé. Le certificat cloud diffère potentiellement de celui GitHub Actions ; utiliser le rapport inclus dans chaque artefact pour vérifier l'APK effectivement téléchargée. Ne pas comparer le SHA d'une compilation cloud à une compilation CI distincte.

Aucune manipulation du jeton Hugging Face n'est nécessaire pour installer cette APK ou utiliser Alba. Aucune connexion Internet n'est nécessaire dans l'application après import du pack. La création de profil personnel est désactivée tant que l'encodeur n'est pas validé.
