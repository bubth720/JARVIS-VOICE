# JARVIS VOICE — MISSION 005J : VOICE STUDIO, UI SIMPLE ET CRÉATION GUIDÉE

**Statut initial : spécification prête à exécuter, pas de code 005J livré.**

- Dépôt : `bubth720/JARVIS-VOICE`
- Branche de travail : `feat/voice-studio-005j`
- Base immuable contrôlée : branche `feat/voice-expression-005i`, commit `f917ef15ce74a045564ddabdc407ccde4b595ba6`
- PR cible : `feat/voice-expression-005i`, **brouillon**, jamais `main` sans autorisation utilisateur
- Applications de référence : `005F` (lecture stable), `005G` (qualité), `005H` (voix/bibliothèque), `005I` (expression/pauses)
- Livrable : **application Android distincte 005J**, belle et facile à comprendre, tests, documentation, APK signée stable si réalisable.

## Retour utilisateur réel — ne pas inventer les mesures

Sur Xiaomi, l'utilisateur a restauré sa sauvegarde chiffrée `.jvi` de 005H dans 005I, a activé son profil vocal personnel choisi après comparaison dans 005H (dit « B »), a généré et écouté une synthèse NEUTRAL sans problème, puis a testé plusieurs combinaisons et apprécié les **pauses entre phrases complètes, particulièrement pour des phrases longues**. Ce sont ses essais perceptifs : ils ne démontrent ni commande d'émotion native, ni supériorité des styles, ni chiffres de consommation/température. Il demande explicitement une interface « plus commerciale », plus élégante, plus simple, **moins de boutons**, et un parcours guidé lors de la création d'une nouvelle voix.

**Le profil B personnel reste local ; ne pas prétendre connaître son UUID/SHA, ne jamais le collecter.**

## Règles non négociables

1. **Ne pas modifier ni désinstaller les APK 005D/F/G/H/I**, leurs données, profils, signature ni code fonctionnel stable. Si refactorisation partagée s'avère indispensable, isoler, tester et demander validation avant intervention. Préférer un module `voiceStudio` autonome avec `applicationId=com.jarvis.voice.voicestudio005j`, sa propre sandbox. Reprendre les moteurs et librairies versionnées `pocketRuntime`, `identityRuntime`, `expressionRuntime` inchangés lorsque possible, sans copier des erreurs ou altérer `QualityEngine`.
2. **100 % hors ligne** sur le téléphone. Aucune permission réseau/micro, télémétrie, publicité, bibliothèque de suivi ni compte. Aucun MP3/WAV, `.jvi`, profil JVFP1, PCM, mot de passe ou modèle privé sur GitHub/Codex/CI. Le transfert depuis 005I se fait exclusivement via le fichier `.jvi` chiffré dans le sélecteur de fichiers local, sans accès direct à la sandbox 005I.
3. Conserver le même conditionnement/profil personnel (hash/UUID/pack) après restauration, sans réencodage ni auto-basculement d'identité. Ne pas toucher aux formats JVIENC01, JVFP1 ni à l'ordre des 18 états FP32. Garder 005I installée comme référence de repli.
4. Préserver les modes et contraintes techniques : moteur ONNX unique, threads/queue, admission RAM ~2 Gio libres, protection thermique, batterie, arrêt/annulation, lecture STATIC, PCM borné, limites de 50 tokens / 300 caractères / 20 s, accès SAF uniquement local, cryptographie AES-256-GCM, consentement préalable, limites d'archives et rollback. Aucun « pleine puissance » sur Xiaomi.
5. Les intentions non NEUTRAL de 005I **ne contrôlent pas l'acoustique émotionnelle**. L'UI peut offrir un sélecteur de contexte/intentions avec mention honnête « intention enregistrée, effet émotionnel indisponible », mais ne doit pas vendre de fausses voix heureuses, tristes, etc. Préserver l'invariance de génération à entrée égale sans pauses.
6. Les **pauses** actuellement vérifiées exigent deux ou trois phrases complètes, séparées manuellement par retour à la ligne, pause 0/250/500/1000/1500 ms, sans prétendre une segmentation auto universelle. En mode simple : choix « Pauses entre phrases » clair, réglage 250 ms par exemple comme préférence **explicite** et non activé silencieusement si le texte n'est pas admissible ; si inadmissible afficher une aide pour insérer des retours à la ligne ou désactiver. Respecter le refus des segments ambigus et ne jamais couper/tronquer en secret.
7. Aucune fusion dans main et aucune livraison « testée sur Xiaomi » sans retour physique de l'utilisateur. Tous tests et métriques avec références exactes. Signatures stables et versionCode évolutif si clé disponible ; sinon documenter le blocage.

