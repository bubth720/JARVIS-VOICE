# Contrat CORE → VOICE v1

Aucune modification de CORE. `CoreVoiceRequestV1` est un DTO testable ; `json()` et `parse()` constituent le contrat fermé. Maximum 8192 octets, 300 caractères de texte, champs et flags exacts, UUID canoniques, nombres finis et typés, version 1. Pas d'exécution de commande ni interprétation d'instructions dans le texte. Jetons spéciaux et contrôles refusés. Un intent inconnu syntaxiquement valide revient à NEUTRAL avec dégradation explicite ; champ/flag inconnu refusé. Les exceptions ne doivent pas être journalisées avec le texte reçu.

Exemple synthétique :
```json
{"version":1,"utteranceText":"Demain, je prendrai le train pour Paris.","identityId":"00000000-0000-0000-0000-000000000001","personaId":null,"expressionIntent":"CONVERSATION","intensity":null,"priority":0,"speedRequested":null,"pauseMs":null,"flags":{"allowSentencePauses":false}}
```

`identityId` ne suffit pas à autoriser une voix : le coffre local consentant doit résoudre le profil actif. Le plan lie explicitement UUID/hash/pack. `personaId` est facultatif et ne sélectionne jamais un timbre. Priorité 0–3 : métadonnée uniquement, aucune préemption ni contournement RAM/thermique. Intensité 0–1 et vitesse 0.5–2 : demandes non prises en charge, indiquées comme telles. Pause 0–1500 ms : validation distincte du plan inter-phrases. Aucun classifieur ou paraphrase automatique.
