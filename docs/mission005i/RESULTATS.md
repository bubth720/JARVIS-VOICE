# Mission 005I — résultats vérifiables

Modules livrés : `expressionRuntime` (contrat v1, politique et planification) et `voiceExpression` (Android indépendant). Branche `feat/voice-expression-005i`, PR brouillon #7 vers `feat/voice-identity-005h`, aucune fusion. Commits séparés A `e74096a`, B `859b8f5`, C `49b981a`, précision des capacités `532c658`.

## Comportement livré

Identité vocale, persona conversationnelle et intention d'expression restent distinctes. Pas de CORE modifié, de classifieur d'émotion, de paraphrase ou d'effet pitch/timestretch. Les intentions non NEUTRAL sont indiquées comme non prises en charge acoustiquement. Les capacités natives Pocket restent vides ; les pauses de l'orchestrateur ne sont annoncées qu'après validation effective.

Sans pauses explicites, le texte entier suit QualityEngine 005G inchangé, avec profil, graine, LSD et température choisis. Avec autorisation : 2–3 phrases complètes séparées manuellement par retours à la ligne ; ambiguïtés refusées, 50 jetons par phrase, 20 s PCM total pauses incluses, aucune lecture tronquée présentée comme succès. URGENT ne promet ni alarme ni latence.

L'app `com.jarvis.voice.voiceexpression005i`, version 0.5.0-mission005i-experimental / code 1, s'installe à côté de 005H. Import SAF local du ZIP FP32 existant et des archives JVIENC01 de 005H ; authentification avant restauration atomique, sans réencodage, remplacement d'UUID ou nouveau format JVFP1. Le choix actif de l'archive est conservé, mais une confirmation explicite du profil est exigée dans 005I avant génération. L'UUID/empreinte du vrai B restent connus du propriétaire seulement.

Les fonctions locales de bibliothèque, références MP3/WAV, profils, sauvegarde/restauration, lecteur STATIC, Arrêt et réécoute sont reprises dans la sandbox indépendante. A/B 005I impose le même profil exact (UUID/SHA fichier/SHA canonique/pack), texte, graine, LSD/température ; A NEUTRAL sans pauses, B intention/pauses choisies. Notes d'expressivité privées distinctes de la ressemblance 005H, dans les caches bornés. Mesures de génération avant lecture conservées pour chaque variante ; mesures totales affichées aussi après lecture. Aucun envoi de données personnelles.

## Tests