## Étape A — UX, maquettes structurelles et code de navigation

Inspecter la structure réelle de `voiceExpression/src/main/java/com/jarvis/voice/voiceexpression/MainActivity.kt`, `voiceExpression/build.gradle.kts`, `identityRuntime`, `expressionRuntime` et les documents `docs/mission005i/`. Conserver les comportements, éviter le long `ScrollView` unique de la version technique.

Concevoir une interface française responsive, moderne et tactile, compatible **Android API min 26, target 35**, téléphone Xiaomi, tailles d'écrans et mode clair/sombre ; thème bleu nuit futuriste JARVIS, accents bleu/vert doux, contraste élevé, textes lisibles, composants homogènes, boutons >=48 dp, accessibilité TalkBack/labels, sans animations lourdes.

Choisir de manière motivée Android Views XML + Material ou Jetpack Compose Material 3 selon support Gradle disponible, coût binaire et maintenance. Ne pas introduire un gros framework si risque inutile. UI native uniquement, aucun WebView/dépendance cloud. Séparer navigation, écrans et logique en composants testables ; pas de copie du gigantesque Activity monolithique si évitable.

Écrans de base :
- **Accueil / Parler** : identité/profil actif clairement visibles (« Ma voix JARVIS » sans renommer le profil en dur), texte, bouton principal `Parler`, `Arrêter` uniquement pendant travail, `Réécouter` si cache valide, état de modèle et progression, pauses facultatives explicites ; panneau déroulant « Options » limité (intention indiquée honnêtement, délai/pause).
- **Mes voix** : identités et profils, activer explicitement, écouter/distinguer le bon profil, sauvegarder/restaurer `.jvi`, créer une voix, gestion de suppression toujours séparée et confirmée.
- **Créer une voix** : assistant séquentiel avec retour/continuer, état de complétude, reprise après interruption sans réinitialiser données privées.
- **Réglages avancés** : graine, LSD, température, comparaisons A/B, diagnostics CPU/PSS/thermique, stop, licences et informations techniques ; modes avancés non imposés à l'utilisateur courant.

Ne pas cacher les états d'erreur dans un bas de page invisible. Messages à l'écran précis et actionnables : modèle absent, pas de voix active, WAV absent, profil incompatible, archive mauvaise, pause refusée, durée d'extrait invalide, capacité émotionnelle absente.

**Critères A** : build UI et tests de navigation/états vides ; génération jamais lancée sans profil confirmé ; pas d'actions dangereuses par tap unique ; pas d'éléments décoratifs faussement cliquables ; performance raisonnable sur Android.

## Étape B — Assistant de création de voix pas à pas

Créer un flux clair :
1. **Choisir** : « Importer une voix existante » depuis `.jvi` ou « Créer une nouvelle voix ». ZIP FP32 déjà disponible ou aide d'import local.
2. **Identité et consentement** : nom local, note optionnelle, consentement explicite et droit de cloner, précautions de confidentialité. Créer une identité logique seulement à cette étape, ne pas créer une nouvelle identité pour chaque MP3.
3. **Ajouter des audios** : permettre un ou plusieurs MP3/WAV locaux via SAF ; si sélection multiple difficile, prise en charge incrémentale avec file d'ajouts claire, traitement un par un. Chaque fichier contigu doit durer au moins 5 s et la portion sélectionnée 5–15 s ; afficher la durée réelle avant validation. Un fichier 3–4 s = alerte précoce, sans erreur obscure. Ne jamais fusionner silencieusement plusieurs fichiers / mélanger plusieurs locuteurs. Si assistance d'assemblage proposée, option manuelle séparée et expliquer que les collages peuvent détériorer le naturel : hors périmètre prioritaire. Les MP3 originaux demeurent intacts.
4. **Vérifier chaque extrait** : écouter, contrôle niveau/silence/écrêtage existant, labels manuels NEUTRE/CHALEUREUSE/SERIEUSE/etc sans revendiquer de commande émotionnelle, consentement du locuteur à chaque fichier ; conserver WAV explicitement pour réencodage/backup.
5. **Créer le ou les profils** : encoder via `IdentityEncoding.official` inchangé, un profil individuel par référence, progressions/hors-ligne/annulation/température, pas de fusion automatique des 18 états ; ne pas forcer l'encodage de 10 fichiers à la suite lorsque téléphone chauffe.
6. **Essayer et choisir** : phrases de test, écoute/réécoute, sélectionner explicitement profil principal, comparaison A/B si plusieurs profils, aucune victoire automatique ni notation inventée.
7. **Sauvegarder** : proposer sans forcer l'archive chiffrée `.jvi`, expliquer WAV facultatifs, stockage SAF local et mot de passe personnel non stocké. Guide de restauration optionnel.

