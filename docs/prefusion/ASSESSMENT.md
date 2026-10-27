# VOICE-PF-01 — lot 1, rétablir la couverture de qualification

Date : 2026-10-27 UTC. Base mission `697110fa5a8dc87c4ed2a7e8df81a2e644a9559b`, parent `95f873226a6b80e2517266a943dfbead94432b86`, vérifiés par Git. Branche `codex/prefusion-20261026-lot1`, PR brouillon vers `mission/prefusion-gates-20261026`. Le HEAD publié est celui de la PR.

## Diagnostic et correction

CI exacte [40412774881](https://github.com/bubth720/JARVIS-VOICE/actions/runs/40412774881) : SUCCESS du seul workflow Android historique, qui compile/teste/lint `app`. Ce vert ne qualifie ni voiceFusion02, ni les autres runtimes, ni AudioTrack API26/35. Le workflow complet FUSION-02 ne ciblait pas la branche mission et son garde original y refuse le document de mission additionnel (reproduit).

Le workflow complet est désormais déclenché sur la branche de ce lot et les PR vers mission. Tous ses tests JVM/Robolectric, cas audio instrumentés API26/35 et build/lint/audit sont conservés ; les artefacts portent un nom PRE-FUSION et le résumé JVM est généré dans `build/prefusion`, sans réécrire les preuves historiques versionnées. Aucun modèle, poids, profil, échantillon personnel ou APK distribué par ce lot.

Le garde additionnel fige tous les octets du SHA mission hors transformation CI exacte et permet seulement lui-même, ses six tests et ce rapport. Le garde FUSION-02 original reste intact et est exécuté séparément dans un clone temporaire au parent 95f873… . Une réussite de ce contrôle historique n'est pas une nouvelle qualification du moteur.

## Validation réellement exécutée

- `python3 scripts/fusion-02/check_scope.py` au HEAD mission : FAIL attendu de périmètre sur le fichier mission, conservé.
- `PYTHONDONTWRITEBYTECODE=1 python3 tools/check-prefusion.py` : PASS gel des sources actuelles + garde historique au SHA parent exact.
- `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tests/prefusion -v` : 6/6 PASS, 0 échec/skip, Linux Python3.12.
- Gradle complet/JVM/Robolectric, builds et instrumentation : NOT_RUN local (SDK/cache absents, accès proxy shell indisponible). La CI doit être relue au nouveau HEAD. Les éventuels skips de packs privés doivent rester distincts de cas exécutés.
- Synthèse native, first PCM/AudioTrack, comparaison 1/2/3/5 étapes, écoute aveugle, POCO, batterie/thermique : NOT_RUN, aucun gain annoncé.

## Sources, compatibilité et gates

Relus : `Voice04OperationMetrics.kt`, flux du Studio, `voiceBridgeContract`, `expressionRuntime`, FUSION-02 CONTRACT/TESTS. Les métriques d'énergie non disponibles restent inconnues ; first PCM, write AudioTrack et audition ne sont pas synonymes. Le contrat FUSION-02 exige EOS/frames avant Finished et ne démontre pas unload natif. Le contrat expression historique n'applique pas d'émotion acoustique : NEUTRAL baseline, autres intentions unsupported ; ce lot ne modifie ni son format ni les paramètres du sampler. Les événements avatar restent estimés, sans phonèmes qualifiés.

MinSdk26, Gradle8.13, JDK17 et versions des modules gelés ; ABI/ONNX/16Kio à contrôler par le build et sur cible. CORE API24/25 doit traiter VOICE comme indisponible. Dépendances et licences restent celles du dépôt ; aucun changement de pack, licence ou redistribution approuvé. V1 Binder signé préservé ; pas de connexion CORE/UI active, pas d'accès coffre depuis LLM, pas de nouvelle capture.

**BLOCKED** : confiance CORE, ressource native partagée et émotions non neutres ; les mesures Linux historiques ne sont pas des mesures Android. Prochaine étape : vérifier la campagne complète au SHA de PR, puis mesures synthétiques sur plateforme autorisée. Les essais personnels et toute activation attendent accord. Rollback : revert du garde/tests/workflow/rapport uniquement, aucun profil ou rendu historique modifié. Mission non terminée.
