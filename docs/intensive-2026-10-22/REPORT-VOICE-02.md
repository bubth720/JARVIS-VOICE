# VOICE-02 — styles logiciels, pauses et chronologie PCM

**Statut : PARTIAL — logiciel développé/testé ; modulation native et écoute physique non qualifiées.**
Module VOICE ; dépôt `bubth720/JARVIS-VOICE` ; date 23 octobre 2026.
Branche `feat/voice-02-intensive`, fille de VOICE-01
`99d4073426896c992141bc010a69bb2690e03e71`, sans fusion.
Fiche intégralement lue à `31a0087381db14a9d2256e6e64373ef45b38b616`.

## Dépendances et responsabilité

CORE ID-01 revérifié sur GitHub : branche `feat/id-01-intensive`,
commit `9b04ecd8794da54e571d95f28a1de02512e86b5d`.
Sources lues : identity/profiles/{IdentityProfile,ProfileRepository,ProfilePreviewEngine}.kt
sous app/src/main/java/com/stark/jarvislocal/ au commit exact.
[API readonly PersonaExpressionPort](https://github.com/bubth720/JAVIS-Local-v2/blob/9b04ecd8794da54e571d95f28a1de02512e86b5d/app/src/main/java/com/stark/jarvislocal/identity/profiles/ProfilePreviewEngine.kt).

Le port amont fournit seulement contexte DAILY/WORK/URGENT, style ELEGANT/PLAIN et
booléen tutoiement. Notre PublicPersonaStyleIntent reproduit ces seuls labels locaux,
pas le stockage CORE ni une autorité de permission. Aucune connexion CORE réelle
activée. Les pourcentages de l'éditeur ne deviennent jamais du wire jarvis.identity/1 ;
CONTRACT_EXTENSION_REQUIRED reste intact. Aucun prompt SYSTEM/Athéna activé.

## Code et fonctionnement livré

- Nouveaux fichiers expressionRuntime : ExpressionStyleProfiles, ProsodyPlannerAdapter,
  ExpressionTimeline et AudibleComparisonStore ; anciens fichiers inchangés.
- Profils neutre/Athéna/personnalisé bornés : pauses logicielles uniquement. Athéna propose
  500 ms DAILY, 250 ms WORK, 0 ms URGENT, sans inférence d'émotion personnelle.
  Une proposition ne vaut pas consentement : allowSentencePauses reste exigé.
  Une pause explicitement demandée garde priorité ; texte, graine, sampler et binding inchangés.
- Intensité, rythme ou émotion native requise : STYLE_UNSUPPORTED, jamais annoncés appliqués.
  Les intentions contextuelles restent des métadonnées avec capacités réelles explicites.
- ExpressionSpeechAdapter réutilise le backend/ExpressionRenderer existants via Studio,
  même repository SDK, worker et admission. Pas de deuxième moteur, frontend ou système audio.
- Timeline : frontières SPEECH/PAUSE tirées des métriques cohérentes avec le PCM rendu,
  offsets 24 kHz exacts et bornés. Pas de phonèmes, d'alignement labial ni de wall-clock.
  AvatarTimingCuePort ne transmet ni texte, PCM, profil ou secret ; aucun avatar/IPC activé.
- Studio → Réglages avancés → Styles logiciels : A neutre et B Athéna sur deux phrases
  publiques fixes, même voix/texte/graine/sampler. Réécoute manuelle ; aucun score automatique.
  Le cache PCM32 existant conserve les bits ; activation/révocation du vault et suppression
  des réécoutes associées restent appliquées. Aucun profil copié ou réencodé, schéma inchangé.

## Vérifications réelles

```sh
./gradlew :expressionRuntime:test --tests '*ExpressionStyleTests*' :voiceSdk:test :voiceSdk:styleDemo :voiceStudio:assembleDebug :voiceStudio:lintDebug --no-daemon --max-workers=1
./gradlew :expressionRuntime:test :studioRuntime:test :identityRuntime:test :pocketRuntime:test :app:testDebugUnitTest --no-daemon --max-workers=1
python scripts/voice_sdk/check_style_preservation.py
```

En cloud : préfixe existant `bash /workspace/JAVIS-Local-v2/tools/identity-cloud-env.sh`,
aucun fichier de ce dépôt externe modifié. **Lot ciblé : 37 réussis, 0 échec/erreur/saut**
(8 styles + 29 SDK dont les 25 tests VOICE-01). Reçu VALIDATION-VOICE-02.json.

Résultats cumulés disponibles sur cette branche :

| Suite | Réussis | Échecs/erreurs | Sautés |
|---|---:|---:|---:|
| expressionRuntime | 19 | 0 | 1 |
| voiceSdk | 29 | 0 | 0 |
| studioRuntime | 9 | 0 | 1 |
| identityRuntime | 18 | 0 | 1 |
| pocketRuntime | 33 | 0 | 13 |
| app | 8 | 0 | 0 |

**132 recensés, 116 réussis, 0 échec/erreur, 16 sauts historiques** faute de
packs/profils locaux. Les suites immuables Studio/identity/pocket/app ont été réutilisées
UP-TO-DATE par Gradle après leur exécution VOICE-01 ; ne pas les compter comme de nouvelles
exécutions. La suite expression complète et le lot ciblé ont réellement tourné ici.
Les 16 sauts ne figurent pas dans les checks réussis du reçu.

Build et Lint Studio réussis : 17 avertissements, aucune erreur.
Audit APK réussi : API26/target35, ARM64, signature vérifiée, alignement16K, aucun poids
ni permission INTERNET. SHA-256 APK locale : `cd12d093eec48c74e8cff735541062f94234fffedc5adf95e8d84e9f37f46010`.
Aucune installation. Garde de préservation nouvelle réussie : tous anciens fichiers
expressionRuntime, moteurs/vault, admission SDK, AudioTrack, seuils, manifestes,
signatures/configuration et fixtures Binder préservés. La garde historique du pilote
reste inchangée et interdit l'édition Studio (échec de portée déjà documenté VOICE-01).

Démo : `SYNTHETIC_SOFTWARE_ONLY neutral-v1` → 1 segment, 0 pause, 240 échantillons ;
`athena-v1` → 2 segments, pause500 ms, 12480 échantillons, 3 cues. Cette démo utilise
ExpressionRenderer.renderWith avec un générateur PCM synthétique, aucune synthèse native.
Tests : répétabilité, consentement, champs impossibles, budgets, frontières ambiguës,
cohérence métriques/PCM, annulation avant/après pause, cache raw exact et révocation.

## Limites et suite

Aucune émotion acoustique, synthèse ONNX réelle, écoute humaine, Xiaomi, lip-sync ou
libération JNI matériellement attestée. PCM complet différé conservé, pas de promesse
streaming/latence. Le problème historique AudioTrack005D n'est pas annoncé corrigé.
Aucun changement natif, budget, permission, signature, contrat v1 ou données réelles.
Aucune fusion main. La qualification software_tested du reçu couvre les ports/pauses/
refus testés, pas une validation de personnalité audible.

VOICE-01 CI précédente confirmée SUCCESS : runs39952347409 (SDK) et39952347602 (Android).
La nouvelle CI inclut ce code ; ne pas présumer son résultat avant observation.
Prochaine étape : VOICE-03 uniquement après vérification CORE-02/UNI-01 et des ports
livrés ici, dans des adaptateurs/fixtures inactifs. Activation Binder/native, transport
sensible et essai personnel nécessitent une autorisation distincte.
