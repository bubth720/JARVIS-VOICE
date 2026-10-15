# Essai 005G sur Xiaomi — naturel et artefacts, sans modifier 005F

005F fonctionne déjà : garder son APK et son profil. Installer uniquement
l'APK **005G signée** : `com.jarvis.voice.voicequality005g`. C'est une autre
application ; elle conserve le même certificat privé stable mais un sandbox
séparé. Ne pas installer une APK CI signée avec une clé éphémère.

## Préparation entièrement depuis Android

1. Réutiliser le **ZIP FP32 complet déjà validé** dans Téléchargements. L'importer
   dans 005G ; il ne faut ni reconstruire ni retélécharger les modèles.
2. Copier localement le profil 005F déjà créé, sans réencodage, comme ci-dessous.
   Cocher le consentement et utiliser « Importer un profil FP32 déjà créé ».
3. Choisir ce profil personnel. La sélection de la référence publique Alba reste
   explicite ; il n'y a aucun remplacement automatique de voix.
4. Commencer téléphone refroidi, volume média et route audio constants, économie
   d'énergie notée. Conserver un thread CPU et les gardes RAM/thermiques.

### Copier le profil depuis 005F, sans PC ni cloud

Si un fichier JVFP1 .bin a déjà été exporté localement, l'utiliser. Sinon, Termux
F-Droid et le débogage sans fil Android permettent une copie sur le **même
 téléphone**. Il faut généralement du Wi-Fi pour activer ce service Android.
Si le service ou `run-as` sont refusés, arrêter cette migration et conserver
005F ; ne pas désinstaller ni demander de transmettre le MP3/profil au développeur.

```sh
pkg install android-tools
termux-setup-storage
adb pair 127.0.0.1:PORT_ASSOCIATION
adb connect 127.0.0.1:PORT_CONNEXION
```

Les deux ports sont montrés dans les options développeur « Débogage sans fil ».
Entrer le code d'association dans Termux uniquement. Si loopback n'est pas
accepté, employer l'adresse du même téléphone indiquée par Android.

Vérifier qu'il existe exactement un state.bin :

```sh
adb shell "run-as com.jarvis.voice.voiceprototype005f sh -c 'ls no_backup/profiles/*/state.bin'"
```

S'il y en a plusieurs, arrêter. Sinon :

```sh
adb exec-out "run-as com.jarvis.voice.voiceprototype005f sh -c 'cat no_backup/profiles/*/state.bin'" > ~/storage/downloads/JARVIS-personal-FP32.bin
sha256sum ~/storage/downloads/JARVIS-personal-FP32.bin
adb shell "run-as com.jarvis.voice.voiceprototype005f sh -c 'sha256sum no_backup/profiles/*/state.bin'"
```

Comparer localement les deux empreintes, sans envoyer les chemins ni le fichier.
Importer le .bin dans 005G puis supprimer cette copie temporaire de
Téléchargements (dossier local non synchronisé). Le profil 005F demeure intact.
Désactiver le débogage sans fil après la copie. Cette disponibilité dépend du
Xiaomi ; elle n'a pas été testée à distance sur cet appareil.

## Premier A/B : ne changer que le nombre d'étapes

- Même texte, voix et graine 42 ; température 0,3, EOS −4 et queue « officielle ».
- Générer A (1 étape), puis B (3 étapes). Si texte, profil ou graine changent,
  la génération B est refusée jusqu'à la création d'un A correspondant.
- Réécouter A et B plusieurs fois : aucun modèle n'est rechargé. Alterner l'ordre
  des écoutes ; si possible, demander à une personne présente de choisir l'ordre
  sans le révéler, sans lui transférer le profil ou les fichiers.
- Après une écoute réellement audible, noter naturel et prosodie (0 mauvais,
  5 bon), intelligibilité (0 faible, 5 excellente) et artefacts métalliques
  (0 absents, 5 prononcés). Les 50 dernières notes restent sur le téléphone,
  liées au texte, réglages, PCM et format réellement écoutés. Aucun score n'est
  prérempli ni assimilé à une validation générale.
- Changer B vers 5 étapes puis 2 étapes, régénérer **B seulement** si le contexte
  n'a pas changé ; A demeure utilisable. Noter les mesures par variante : temps,
  CPU processus, PSS maximal échantillonné, batterie et thermique. Le nombre
  d'étapes peut changer la durée finale ; comparer aussi le RTF, pas seulement
  la durée totale. Répéter les mesures en inversant l'ordre des générations une
  fois A/B disponibles pour réduire les effets de cache et de chauffe.

Quatre phrases communes :

1. « Bonjour, cette nouvelle phrase fonctionne hors ligne. »
2. « Je garde ma voix et mes fichiers sur mon téléphone. »
3. « Demain, je prendrai le train pour Paris. »
4. « Vraiment ? Je pensais arriver plus tôt ! »

Répéter avec les graines 0 et 123 si la première comparaison est stable. Ensuite
seulement, comparer une température B 0,2 puis 0,5, à étapes fixées, puis EOS/queue.
Ne changer qu'un facteur à la fois. En cas de résultat moins bon, conserver le
mode A et 005F ; plus d'étapes ne garantit pas davantage de naturel.

## Isoler le PCM et sa conversion

Sans régénérer, sélectionner la sortie **FLOAT32**, puis réécouter exactement le
même A ou B. Comparer à PCM16 au même volume et sur la même route. Les deux
utilisent STATIC et la même borne d'amplitude, sans filtre. Vérifier pic, RMS,
écrêtage et SNR de conversion affichés. Si le rendu métallique est comparable
dans les deux sorties, cela oriente vers le PCM/source/modèle, sans être une
preuve absolue. Si seul PCM16 semble dégradé, conserver les diagnostics et
répéter. Si FLOAT32 est refusé par l'appareil, sélectionner explicitement PCM16 :
il n'y a pas de repli caché ni de réussite fictive.

## Référence vocale, dans un second temps

Écouter l'extrait local, analyser niveau/silences, éventuellement préparer une
portion **contiguë** de cinq secondes et l'écouter. Ce n'est pas une VAD ni une
mesure de bruit de fond ; musique et plusieurs locuteurs exigent une écoute.
Le profil existant n'est pas remplacé automatiquement. Pour cette phase, toute
création/remplacement est explicite et ne concerne que 005G. Ne pas mélanger
un changement de référence et un changement de sampler dans le même A/B.
Les extraits >15 s restent une étude numérique et ne sont pas activés.

ARRÊT reste immédiat. En cas de chauffe, faible mémoire, répétitions, phrase
tronquée ou erreur AudioTrack, interrompre l'essai et conserver les mesures.
Aucune amélioration de naturel n'est encore validée sur Xiaomi par le développeur.
