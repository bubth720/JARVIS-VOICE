# JARVIS VOICE — MISSION 005H
## Identité vocale durable, bibliothèque de références MP3 et profils multiples (offline Android)
**Statut : mission à exécuter par Codex. Aucun résultat d'implémentation n'est présumé.**
**Branche de travail : feat/voice-identity-005h**
**Branche de référence : feat/android-pocket-cloning-005d (PR #5 en brouillon).**
**Portée : JARVIS-VOICE seulement ; ne modifier ni JARVIS CORE, ni JARVIS 005D, 005E, 005F ou 005G installés.**

### 0. Contexte constaté
Le propriétaire a testé physiquement JARVIS VOICE 005F et 005G sur son Xiaomi Android ARM64. L'encodage FP32 des profils, la synthèse hors ligne et la lecture audio fonctionnent. Le rendu 005G est jugé excellent, avec une amélioration marquée sur les phrases longues. La latence reste élevée mais n'est pas prioritaire.

Le système 005G utilise Pocket TTS français 6 couches, ONNX FP32, le sampler LSD officiel (1/2/3/5 étapes), un extrait vocal de 5 à 15 secondes, 18 états de cache FlowLM, un format JVFP1 lié strictement au packId, et des tests A/B avec lecture différée. Le dépôt contient PersonalVoiceProfile.kt, CandidateVoiceEncoder.kt, QualityEngine.kt, FrenchFrontend.kt, ReferenceSelection.kt et l'application voiceQuality. PersonalVoiceRepository n'autorise actuellement qu'un profil actif et efface ses prédécesseurs lors de save().

L'utilisateur possède plusieurs MP3 de la même voix (avec consentement requis), présentant éventuellement différentes intonations, émotions et rythmes. Il veut (1) une bibliothèque privée qui n'efface pas les anciennes références, (2) une identité vocale persistante, réutilisable par les futures versions de JARVIS, (3) des variantes de référence et une comparaison perceptive fiable, sans fusion naïve d'états, et (4) préparer un futur contrat séparant identité, personnalité et expression. Cette mission couvre d'abord la couche IDENTITÉ. Les couches de personnalité / expression sont des missions ultérieures (005I/005J), pas une promesse de contrôle émotionnel disponible dans Pocket TTS 6L.

### 1. Règles non négociables
1. Aucune donnée vocale personnelle n'est envoyée hors de l'appareil. Ne pas demander de MP3 privés, JVFP1 privés, fichiers PCM privés, ni de dumps de mémoire au Cloud, GitHub, aux logs publics, à des crash-reporters, ni aux connecteurs. Tests Cloud uniquement sur voix publiques licites ou signaux synthétiques.
2. Clonage seulement de la voix du propriétaire ou d'une personne donnant son consentement explicite et légal. Afficher et conserver la confirmation de consentement au niveau de chaque identité/référence. Pas d'usurpation ou diffusion trompeuse.
3. Fonctionnement intégralement hors ligne, sans permission INTERNET ni RECORD_AUDIO ni sauvegarde automatique. Imports par Storage Access Framework, accès limité aux documents choisis ; fichiers sensibles dans le stockage privé noBackup de l'application.
4. **Garder 005G intacte et fonctionnelle**, son excellent rendu et son réglage A de référence. Ne changer aucun poids, packId, hashes du modèle ou format JVFP1 en place. Ne pas toucher aux APK déjà installées. Isoler les nouvelles fonctions dans une application 005H distincte, par exemple com.jarvis.voice.voiceidentity005h, ou une architecture aussi sûre justifiée. Réutiliser les bibliothèques internes existantes en lecture, sans régression.
5. Sur Xiaomi, un seul gros modèle ONNX chargé à la fois. Conserver 1 thread CPU par défaut, garde RAM actuelle >= 2 Gio disponibles pour opérations lourdes, garde thermique, annulation, lecture STATIC différée et Arrêt. Ne pas confondre espace disque et PSS RAM. Aucune optimisation prétendument validée sans essais.
6. Ne pas fusionner dans main ni PR #5 ; produire une PR **brouillon distincte** depuis la branche 005H vers la branche de référence, documenter les tests et les limites. Préserver les licences et attributions officielles.
7. Aucune fusion, moyenne ou concaténation des caches 18 états ou de profils JVFP1 sans preuve de compatibilité scientifique et tests de parité/qualité. Un profil = un conditionnement officiellement encodé depuis un extrait contigu admissible. Les profilages de voix différentes ne doivent jamais être combinés.

### 2. Architecture demandée
Construire des abstractions testables, sans couplage avec les écrans Android :
- VoiceIdentity : identité logique stable, identifiant UUID aléatoire, nom local éditable, note de consentement, date, référence neutre préférée, profil actif par backend et version de schéma. L'identité n'est PAS le cache JVFP1.
- VoiceReference : un MP3/WAV volontairement importé ou une portion décodée, identifiant local, identityId, longueur, plage retenue [début/durée], analyse énergétique, étiquette facultative NEUTRE/CHALEUREUSE/SERIEUSE/EXPRESSIVE/INCONNUE et commentaires privés. La catégorie ne vaut pas classification automatique ni style vocal contrôlable.
- VoiceProfile : profil JVFP1 individuel relié à identityId, referenceId, engineId, packId/empreinte, sampler/protocole de création, SHA256, provenance, date, évaluation humaine explicite et statut valide/incompatible. Plusieurs profils peuvent coexister ; un seul profil actif pour une synthèse donnée.
- VoiceIdentityVault / Repository : opérations atomiques créer, ajouter référence, encoder, sélectionner, renommer, noter, désactiver, supprimer, restaurer depuis archive contrôlée ; index versionné, transactions, vérification SHA256, absence de corruption et isolation par identité. La suppression n'efface jamais une autre identité ; confirmation avant toute opération destructive.
- VoiceBackendIdentityBinding : interface stable pour obtenir, à partir d'une identité, un profil compatible avec le moteur et pack exacts. Si incompatibilité (6L vers 24L, packId différent, modèle nouveau), refuser clairement et proposer réencodage LOCAL depuis une référence conservée et autorisée, jamais transfert / interprétation des 18 caches comme format universel.

Conserver les primitives des modules Pocket existants et documenter les points précis de réutilisation. Ne jamais modifier silencieusement l'ancienne PersonalVoiceRepository ni son unique profil.

### 3. Import et bibliothèque locale de plusieurs enregistrements
Créer une expérience d'import simple sur téléphone Android pour 5 à 10 MP3/WAV d'une même identité, sans imposer un nombre minimal. Import fichier par fichier (éventuellement sélection multiple si SAF fiable), durée/poids limités, robustesse codec, annulation, validation des segments. Décodage privé en mono 24 kHz ; exploiter les corrections MP3 et analyses de silence, RMS, écrêtage déjà validées. Éviter les copies inutiles de fichiers bruts volumineux.

Pour chaque fichier, suggérer et écouter une portion contiguë de 5 à 15 secondes de parole claire : pas de collage de fragments ni ajustement automatique trompeur. Respecter le warning « beaucoup de silence ». Les catégories expressives sont des annotations de l'utilisateur, jamais des étiquettes d'IA prétendues certaines. Si l'app ne dispose pas de vérificateur de locuteur fiable, demander confirmation que les références proviennent bien de la même personne ; ne pas annoncer une identification automatique.

Politique de conservation explicite :
- par défaut conserver les métadonnées et les profils encodés en stockage privé ; rendre transparent si les WAV sélectionnés sont ou non conservés ;
- si l'utilisateur souhaite recréer ses profils sur un autre backend/appareil, prévoir un mode optionnel de conservation sécurisée de références décodées ou un réimport des MP3 sources ;
- aucune rétention illimitée cachée des originaux, aucun envoi cloud ;
- vue permettant d'inspecter et de supprimer les données sensibles liées à une identité.

### 4. Plusieurs profils, une identité cohérente
Permettre de créer plusieurs profils à partir d'extraits différents, en respectant exactement l'encodeur officiel FP32 de 005G et le format JVFP1. Ne pas entraîner de nouveaux poids et ne pas modifier les poids existants. Chaque profil garde le packId fixe, la provenance locale et l'empreinte d'intégrité.

Proposer une référence NEUTRE PRINCIPALE et des profils secondaires étiquetés. Par défaut, garder le profil neutre sélectionné pour toutes les synthèses. Les variantes expressives sont choisies manuellement durant 005H, pas automatiquement selon un « sentiment » : changer de profil peut changer le timbre, ce qui doit être mesuré avant activation automatique.

Offrir une comparaison A/B entre deux profils choisis avec exactement le même texte, graine 42, température, étapes LSD et sortie PCM. Option de réécoute sans recalcul et notes subjectives sur : naturel, ressemblance, constance de timbre, intelligibilité, artefacts métalliques. Ne pas confondre scores automatiques (RMS/ASR) avec naturalité humaine. Évaluer si l'ajout d'autres extraits améliore réellement les résultats ; pas de garantie ou score de fidélité inventé.

### 5. Portabilité et archivage privés
Définir un format versionné JARVIS VOICE IDENTITY, distinct du format binaire JVFP1, incluant manifeste, identifiant logique, références autorisées facultatives, profils multiples par moteur/pack, SHA256 et notices/licences. Il doit préserver l'identité logique lors des upgrades, sans promettre que les profils 6L pourront être chargés par des moteurs différents.

Prévoir un export manuel et un import privé explicites via SAF, avec chiffrement authentifié (AEAD), mot de passe fourni par l'utilisateur et dérivation de clé robuste (Argon2id si correctement disponible, sinon PBKDF2-HMAC-SHA256 avec sel aléatoire, paramétrage et limites de coût documentés, AES-256-GCM avec nonce unique). Pas de ZIP brut de données vocales sur stockage partagé, pas de mot de passe en logs ou base locale. Chiffrement/authentification avant toute sortie du stockage privé ; import avec contrôle de taille, unicité de noms, traversée ZIP, limites anti-zip-bomb, vérification AEAD+SHA, staging atomique et refus des doublons/incompatibilités.

Si une exportation chiffrée sûre ne peut être implémentée et testée dans cette mission, **livrer en priorité une bibliothèque locale fiable et garder l'export désactivé**, documenté comme blocage. Ne jamais substituer un export clair ou prétendre à un backup opérationnel. Le fichier source privé ne doit pas être transmis à Codex.

Définir politique de migration non destructive et suppression : confirmation, vérification de la transaction, essai de restauration, aucune perte si mauvais mot de passe ou corruption. Le chiffrement du transfert n'implique pas magiquement que les fichiers déjà dans la sandbox soient protégés de tous types d'accès système ; expliciter le modèle de menace.

### 6. Préparation des missions 005I / 005J, sans implémentation prématurée
Définir seulement les interfaces de données suivantes : un VoiceIdentityId logique indépendant du backend, un VoicePersonaId qui appartient au CORE et n'est pas lié à un timbre, et une VoiceExpressionRequest (intent de communication, intensité, vitesse, pause, priorité). Prévoir capabilities déclarées par moteur, pour que l'orchestrateur refuse ou simplifie explicitement les expressions non prises en charge. **Ne pas inventer de contrôles émotionnels dans Pocket TTS 6L.**

Le futur moteur 24L/Chatterbox doit posséder son pack, son encodeur, son profil compatibles, ses propres tests et ses propres budgets. Aucun routage automatique vers réseau ou changement d'identité. Ajouter uniquement documentation d'architecture et types stables, sans intégrer un moteur lourd dans 005H.

### 7. Application expérimentale 005H
Fournir une APK Android 005H distincte, hors ligne et signée avec une clé permettant de vraies mises à jour. Si une clé existante 005F/005G est réutilisée, vérifier le certificat et le stockage privé de la clé ; ne pas en régénérer une silencieusement. Ne jamais exposer la clé dans Git ou artefacts. Si l'ancienne clé n'est plus accessible, documenter ce fait et la conséquence avant livraison.

Écran simple : liste d'identités, création, import des références, analyse/extrait/écoute, profils par référence, créer/choisir un profil, comparer les profils A/B, bibliothèque locale, sauvegarde sécurisée si implémentée, suppression explicite. Éviter une interface complexe et la prolifération de menus inutiles. Une synthèse ne doit jamais changer d'identité à l'insu de l'utilisateur.

Conserver une expérience possible depuis le seul Xiaomi, sans PC, Termux, ADB ou débogage sans fil requis. Recharger le ZIP privé FP32 depuis Téléchargements si nécessaire ; ne pas forcer un nouvel export Cloud, ne publier aucun poids gated.

### 8. Tests indispensables
Tests JVM unitaires sur schéma versionné, duplication, sélection d'identité, provenance et packId, création de 2+ profils, restauration, erreurs fichier, interruptions et compatibilité JVFP1. Tests instrumentés Android : import MP3/WAV synthétiques, stockage sans réseau, restart/reload, isolations entre identités, refus de fichiers malicieux, suppression, écoute via lecteur stable, annulation pendant l'encodage, garde thermique/RAM. Utiliser références publiques autorisées si de la voix humaine est nécessaire ; aucun fichier personnel dans CI.

Tests A/B comparables (même texte/seed/params, deux profils) pour vérifier que le code compare réellement deux conditionnements distincts et n'utilise pas un profil mis en cache par erreur. Tests export chiffré, mot de passe incorrect, altération et import atomique si export livré.

Audit APK : pas de permission INTERNET/micro, identité package distincte, signature/versionCode, aucun profil/poids privés embarqués. Vérifier la non-régression de 005F et 005G. Garder les tests imparfaits visibles, distinguer échecs, sauts, limitations d'émulateur et non-validation Xiaomi.

### 9. Livrables et critères d'acceptation
Documentation sous docs/mission005h/ : ARCHITECTURE.md, IDENTITE-SCHEMA.md, CONFIDENTIALITE-MENACES.md, TEST-XIAOMI.md (pas de PC), RESULTATS.md, MIGRATION-EXPORT.md, CI-RESULTATS.json. Journaliser les décisions et limites réelles, pas de réussites inventées.

Livrer une APK 005H de test et la PR brouillon, ou une première étape indépendante fonctionnelle si la sécurité de la bibliothèque nécessite plusieurs commits. Priorité :
P0 : stockage robuste de plusieurs références et profils, sélection explicite, synthèse et A/B avec moteur actuel, sans régression.
P1 : export/import chiffré audité, si possible sans compromettre P0.
P2 : contrat de données pour 005I/005J, documentation sans tentative de styles émotionnels artificiels.

La recette sur Xiaomi devra pouvoir :
1. importer le ZIP FP32 existant ;
2. sélectionner deux MP3 consentis de la même voix, puis deux extraits valides ;
3. créer deux profils séparés sans écraser le précédent ;
4. activer un profil principal et générer une phrase avec 005G-equivalent ;
5. écouter A/B deux profils sur même texte et même graine ;
6. fermer/redémarrer l'app et retrouver les deux profils et l'identité active ;
7. supprimer un profil sans effacer l'autre ;
8. sauvegarder/restaurer les références et profils par archive chiffrée uniquement si ce sous-système a été effectivement validé.

**Commence par inspecter la base et rédiger un plan court, puis implémente réellement. Ne te limite pas à l'analyse. Aucune fusion dans main. Aucun son personnel ni secret dans Codex Cloud. Termine par un compte-rendu vérifiable et les liens PR/Actions/APK.**
