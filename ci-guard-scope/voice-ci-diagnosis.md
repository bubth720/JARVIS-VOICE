# VOICE-00 — Diagnostic et correction de portée des gardes CI historiques

Branche : `claude/repos-folders-analysis-sr65sg`, créée depuis `feat/voice-expressive-v3-android` @ `92d07453260553466c92196454b56929b7494b2f` (PR #27). Aucune fusion dans `main`, aucune PR créée.

## Échecs avant correction (SHA `92d0745`)

| Workflow | Run | Étape en échec | Cause exacte |
|---|---|---|---|
| VOICE SDK and expression styles | [43009384631](https://github.com/bubth720/JARVIS-VOICE/actions/runs/43009384631) | `Preserve engines, packs, identities, historical app and bridge` | `scripts/voice_sdk/check_style_preservation.py` exige `git diff --exit-code 99d4073` sur 20 chemins. |
| Voice Studio 005J | [43009384629](https://github.com/bubth720/JARVIS-VOICE/actions/runs/43009384629) | `Verify preservation of functional applications and model bindings` (reproduit localement ; les logs CI tronqués ne montrent que le nettoyage) | `git diff --exit-code f917ef1` sur 13 chemins. |

Reproduction locale (lecture seule, worktrees détachés) :

| Commit | Garde SDK | Garde Studio 005J |
|---|---|---|
| `3ffb280` VOICE-02 (introduit la garde SDK) | PASS | FAIL |
| `cce9a45` 005J tip (introduit la garde Studio) | absente | PASS |
| `642d9c1` base de V3 (tip M50) | **FAIL** | **FAIL** |
| `fe57452` voice-final-01 | FAIL | FAIL |

Conclusion : les deux gardes échouaient **avant le premier commit V3**. Elles ne valident que leur propre mission et se déclenchent par filtre `paths` sur toute PR touchant `voiceStudio/**`. Ce n'est pas une régression de V3. Dans les 30 chemins protégés, V3 (`ad27149..92d0745`) ne modifie que 3 fichiers : `studioRuntime/.../QuickReactionTiming.kt` (ajout), `QuickReactionTimingTest.kt` (ajout) et `voiceStudio/build.gradle.kts` (variante `voiceExpressivePilot`).

## Correction (aucune garde supprimée ni affaiblie)

`scripts/ci_guard_scope/voice_v3_successor.py` :
1. Vérifie que `check_style_preservation.py` est identique octet pour octet à `3ffb280`, puis l'exécute **inchangé** dans un worktree détaché de `3ffb280` (PASS requis).
2. Lit la commande Studio **telle quelle** dans `voice-studio.yml` de `cce9a45`, puis l'exécute dans un worktree de `cce9a45` (PASS requis).
3. Sur HEAD, impose que les 30 chemins protégés (union des deux gardes) soient identiques à la base V3 `642d9c1`, fichiers non suivis compris. Seule exception : les 3 fichiers V3, épinglés par leur blob git exact.
4. Vérifie que `3ffb280`, `cce9a45` et `642d9c1` sont des ancêtres de HEAD.

`scripts/ci_guard_scope/test_voice_v3_successor.py` contient 6 cas, exécutés dans la CI SDK :
- HEAD propre → PASS ;
- 5 mutations → toutes rejetées : fichier `app/`, chemin propre à 005J, fichier V3 épinglé, fichier injecté non suivi, garde historique modifiée.

Changements de workflows :
- Les étapes de garde conservent leur nom. Seule leur commande appelle désormais le successeur ; la commande d'origine reste citée en commentaire.
- `scripts/ci_guard_scope/**` est ajouté aux filtres `paths`.
- Un déclencheur `push` est **ajouté** pour la branche `claude/repos-folders-analysis-sr65sg`, sur SDK, Studio et V3 lab. Raison : sans PR, `workflow_dispatch` est impossible car `main` ne contient aucun workflow.

Les tests V3 restent bloquants. Les étapes Gradle, lint, APK et émulateur sont inchangées.

## Résultats

Tests locaux exécutés au SHA de travail (Python 3.12, sans Android SDK) :
- `python3 scripts/ci_guard_scope/voice_v3_successor.py` → PASS ;
- `python3 scripts/ci_guard_scope/test_voice_v3_successor.py` → 6/6 PASS.

Gradle, lint et émulateur : NOT_RUN localement, délégués à la CI. Les runs après correction sont décrits dans `voice-ci-runs.json`.

## Limite

La PR #27 pointe sur `feat/voice-expressive-v3-android`, qui n'a pas reçu ce correctif. Son signal ne changera qu'après report (rebase ou cherry-pick explicite) de ces commits sur cette branche, avec accord humain.
