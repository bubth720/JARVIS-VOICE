# Capacités effectives 005I

| Demande | Traitement réel |
|---|---|
| NEUTRAL | QualityEngine 005G sans modification, même texte/graine/profil/sampler/température |
| INFORMATIVE, CONVERSATION, SERIOUS, URGENT, CURIOUS, SURPRISED, HUMOROUS | Intention conservée ; aucune commande acoustique validée, signalée non prise en charge |
| Formulation/personnalité | Texte fourni manuellement ou par futur CORE ; aucune paraphrase |
| Intensité, vitesse | Non prises en charge et non appliquées |
| Pauses | Option explicite, 0–1500 ms, entre 2–3 phrases complètes séparées manuellement par retour à la ligne ; phrases ambiguës refusées |
| Ponctuation | Aucune transformation d'expression ; normalisation FrenchFrontend 005G existante seulement |
| URGENT/priorité | Métadonnée ; aucune alarme temps réel, aucune préemption, aucune latence bornée |

Les frontières avec citations, parenthèses, ponctuation terminale interne (abréviations, décimales, phrases multiples), mot unique ou absence de majuscule sont refusées pour les pauses. Sans pauses, aucune fragmentation nouvelle. Chaque segment est limité à 50 jetons par le vrai tokenizer ; total PCM <=20 s pauses incluses. Dépassement/EOS absent : échec, aucune lecture tronquée présentée comme complète. La graine choisie est réinitialisée identiquement par phrase entière, sans bruit ou réglage caché.

Le frontend historique supprime notamment guillemets/parenthèses et normalise ponctuation/casse : 005I ne prétend pas préserver ces signes dans la tokenisation. Le texte utilisateur reste exact dans le plan et le cache. Aucune réécriture supplémentaire n'est permise, même si proposée comme équivalente. La comparaison neutre doit rester bit-identique au moteur 005G avec les mêmes entrées.

Pas de signal d'urgence ajouté : le signal de test du lecteur est explicitement synthétique et volontaire. Ne pas utiliser cette application comme alarme.

Les capacités natives Pocket restent exactement celles de 005H (ensemble vide). `LocalProsodyPlan.capabilities` déclare séparément le champ `pause` uniquement lorsqu’un silence inter-phrases est effectivement appliqué ; `unsupportedEffects` expose les demandes ignorées/dégradées après validation. Il ne s’agit jamais d’une capacité émotionnelle native.
