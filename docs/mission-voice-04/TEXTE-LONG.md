# Texte long local versionné

`LongFormSpeechRequestV1` est interne à VOICE-04. Les contrats fermés CORE/005K/Binder, les API SDK antérieures, les limites 300 caractères/50 tokens et leurs refus restent inchangés.

Entrée 1–1800 unités UTF-16, au plus six jobs ; chaque job <=300 unités, <=50 tokens du **vrai FrenchFrontend.prepare**, PCM <=480000 échantillons/20 s. Une phrase complète par job. Aucune coupure silencieuse dans un mot, nom ou phrase trop longue. Plus de six phrases : refus BATCH_REQUIRED ; l'utilisateur choisit des lots distincts. Aucun mode illimité.

La segmentation automatique conserve les espaces entre phrases dans le segment précédent. Les offsets [start,end[ partitionnent toute l'entrée, y compris espaces initiaux/finals, accents et retours à la ligne. La concaténation est vérifiée strictement. Les abréviations, sigles, ponctuations collées, citations/parenthèses et décimales ambiguës sont refusés. L'utilisateur peut choisir une phrase par ligne pour exprimer ses coupes ; le texte n'est jamais réécrit. La normalisation FrenchFrontend historique reste distincte de cette garantie orthographique.

La prévisualisation tokenise **tous** les jobs avant tout chargement ONNX/son. Le texte est conservé uniquement en RAM dans l'éditeur et le plan. Toute modification de texte ou du mode de coupe invalide le plan ; la lecture exige un plan revalidé et le même profil exact. Réglages de graine/LSD/température figés à la préparation. Annulation ne marque pas le job courant terminé ; reprise volontaire depuis ce job, sans répéter les précédents (une portion du job incomplet peut avoir été entendue : avertissement explicite).

Chaque job ouvre un moteur, réinitialise les états comme l'original et attend son retour de fermeture avant le suivant. Aucun moteur persistant inter-tours ni deux moteurs lourds simultanés. Le worker et les admissions Studio existants sont réutilisés. En différé, un seul PCM de job ; en progressif, un PCM de job borné plus une file de 96000 samples, jamais le PCM de toute la narration. Les tampons expérimentaux sont effacés après usage. Pas de réécoute globale ni cache disque de narration livrés. Les intervalles entre jobs dépendent des rechargements/calculs et ne sont pas vendus comme pauses prosodiques naturelles.

## Preuves

- Fixtures de comptage 50/51/100/150/200 tokens au tokenizer officiel : perte nulle, ordre et déterminisme. Les répétitions « le » servent au comptage, **pas** à juger la qualité vocale. [Reçu](proofs/TOKENIZATION.json).
- Texte français naturel **178 tokens**, six jobs 25/28/29/33/32/31 : six générations natives FP32 LSD3/graine42, EOS atteint, PCM fini et <20 s pour chaque job, fermeture retournée à chaque fois. [Mesures Linux](proofs/PUBLIC-LONG.json). Pas d'AudioTrack ni écoute humaine dans ce test natif Linux.
- Les modes différé/progressif combinés sont couverts par producteurs synthétiques et annulations ; leur couverture matérielle exacte est indiquée dans RESULTATS.md. Aucun succès Xiaomi présumé.
