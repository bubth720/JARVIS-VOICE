# Mission 005J — preuves et état de livraison

Module VOICE, branche `feat/voice-studio-005j`, base 005I contrôlée `f917ef15ce74a045564ddabdc407ccde4b595ba6`. Aucune fusion.

## Implémentation par phases

- A `244ca8f` : coque Material3, navigation, thèmes et états privés. JVM 4/4, APK/UI-test build réussis.
- B `275e2a1` : assistant sept étapes, deux audios dans une même identité, coffre et sélection explicite, archives chiffrées, écrans simples/avancés. JVM 7/7, build/lint réussis (0 erreur, 17 avertissements).
- C : parité native complète, tests de parcours avec fixtures synthétiques, protections, coexistence, audit et livraison indépendante. Résultats locaux ci-dessous ; les tests Android CI et la livraison seront consignés après exécution.

## Vérification locale C

`TESTS-LOCAUX.json` contient les comptes JUnit : studioRuntime 8/8, expressionRuntime 12/12, identityRuntime 19/19 ; pocketRuntime 35 réussis, 11 sautés (46 déclarés), zéro échec/erreur. APK et APK de test compilent ; lint 0 erreur, 17 avertissements. Les avertissements restent consignés dans le rapport CI, sans abaissement des contrôles de sécurité.

`PARITE-PUBLIQUE.json` : pack FP32 privé existant, sans reconstruction, Alba publique CC BY 4.0 ; quatre générations 005I/J, même conditionnement, graine, LSD/température et texte. Sans pauses (LSD1 puis3, intention CONVERSATION) et avec pauses 250 ms/2 phrases et 500 ms/3 phrases : PCM bit-identique, erreur absolue maximale 0. Valeurs finies, aucun écrêtage sur ces cas. Les temps mesurés sont ceux du CPU Cloud, pas du Xiaomi. Ce résultat limité ne remplace pas la validation indépendante PyTorch stricte ni l’évaluation perceptive ; aucun seuil ancien n’est changé.

L’audit a identifié `DebugProbesKt.bin`, ressource de débogage coroutine transitive de Material/AndroidX : exclusion précise de cette ressource inutilisée dans le nouveau module, sans relâcher le refus global des `.bin`/modèles/enregistrements.

## Périmètre des tests Android

Les tests de parcours emploient des MP3/WAV de sinus et un `.jvi` chiffré synthétique générés en CI, jamais des voix privées. Le coffre, l’authentification, les contrôles locaux, le codec MP3, le lecteur AudioTrack et l’UI sont réels. Le backend d’encodage/génération et le transport des documents sont injectés **uniquement par l’APK de test** : ces tests ne valident donc ni l’encodeur ONNX réel sur Android ni les autorisations du sélecteur Xiaomi. Le pack réel est exercé séparément en JVM locale. Aucun faux backend ou audio fixture dans l’APK livrée.

La CI prévoit aussi coexistence I/J avec sentinelle privée synthétique, capture clair/sombre de bibliothèque vide, et non-régression des workflows F/G/H/I. L’échec historique 005D (4/5, underrun terminal) doit être rapporté séparément, sans modifier 005D.

## Confidentialité et limites

Les trois bibliothèques et applications historiques sont inchangées par rapport à la base. ZIP privé conservé : 234932686 octets, SHA256 `9436555c6965e3480c06f51fedced0f512a8316e482a7e54df0f4ee83d0a01cd`. Aucun modèle privé, archive personnelle, enregistrement, jeton ou clé de signature publié. L’application n’a aucune permission réseau/micro, reste expérimentale et n’améliore pas le son par son UI.

005J n’a pas encore été essayée sur un Xiaomi physique. Le propriétaire doit importer son ZIP existant puis son `.jvi` local depuis 005I et confirmer B lui-même ; voir TEST-XIAOMI.md. Le profil B n’est ni connu ni collecté. Garder 005I installée.
