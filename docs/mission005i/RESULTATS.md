# 005I — résultats progressifs

Phase A : contrat v1, persona sans tenseur, liaison immuable au profil, politique pure et priorité sans préemption. Tests JVM ciblés : 4/4 réussis, aucun saut (log privé `work/005i-phase-a.log`). Aucun code CORE, identitéRuntime ou ancien moteur/app modifié. La compilation initiale a révélé une ambiguïté d'import VoiceProfile, corrigée par import explicite avant validation.

Les phases B/C et l'écoute Xiaomi ne sont pas encore validées à ce stade du commit A.

Phase B : 9/9 tests JVM passent, zéro saut, avec pack FP32 privé existant et référence Alba publique autorisée. Deux phrases / graines 7 et 42 / LSD 1 et 3 : NEUTRAL et CONVERSATION sont bit-identiques au backend 005G inchangé (erreur max 0). Voir PUBLIC-NEUTRAL-PARITY.json. Ceci prouve l'absence d'effet acoustique inventé et la préservation du rendu, pas une amélioration perceptive. Les pauses explicites, ambiguïtés, invariance texte/liaison/réglages, non-finis, EOS, bornes et annulation passent aussi. Pas de reconstruction du pack.
