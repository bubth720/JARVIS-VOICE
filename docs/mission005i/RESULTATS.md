# 005I — résultats progressifs

Phase A : contrat v1, persona sans tenseur, liaison immuable au profil, politique pure et priorité sans préemption. Tests JVM ciblés : 4/4 réussis, aucun saut (log privé `work/005i-phase-a.log`). Aucun code CORE, identitéRuntime ou ancien moteur/app modifié. La compilation initiale a révélé une ambiguïté d'import VoiceProfile, corrigée par import explicite avant validation.

Les phases B/C et l'écoute Xiaomi ne sont pas encore validées à ce stade du commit A.

Phase B : 9/9 tests JVM passent, zéro saut, avec pack FP32 privé existant et référence Alba publique autorisée. Deux phrases / graines 7 et 42 / LSD 1 et 3 : NEUTRAL et CONVERSATION sont bit-identiques au backend 005G inchangé (erreur max 0). Voir PUBLIC-NEUTRAL-PARITY.json. Ceci prouve l'absence d'effet acoustique inventé et la préservation du rendu, pas une amélioration perceptive. Les pauses explicites, ambiguïtés, invariance texte/liaison/réglages, non-finis, EOS, bornes et annulation passent aussi. Pas de reconstruction du pack.

Phase C, avant CI Android : application `com.jarvis.voice.voiceexpression005i`, version 0.5.0-mission005i-experimental / code 1, installable parallèlement à 005H. Import local JVIENC01 sans réencodage, consentement et confirmation explicite du profil requis, reprise des fonctions de bibliothèque/encodage/archives/lecteur 005H dans le nouveau module. Comparaison A/B même profil strict ; notes d'expressivité dans caches privés, mesures CPU/PSS/thermique/batterie de génération conservées avant lecture. Texte exclu des journaux, sauvegardes Android et apprentissage IME demandés désactivés. Aucune permission réseau/microphone.

Tests locaux : expressionRuntime 12 réussis / 0 échec / 0 saut ; identityRuntime 19/0/0 ; pocketRuntime 35/0/11. Build ARM64 et AndroidTest + lint : succès, 0 erreur, 24 avertissements. Les pauses sont aussi comparées au vrai backend : concaténation de deux phrases entières + silence exact de 250 ms bit-identique, 67440 échantillons / 2,81 s, sans non-fini ni écrêtage. Mesures Cloud non assimilées à Xiaomi. Les tests Android ne sont pas encore exécutés à ce commit.

PRESERVATION.json vérifie les anciens modules et livraisons contre le commit 005H `1d0285e`, le ZIP privé original et les dix fichiers du pack. Aucun poids, état vocal, archive privée ou clé ajouté aux livrables. Le profil B personnel n'a jamais été accessible au Cloud. Le protocole TEST-XIAOMI.md reste nécessaire ; aucun contrôle émotionnel ou migration physique revendiqué.

CI initiale du code `49b981ae500382e4311f42506c394d54671a2f91` : 005I 31/31 Android, 005H 18/18, 005F 10/10, 005G 14/14 ; zéro saut/échec dans ces suites. Rapports XML téléchargés et comptés. Le build/audit 005I et Android principal passent. Le défaut connu 005D est reproduit sans changement : 4/5, underrun terminal AudioTrack `count=1 frames=72000 submitted=72000`, attendu 0 obtenu 1 ; build/inférence 005D passent.

Revue finale : séparation explicite des capacités natives Pocket (toujours ensemble vide) et des pauses de l'orchestrateur local (champ `pause` annoncé seulement s'il est effectivement appliqué). Tests d'absence de capacité native et des demandes non prises en charge ajoutés aux scénarios JVM/Android existants. JVM 12/12 avec parité PCM réelle, build et lint repassent. Une nouvelle CI Android est requise pour cette précision avant livraison finale.
