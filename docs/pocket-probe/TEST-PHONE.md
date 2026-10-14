# Validation sur téléphone — Mission 004

Ne pas fournir le MP3 personnel pour ces essais. La voix de test est **Alba, avec le modèle français 6 couches**. Ne pas conclure à la validité du clonage à partir de ces résultats.

| Essai | Action | Résultat attendu |
| --- | --- | --- |
| Installation | Installer Pocket Probe à côté de JARVIS VOICE et importer le ZIP officiel de la livraison | Deux applications distinctes ; pack vérifié ; aucun MP3 sélectionné. |
| Français | Mode économique, moteur Pocket. Lire « Bonjour, je suis JARVIS. Cette voix fonctionne sur ce téléphone. » | Phrase intelligible et complète. Diagnostic indiquant **Pocket**, pas Android TTS. |
| Cas courts | Lire « Oui. », « Non. », « Arrêt. », puis « Le café est prêt, vous pouvez arrêter la lecture. » | Aucun mot manquant, souffle à la place du texte, babillage ou coupure. Noter toute anomalie. |
| Arrêt | Pendant une phrase audible, toucher Arrêt ; recommencer une nouvelle lecture | Le son cesse rapidement et ne reprend pas. Le calcul s'annule et une nouvelle lecture fonctionne. |
| Arrêt pendant chargement | Fermer/rouvrir l'application, lancer Lecture puis Arrêt immédiatement | Pas de lecture différée. La création des sessions natives peut finir avant l'annulation : noter le délai. |
| Hors ligne | Activer mode avion, couper également Wi-Fi et données ; fermer/rouvrir l'application et relire une nouvelle phrase | Pocket fonctionne après rechargement du pack local, sans connexion. |
| Modes CPU | Faire trois lectures identiques en mode économique, puis trois sans ce mode | Mesures affichées pour 1 et 2 threads, sans promesse que 2 threads soient plus rapides. |
| Secours | Choisir explicitement Android TTS, lire et arrêter ; refaire en mode avion | Moteur Android affiché ; lecture locale avec la voix hors ligne installée. Ces résultats ne valident pas Pocket. |
| Non-régression | Ouvrir l'ancienne application JARVIS VOICE, lire, arrêter, refaire en mode avion | Les trois tests précédemment validés restent fonctionnels. |

Avant les comparaisons : téléphone débranché, luminosité/volume constants, applications lourdes fermées. Faire un premier passage à froid, puis les trois passages à chaud. Noter le modèle du téléphone, la version Android, les ABI et la taille de page affichés. Si la taille est 4096, ce téléphone ne valide pas l'exécution sur un système à pages 16384.

Relever ou photographier le diagnostic, sans ajouter de données privées : chargement du modèle, premier PCM depuis Lecture, premier PCM après chargement, lancement AudioTrack, durée de génération, durée audio, RTF/vitesse, PSS maximal observé. **RTF < 1** signifie que le calcul est plus rapide que la lecture. La génération exclut l'attente du lecteur audio ; le lancement AudioTrack ne mesure pas précisément le premier son entendu. Le PSS est échantillonné approximativement toutes les 250 ms et n'est pas un pic absolu garanti.

Faire ensuite une courte session de quelques minutes. Arrêter si la chauffe devient gênante. L'application refuse ou annule Pocket lorsque Android signale un état thermique sévère ; aucune mesure de température CPU ni consommation énergétique n'est promise dans cette version.

En cas d'échec, noter le moteur choisi, le message exact, si le pack venait d'être chargé, et si l'application s'est fermée. Ne pas envoyer le MP3, un profil vocal, les fichiers privés du téléphone ou un journal complet. Un plantage natif peut fermer l'APK sans afficher une exception Kotlin.

La validation attendue est : français correct, Arrêt et reprise fiables, rechargement hors ligne, ressources acceptables sur votre téléphone et secours explicite vérifié. L'intégration dans JARVIS VOICE et l'import MP3 restent différés jusqu'à votre retour.
