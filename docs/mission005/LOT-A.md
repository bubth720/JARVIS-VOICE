# Mission 005 — lot A : restitution française

Le Xiaomi 23073RPBFG Android 15 / ARM64 / pages 4 Ko a été testé par l'utilisateur : Alba génère du français mais la lecture M004 saccade fortement. Android TTS est excellent. Les résultats ci-dessous sont des mesures cloud, **pas** des mesures de ce téléphone.

## Diagnostic

M004 démarrait AudioTrack avant la première écriture, avec 24 000 **octets**, soit seulement 250 ms de PCM float mono à 24 kHz. Les premiers blocs représentent 160 ms, puis 320 ms. Les écritures et ONNX partageaient le même thread : le remplissage pouvait bloquer la génération, puis un calcul lent pouvait vider le buffer. Les sous-alimentations réelles n'étaient pas comptées ; ces observations expliquent un risque, pas encore la cause physique démontrée.

Un contrôle CPU sur 14 latents synthétiques compare le décodeur INT8 en une passe et en blocs 2/4/4/4 : même longueur (26 880 échantillons), écart relatif L2 0,107485, maximum absolu 0,167457. Les sorties ne sont donc pas acoustiquement identiques. Cela ne prouve ni un trou audio ni une meilleure qualité perceptive d'une stratégie. Le script `scripts/pocket/check_decoder_continuity.py` conserve ce diagnostic au lieu de prétendre à une équivalence.

## Correction

Le mode par défaut génère tous les latents et décode la phrase complète **avant** restitution. Une file PCM bornée à 20 s (~1,83 Mio) et un thread audio indépendant préservent chaque échantillon. AudioTrack dispose de 2 s minimum et démarre après 1 s préchargée (ou à la fin pour une phrase courte). Aucun silence artificiel, saut ou remplacement Android TTS n'est ajouté.

La lecture progressive reste explicitement expérimentale : départ avec 2 s de réserve et production mesurée RTF ≤ 0,75 ; si la file se vide, pause et rechargement. Une sous-alimentation reste possible si la vitesse se dégrade après le départ. Pour la stabilité, utiliser le mode différé. Dans la version Reference, la réserve adaptative augmente jusqu’à 10 s selon deux fois le pire intervalle de production observé ; l’annulation thermique est également échantillonnée pendant ONNX.

Arrêt annule ONNX via RunOptions, la file PCM et AudioTrack pendant chargement/génération/lecture. Les sessions sont réutilisées à thread constant et libérées en arrière-plan. Le mode économique reste 1 thread ; le mode normal utilise 2 threads, sans changement de modèle ni qualité de quantification. L'état thermique sévère interrompt la génération.

## Mesures CPU cloud

Phrase : « Bonjour, je suis JARVIS. Cette voix fonctionne sur ce téléphone. » ; seed 42 ; 3,84 s audio ; ONNX Runtime 1.27.0 CPU. Même moteur et sessions réutilisées pour comparer les stratégies. Un seul passage par configuration, sensible à la charge cloud ; aucune mesure AudioTrack sur Linux.

| Threads | Décodage | Génération ms | RTF | Premier PCM ms | Décodeur ms |
|---|---|---:|---:|---:|---:|
| 1 | Blocs M004 | 1361 | 0,355 | 272 | 549 |
| 1 | Phrase complète | 1136 | 0,296 | 1136 | 429 |
| 2 | Blocs M004 | 1097 | 0,286 | 165 | 436 |
| 2 | Phrase complète | 1051 | 0,274 | 1051 | 352 |

Le premier PCM différé est volontairement tardif. Le premier son réel dépend ensuite du téléphone. Les diagnostics affichent premier PCM, démarrage AudioTrack, premier avancement du compteur matériel (estimation, pas mesure acoustique), temps ONNX par composant, appel maximal, intervalle entre blocs, RTF, PSS échantillonné 250 ms, état thermique maximum, sous-alimentations et rechargements. Les valeurs RAM/température/consommation du Xiaomi restent à mesurer.

## Validation avant lot B

17 tests JVM Pocket, dont les 11 contrôles antérieurs avec les vrais modèles, 5 tests de file/arrêt/lecture et 1 comparaison 1/2 threads ; 8 tests du moteur stable ; compilation et lint Android réussis. Vérification de signature, permissions, ARM64 et alignement ELF/APK 16 Ko. Pas de téléphone ou émulateur connecté : AudioTrack et le gain perceptif nécessitent la validation physique.

APK lot A conservé séparément, version 0.2.0-lotA. Même pack Alba français que M004, aucun téléchargement supplémentaire nécessaire. Ni `app/` stable ni JARVIS CORE ne sont modifiés.