Code d'application testé : `532c6580835525b8879e012bde56fae8159fbe55`.
[CI 005I réussie](https://github.com/bubth720/JARVIS-VOICE/actions/runs/38759583443).

| Validation | Réussis | Échecs | Sautés |
|---|---:|---:|---:|
| JVM expressionRuntime local / CI | 12 / 11 | 0 | 0 / 1 |
| JVM identityRuntime local / CI | 19 / 18 | 0 | 0 / 1 |
| JVM pocketRuntime local / CI | 35 / 33 | 0 | 11 / 13 |
| Android 005I | 31 | 0 | 0 |
| Android 005H | 18 | 0 | 0 |
| Android 005G | 14 | 0 | 0 |
| Android 005F | 10 | 0 | 0 |
| Android 005D, défaut connu | 4 | 1 | 0 |

Build ARM64, AndroidTest, audits et lint réussissent ; lint 0 erreur / 24 avertissements. CI-RESULTATS.json conserve les runs/jobs, les suites XML téléchargées et les SHA256 des artefacts vérifiés ; LOCAL-RESULTATS.json conserve les contrôles locaux.

Les sauts correspondent aux packs/fixtures absents du CI public et ne sont pas des réussites. Aucun poids gated n'y est téléchargé. Les tests utilisant le vrai pack sont exécutés dans l'espace privé avec Alba publique CC BY 4.0 uniquement, jamais avec B ou un enregistrement personnel.

PUBLIC-NEUTRAL-PARITY.json : deux textes français, graines 7/42, LSD 1/3, même profil public encodé officiellement ; NEUTRAL et CONVERSATION bit-identiques au backend ONNX 005G, erreur max 0. Test réel de deux phrases avec 250 ms : PCM identique à la concaténation des synthèses entières + silence exact, 67440 échantillons / 2,81 s. Non-finis et écrêtage : 0. Cela prouve la préservation du backend, pas une amélioration perceptive ni une nouvelle parité des générations indépendantes PyTorch/ONNX. Le verrou conserve `strictIndependentParity=false` et tous les résultats antérieurs.

JVM : schéma fermé, types/limites, doublons JSON et différences de parseur Android, priorité sans préemption, style inconnu déterministe, non-confusion persona/voix, refus de réécriture, frontières ambiguës, PCM borné, EOS/annulation, cache même voix et dérive texte/graine/réglages, archive H→I sans WAV et préservation des labels/empreintes/profils, mauvais mot de passe/altération sans perte.

Android API 35 x86_64 : ces contrats avec le parseur système, fixtures synthétiques et crypto Android, conservation du profil actif, confirmation persistante explicite dans 005I, interopérabilité JVM→Android JVIENC01 (mot de passe ASCII et accents/emoji), imports MP3/WAV, refus SAF distant/consentement, bibliothèque/reprise/isolation, cache A/B, refus de capacité, mêmes réglages quand on change d'intention, gardes RAM/thermiques, annulation, lecteur STATIC PCM16/FLOAT32 et Arrêt. Aucun modèle lourd dans l'émulateur ; l'encodage des tests Android utilise un callback synthétique. Pas d'essai de coupure matérielle ou d'audit cryptographique indépendant.

## Préservation, confidentialité et APK

PRESERVATION.json : diff vide des anciens modules/livraisons contre 005H `1d0285e`, pack original 234932686 octets / SHA256 `9436555c6965e3480c06f51fedced0f512a8316e482a7e54df0f4ee83d0a01cd`, dix fichiers conformes au verrou inchangé. Aucun modèle reconstruit. Profil B personnel jamais accessible au Cloud, aucun ancien sandbox touché.

Garde lourde RAM 2 Gio, moniteur 250 ms, thermique et Arrêt conservés, un seul thread ONNX et un seul modèle par file d'opérations. Trois PCM maximum de 20 s par identité, dix identités maximum. Sauvegarde Android/transfert automatique désactivés, écran FLAG_SECURE, sauvegarde de texte de saisie et apprentissage IME demandés désactivés, mots de passe locaux temporaires effacés. Pas de réseau/micro ni télémétrie. Archives privées exportées manuellement chiffrées vers fournisseurs SAF locaux autorisés ; notes/PCM ne sont pas exportés.

[Télécharger le ZIP APK 005I](https://github.com/bubth720/JARVIS-VOICE/raw/feat/voice-expression-005i/deliveries/mission005i/005i-experimental-apk.zip), extraire puis installer `JARVIS-VOICE-005I-experimental.apk` depuis Android. ZIP public contenant **uniquement l'APK**, aucun pack/profil/clé/audio. Taille ZIP **11429850 octets**, SHA256 `a0fd0939ccecfaf05980bc04eb82bfee810722a149619b27d80fa893c6d6f9f8`.

APK **29323459 octets**, SHA256 `af79265c432285d45313be1d6220da2046f9691da9bc238fc88e3d3fcf300f15`. Certificat stable réutilisé `aba9ee43361fd342e119a33ae5a628e63199b3b6ef4f14d3a3619f6d5678d89e`, vérifié identique à 005F/G/H ; aucune régénération de clé. Clé privée conservée 0700/0600, ni Git ni CI. MinSdk26 / target35, ARM64, alignements ELF/ZIP 16 Ko, bibliothèques ONNX 1.27.0 vérifiées par hashes, verrou FP32 identique à la source, aucune permission réseau/micro ni poids/profil/média/clé embarqué. VersionCode1 ; futures mises à jour 005I avec même clé et code supérieur. Aucune désinstallation de 005H.

Le wrapper de livraison vérifie hashes/taille du ZIP extrait, certificat épinglé et ascendance du codeCommit `532c6580835525b8879e012bde56fae8159fbe55`. Le CI de livraison ajoute le contrôle de coinstallation émulateur avec sentinelle privée synthétique 005H ; sa preuve sera ajoutée après exécution. APK publique distincte des modèles et archives privés.

## Limites et prochaine action

L'échec 005D AudioTrack terminal reste visible : 4/5 tests, `count=1 frames=72000 submitted=72000`, attendu 0 obtenu 1 ; build/inférence passent. Aucun code 005D n'a été retouché pour masquer ce défaut préexistant.

L'installation, la migration SAF 005H→005I, le rendu, les mesures CPU/PSS/consommation/thermique et l'écoute perceptive doivent être vérifiés physiquement sur Xiaomi. Les nombres de temps natif dans le rapport public proviennent du Cloud ; les compteurs batterie sur téléphone peuvent être indisponibles ou grossiers. Aucun contrôle émotionnel ni gain de naturel ou d'identité n'est revendiqué.

Prochaine action : suivre TEST-XIAOMI.md depuis Android, garder 005H et sa sauvegarde B, restaurer localement dans 005I, confirmer le même profil et comparer A/B sans pauses d'abord. En cas d'échec de migration, arrêter et conserver 005H ; ne transmettre aucune archive ou voix personnelle au Cloud.
