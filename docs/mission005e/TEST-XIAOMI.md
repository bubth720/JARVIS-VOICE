# Mission 005E — test personnel simple sur Xiaomi

**Prototype expérimental. La ressemblance vocale et le fonctionnement sur votre
Xiaomi ne sont pas validés.** Garder Pocket Probe 005D installé : cette APK utilise
`com.jarvis.voice.voiceprototype005e`, un identifiant et un espace privé distincts.
Ne pas désinstaller 005D.

## Préparer une seule fois

1. Télécharger l'artefact **Voice Prototype 005E**, extraire le ZIP et installer
   `voicePrototype-debug.apk`, pas l'APK AndroidTest. Android ARM64, API ≥26.
2. Récupérer **privément** `JARVIS-005E-FP32-PRIVATE-REPRODUCED.zip` dans l'espace
   Codex Cloud (environ 225 Mio). Transférer ce ZIP par USB au Xiaomi. Ne pas le
   publier dans GitHub, une messagerie publique ou un dossier partagé.
3. Ouvrir **JARVIS Voix 005E EXPÉRIMENTAL**, accepter les conditions affichées puis
   importer directement ce ZIP. Le pack INT8 de 005D est refusé. Le ZIP doit être
   le pack lui-même, pas un ZIP contenant un autre ZIP.
4. Prévoir au moins 1,2 Gio de stockage disponible pour l'import et 2 Gio de RAM
   disponible pour lancer les opérations FP32. Fermer les autres applications et
   laisser refroidir le téléphone. Le pack décompressé dépasse 500 Mio ; la mémoire
   et la vitesse sur Xiaomi restent à mesurer.

Le pack n'est pas dans l'APK publique. Les modèles sont vérifiés par taille et
SHA256 contre le manifeste intégré à cette APK. Une conversion par une autre
version d'ONNX peut produire un ZIP refusé : ne pas modifier le manifeste sur le
téléphone pour contourner ce contrôle.

## Créer et comparer

1. Enregistrer **sur le téléphone**, avec son enregistreur local, 10 à 15 secondes
   de votre voix seule, sans musique ni autre voix. Par exemple : « Je prépare une
   référence vocale pour mon téléphone. Cette expérience reste entièrement locale. »
   Utiliser un MP3, ou un WAV PCM16/float32 mono à 24 kHz. Le prototype ne demande
   pas la permission microphone. Ne pas envoyer cet enregistrement dans Codex.
2. Importer ce fichier depuis **Sur cet appareil**. Choisir un début et une durée
   de 5 à 15 secondes. Écouter l'extrait privé pour vérifier qu'il contient la bonne
   voix et qu'il n'est pas tronqué, saturé ou presque silencieux.
3. Cocher la confirmation de consentement puis **Créer / remplacer mon vrai profil
   vocal**. Le prototype encode le WAV, inclut BOS une fois et persiste 18 états
   FP32 liés au pack. Il n'enregistre pas le WAV sous un nom de profil fictif.
4. Saisir une phrase **absente de l'extrait**, par exemple : « Demain, je prendrai
   le train pour Paris. Mes fichiers restent sur mon téléphone. » Si elle dépasse
   50 jetons, raccourcir. Garder la graine 42.
5. Choisir **Référence publique Alba — recalculée FP32**, générer et écouter.
   Puis choisir **Mon profil personnel — EXPÉRIMENTAL** et générer le même texte
   avec la même graine. Aucune voix n'est substituée automatiquement.
6. Refaire la comparaison avec les graines 0 et 123 et une seconde phrase courte.
   Pour la référence publique Selfie, le contrôle ASR a relevé des phrases moins
   intelligibles selon la graine : une seule réussite ne suffit pas à valider le
   profil, et changer la graine ne doit pas masquer des défauts persistants.

Évaluer séparément : tous les mots sont-ils compréhensibles ? La phrase finit-elle
complètement ? Y a-t-il du bruit, des répétitions, des syllabes manquantes ou une
voix instable ? La voix évoque-t-elle votre timbre, indépendamment du texte et de
la vitesse ? Attribuer de 1 à 5 à l'intelligibilité et à la ressemblance pour
chaque graine. Conserver des notes locales ; aucun MP3/WAV ou profil n'est à envoyer.

## Contrôler les fonctions et les ressources

- Tester en **mode avion** après import du pack : création et lecture doivent rester
  disponibles. L'APK ne dispose pas de permission INTERNET.
- Fermer puis rouvrir 005E : le profil doit rester sélectionnable, sans réencodage.
- Pendant la génération puis la lecture, appuyer **ARRÊT**. L'audio est arrêté
  immédiatement ; ONNX reçoit une demande de terminaison. Le chargement natif
  d'un modèle n'est pas interruptible à tout instant : son annulation est vérifiée
  aux frontières et son délai observé est affiché, sans annoncer une latence nulle.
- Relever les mesures affichées : chargement+génération, durée audio/RTF, CPU,
  PSS maximal échantillonné, état thermique, température batterie et variations des
  compteurs de charge/énergie. « Indisponible » ou une variation nulle ne prouve pas
  une consommation nulle. Débrancher l'USB avant les comparaisons de consommation.
- Ne pas insister si le téléphone chauffe. Le prototype s'arrête en état thermique
  sévère ou mémoire basse ; ces protections ne remplacent pas l'évaluation physique.
- **Supprimer mon profil et ma référence** efface les fichiers de 005E. Vérifier que
  le profil devient indisponible. Les fichiers, la voix Alba et l'APK de 005D restent
  intacts. Les sauvegardes/transferts automatiques de 005E sont désactivés.

Ce protocole valide d'abord un fonctionnement expérimental local. Une synthèse
doit toujours être présentée comme artificielle ; ne pas la présenter comme un
véritable enregistrement.
