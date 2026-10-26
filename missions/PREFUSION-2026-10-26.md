# MISSION CODEX VOICE-PF-01 — Temps de parole, émotions et raccordement sécurisé CORE

Dépôt : bubth720/JARVIS-VOICE
Chemin : missions/PREFUSION-2026-10-26.md
Branche de distribution : mission/prefusion-gates-20261026
Base Git vérifiée lors de la préparation : 95f873226a6b80e2517266a943dfbead94432b86

## Règles obligatoires
Codex lit d'abord AGENTS.md si présent, la CI au SHA exact, les PR ouvertes et le code réel. Créer sa propre branche de développement à partir du HEAD de cette branche mission; préserver contrats, historique, données et tests. 100 % local hors ligne au runtime, sobriété Android, minSdk stable. Aucun merge main, changement architecture majeur, installation utilisateur, activation sensible, suppression de preuve ni prise de permission implicite. Chaque phase est un commit testable et réversible.

## Phases

### 1. État et séparation des preuves

Lire docs/fusion-02/CONTRACT.md, TESTS.md, voice04Studio/Voice04StudioController.kt, Voice04OperationMetrics.kt, voiceBridgeContract et expressionRuntime. Isoler workflows verts/rouges au SHA réel, mesures Linux/Android et tests privés/publics; aucun enregistrement vocal personnel dans Git.

Critère : code/diff ciblé, tests positifs et négatifs réellement exécutés, rapport SHA et rollback. Si besoin d'accord => livrer la proposition et s'arrêter.

### 2. Latence et débit

Instrumenter chargement, first PCM, génération par segment, débit PCM, AudioTrack, durée complète, annulation et timeout. Mesurer sur appareil autorisé et synthétiques; comparer 1/2/3/5 étapes, cache privé et mémoire/batterie/thermique. Aucun gain revendiqué sans mesure.

Critère : code/diff ciblé, tests positifs et négatifs réellement exécutés, rapport SHA et rollback. Si besoin d'accord => livrer la proposition et s'arrêter.

### 3. Émotions crédibles

Auditer capacités réelles Pocket TTS/ONNX: styles réellement conditionnés vs simple hauteur/vitesse. Définir émotion NEUTRAL/JOY/SURPRISE/SADNESS/CONCERN/CURIOSITY/IRONY en contrat strict refusant UNSUPPORTED; benchmark multi-extraits autorisés et évaluations écoute aveugle identités/prosodie, temps de génération. Aucun clonage/emotion déclaré validé par une simple liste d'étiquettes.

Critère : code/diff ciblé, tests positifs et négatifs réellement exécutés, rapport SHA et rollback. Si besoin d'accord => livrer la proposition et s'arrêter.

### 4. Connexion CORE/UI

Proposer indication expressive typée LLM→CORE→VOICE séparée du texte/document; CORE authentifie la session. Events vers avatar sans prétendre lip-sync phonémique; conserver V1 Binder signé et voix personnelles privées, pas de liaison production avant décision de confiance et de ressources.

Critère : code/diff ciblé, tests positifs et négatifs réellement exécutés, rapport SHA et rollback. Si besoin d'accord => livrer la proposition et s'arrêter.

### 5. Régressions

Exécuter JVM/Android, tests lecture/annulation/reprise/échec et workflows précédemment en échec. Documenter mesures réelles sur POCO uniquement après essai physique autorisé; préserver le bon rendu 005F/005G/005J et profil vocal.

Critère : code/diff ciblé, tests positifs et négatifs réellement exécutés, rapport SHA et rollback. Si besoin d'accord => livrer la proposition et s'arrêter.

## Livrables et blocages
PR brouillon sur base correspondante, changelog technique, fichiers concernés, matrice dépendances, commandes et résultats tests (succès/échecs/skips/non exécutés), artefacts et SHA du commit, risques, retour arrière, prochaine action. Une réussite JVM ne constitue pas une preuve physique ni un GO Android. Aucun merge dans main.