Assistant sans imposer un nombre minimal de références supérieur à 1 ; sortie avec une seule référence valide possible. Si l'utilisateur a déjà une identité 005I chiffrée, import sans réencodage, contrôle UUID/hash/pack avec conservation de la bonne sélection seulement après confirmation locale. « Créer une nouvelle voix » ne doit jamais remplacer un ancien profil/identité.

**Critères B** : tests d'une seule identité avec deux fichiers ajoutés un par un ; fichiers 3s/4s refusés clairement, >=5s admis si valides ; import audio/consentement sans perte ; plusieurs profils distincts ; restauration `.jvi` sans altération ; retours arrière dans assistant sûrs ; échec d'import/conversion laisse ancienne voix sélectionnée inchangée.

## Étape C — Parité, tests, distribution et documentation

Conserver le rendu neutre 005I / `QualityEngine` bit-identique à mêmes texte, profil public, graine, LSD et température ; test avec/sans pauses admissibles 2–3 phrases ; erreurs honnêtes si ponctuation ambiguë. Les intentions d'émotion ne doivent pas changer le sampler caché. Revoir cache PCM, annulation, signaux d'état, métriques et garde d'admission ; vérifier coexistence 005I/005J sur émulateur et isolation des fichiers privés, sans réellement disposer du B de l'utilisateur.

Automatiser :
- JVM du wizard, sélection de profil, validation de durée, capacités, restauration/erreur, navigation/reprise, pure render policy ;
- tests Android instrumentés API35 de parcours UI : première utilisation sans voix, importer ZIP factice de fixture publique autorisée, `.jvi` chiffré synthétique, importer et créer profil de fixture, parler/réécouter/arrêter, basculer simple-avancé, protections, état après rotation, thème clair/sombre et accessibilité élémentaire ;
- non-régression JVM/Android 005F 10/10, 005G 14/14, 005H 18/18, 005I 31/31 si déclenchables ; documenter séparément l'échec historique 005D 4/5 connu ; tous les nouveaux échecs doivent être remontés et corrigés avant livraison ;
- revue ressources/permissions, APK ARM64, signature, absence d'audio/modèle/clefs/archives inclus.

**Livrables obligatoires** :
- `docs/mission005j/ARCHITECTURE-UI.md` : états, navigation, choix de framework et dépendances
- `docs/mission005j/PARCOURS-VOIX.md` : tutoriel utilisateurs en français
- `docs/mission005j/COMPATIBILITE.md` : 005I→005J via ZIP FP32 + .jvi, profil et sauvegarde intacts
- `docs/mission005j/TEST-XIAOMI.md` : chemin depuis Android, sans PC ni ADB
- `docs/mission005j/RESULTATS.md` : commits A/B/C, tests comptés et source des preuves, limitations
- APK **005J indépendante** signée, et URL de livraison vérifiée si réellement produite (pas inventée)
- PR brouillon `feat/voice-studio-005j` → `feat/voice-expression-005i`, sans fusion.

**Interdits** : prétendre que l'interface améliore le son, ajouter de véritables émotions inexistantes, charger plusieurs modèles lourds simultanés, récupérer le profil B depuis Cloud, envoyer les archives privées, supprimer des anciennes apps, fusionner dans main, introduire une connexion CORE sans décision de transport/authentification.

## Exécution Codex

Commencer par vérifier la branche et le commit réellement présents, rapporter si divergent. Puis implémenter successivement **A → B → C**, en commits séparés avec tests après chaque étape, préserver l'état fonctionnel à tout moment. En cas de blocage, documenter et arrêter la partie concernée sans masquer les défauts.

**Compte rendu final obligatoire** : module VOICE, mission 005J, branche, dernier commit, PR brouillon, fonctionnalités effectivement réalisées, tests réussis/échoués/sautés, blocages, liens de livrables, prochaine action utilisateur sur Xiaomi. Aucun merge, aucun déploiement sur l'appareil sans l'accord du propriétaire.
