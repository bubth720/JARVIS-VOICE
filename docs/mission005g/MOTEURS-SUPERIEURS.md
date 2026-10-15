# Moteurs supérieurs — étude documentaire, pas une validation Android

| Moteur | Français / clonage | Licence vérifiée | Empreinte observée | Décision actuelle |
|---|---|---|---|---|
| Pocket TTS French 6L existant | Oui, profil FP32 déjà testé par le propriétaire sur Xiaomi | Code MIT ; poids CC BY 4.0 + conditions de consentement | Pack actuel conservé ; PSS 005F rapporté par le propriétaire ~1304 Mio | Seul moteur actif ; réglages 005G expérimentaux |
| Pocket TTS French 24L | Configuration officielle française, BOS et clonage ; variante preview | Même dépôt poids Kyutai CC BY 4.0 et conditions gated | Fichier checkpoint 672178676 octets ; RAM/PSS et CPU Android non mesurés | Non intégré, budgets non démontrés |
| Chatterbox Multilingual V3 | Français parmi 23 langues ; prompt vocal officiel | Dépôt ResembleAI/chatterbox : MIT, gated=false au moment du relevé | T3 V3 2143989928 + S3Gen V3 1056381804 + VE 5695784 = 3206067516 octets hors runtime | Non intégré, budgets Android non démontrés et conversion/parité à établir |

Les tailles sont celles des fichiers officiels, **pas des PSS mesurés**. On ne
cumule pas les variantes .pt et .safetensors pour Chatterbox. Le générateur et
le transformeur linguistique sont deux composants distincts. Une inférence CPU
Python supportée ne prouve pas une inférence Android rapide ou thermiquement
acceptable. Aucun nouveau poids alternatif n'a été téléchargé ni publié.

Pocket French 24L a 24 couches contre six : sa banque KV comporte 72 états,
contre 18 pour le pack actuel. Son modèle, ses poids et ses profils ne sont pas
interchangeables avec le profil 6L. L'amont le présente comme plus gros et plus
lent, encore preview/non distillé ; cela n'est pas une mesure perceptive faite ici.
Il faudrait un export indépendant, son BOS, tokenizer, Mimi et validation de
parité, puis un véritable benchmark PSS/CPU/thermique avant toute activation.

Chatterbox V3 est confirmé dans le README officiel et le chargeur
`t3_model="v3"` ; les conversions MLX/ONNX communautaires trouvées ne sont pas
considérées comme une équivalence validée de l'officiel. Le modèle officiel
annonce un watermark Perth : une future intégration devra conserver ce chemin
et ses notices. Son profil de conditionnement n'est pas un JVFP1 Pocket TTS.
L'empreinte disque de plus de 3 Go rend une intégration sans preuve de budget
particulièrement peu adaptée au prototype actuel. Pas d'abaissement de la garde
RAM, pas de quantification hétérogène ni d'activation « Pleine puissance » sur
la base d'une estimation.

Pour un matériel dédié futur : backend indépendant, poids/profil liés à une
identité exacte, mesure PSS et température sur la cible, limites d'annulation,
renderer PCM commun et consentement explicite pour toute éventuelle liaison.
La présente APK ne contient aucun client réseau. Le modèle 6L reste sélectionné
explicitement ; aucun basculement automatique.

Sources officielles consultées :

- [Kyutai Pocket TTS — modèle et licence](https://huggingface.co/kyutai/pocket-tts)
- [Configuration française 24L, version 3.3.0](https://github.com/kyutai-labs/pocket-tts/blob/3dbee45d343d7dddd0d105468d17f8dcba14db3e/pocket_tts/config/french_24l.yaml)
- [Métadonnées du checkpoint français 24L à révision fixe](https://huggingface.co/api/models/kyutai/pocket-tts/tree/d0ebc5deb50bc6b225f686782f124dd37e2dca0a/languages/french_24l)
- [Chatterbox officiel, V3 et langues](https://github.com/resemble-ai/chatterbox)
- [Poids officiels et licence MIT](https://huggingface.co/ResembleAI/chatterbox), révision relevée `5bb1f6ee58e50c3b8d408bc82a6d3740c2db6e18`
