# VOICE-05 — narration opérationnelle, résultat PARTIEL sur la vitesse

Issue#14 intégralement lue, lancée explicitement par l'utilisateur. Base publiée/qualifiée VOICE04 `b1e9c9400072650443cc3c2ccd48bfa810d40a3e`. Branche `feat/voice-05-voice-delivery`. Aucun téléchargement de modèle, installation personnelle, modification JNI/permissions/signatures critiques ni fusion.

## Code et choix réels

- `SentencePreflight` réutilise LongFormPlanner/FrenchFrontend exact : <=1800UTF16,6jobs,50tokens/300unités/job,20s PCM/job ; aucun seuil augmenté ni réécriture.
- `NarrativeQueue` séquentielle : READY/RUNNING/PAUSED/INTERRUPTED/COMPLETED/UNKNOWN_NATIVE ; pas de lock tenu pendant ONNX/AudioTrack, pause après la phrase en cours, reprise explicite, aucun job terminé rejoué ; UNKNOWN reste bloquant, pas de reset au timeout.
- `FirstAudioScheduler` conserve seulement le frontend léger à SHA256 vérifié, invalidation forte et nouvelle observation de débit par session. Aucun modèle ni état de profil conservé entre jobs. Jalons existants FIRST_PCM/FIRST_WRITE/PRESENTATION_ESTIMATED réutilisés ; premier son humain=null.
- `PcmBufferPool` : deux slots exacts480000/96000Float32, maximum576000échantillons (2,304Mo décimaux) ; ownership exclusif, effacement à restitution, fermeture définitive si nettoyage inconnu. PooledPcmCapture présente uniquement le préfixe peuplé, sans copies de tous les blocs ni copie finale. Le chemin progressif peut aussi emprunter ces tableaux ; ses petits blocs consommateur restent alloués : pas de promesse zéro allocation.
- DEFERRED / AUTO_SAFE / EXPERIMENTAL_STREAM : tous maintiennent API26–30 différée. AUTO_SAFE renonce aux sondes suivantes après un producteur mesuré trop lent ; expérimental remesure sans forcer le STREAM. Les gardes0,65/prélecture/underrun/fin/annulation restent intactes.
- UI opt-in : prévisualisation, état par phrase, pause de frontière, reprise, comparaison A différée / B politique choisie à même plan/profil/graine/sampler. Texte modifié invalide le plan et la comparaison. Le mode par défaut reste différé.

Le module Android existant est réutilisé, sans copie divergente : `-PvoiceLab=05` produit **com.jarvis.voice.studio.voice05**, à côté de005J/VOICE04. Les labels changent, pas les permissions/composants exportés ni la configuration de signature. C'est une signature de laboratoire, pas une mise à jour d'une application installée. Aucune migration ou copie B n'a été exécutée.

## Exécutions et mesures

[JVM](proofs/JVM.json) : tests réels d'ordre/pause/reprise/réentrance, annulation, budget/ownership/pool, fermeture inconnue et intégrité ; ancienne suite VOICE04 conservée. CI Android API26/35 en cours à la publication initiale ; les assertions ne seront pas annoncées réussies avant observation.

[Démo native publique](proofs/PUBLIC-DEMO.json) : packFP32 existant, Alba publique CC BY4.0 SHA46264e83… vérifié, six phrases178tokens, graine42/LSD3/temp0,3, un thread ONNX et ouverture/fermeture par job. Deux chemins complets par phrase, ordreAB/BA alterné, comparaison au QualityEngine de référence via ExpressionRenderer. **Six PCM bit-identiques**, pause/reprise explicite après le deuxième job ; six WAV locaux sous work/voice05/public-demo, jamais B. Le pool crée1gros tableau pour6emprunts. Aucun modèle reconstruit.

| Job | Référence ms | Pool ms | PCM identique |
|---|---:|---:|---|
|1|7440,0|7098,3|oui|
|2|6795,2|7244,1|oui|
|3|7227,0|7852,4|oui|
|4|7384,8|8101,3|oui|
|5|6181,6|6583,0|oui|
|6|7199,0|7092,2|oui|

**Pas de gain temporel constant constaté.** Ces mesures exploratoires Linux, n=1parchemin/phrase, sans vidage du cacheOS, incluent des différences d'instrumentation/GC/JIT. La baisse d'allocations ne prouve ni accélération ni baisse de PSS natif. Ne pas extrapoler au Xiaomi. CPU/mémoire/thermique/énergie Android sont instrumentés dans l'app mais non mesurés avec B ici. La production ONNX plus lente que l'audio entraîne le différé explicite ; les pauses de rechargement/calcul entre phrases demeurent possibles. Narration longue complète ne signifie pas temps réel continu.

## Reproduction

```sh
# Aucun poids téléchargé : utiliser seulement le pack déjà validé et Alba publique épinglée.
VOICE05_PACK=/private/existing-pack VOICE05_PUBLIC_WAV=/public/pinned-alba.wav VOICE05_DEMO_OUT=work/public-demo ./gradlew :voice04Runtime:test --tests '*NarrativePublicDemoTests' --no-daemon --max-workers=1
./gradlew -PvoiceLab=05 :voice04Runtime:test :voice04Studio:assembleDebug :voice04Studio:assembleDebugAndroidTest :voice04Studio:lintDebug --no-daemon --max-workers=2
python scripts/voice05/check_scope.py
# Un émulateur explicitement sélectionné seulement ; jamais le Xiaomi.
ANDROID_SERIAL=emulator-5554 bash scripts/voice05/android_audio.sh
```

La démo utilise une identité temporaire Alba et efface ses fichiers de profil après le test ; aucune archive/personne privée utilisée. Les WAV sont des sorties publiques de démonstration, pas une preuve d'écoute humaine. Leurs SHA256 sont fournis. Licence de la source publiqueCCBY4.0, créditKyutai/Alba ; poids exclus.

## Préservation et suite

Gardes historiques + garde dédiée réussies localement : tous anciens moteurs/vault/SDK/005J/archives/livraisons/Binder inchangés ; JNI et lockFP32 inchangés dans l'audit APK. Seuls les modules expérimentaux VOICE04 sont étendus sur la branche05. L'échec005D historique et les anciennes gardes de portée restent documentés dans VOICE04 ; rien n'est masqué.

Statut PARTIEL : logiciel exécutable et démo native acquis, accélération Xiaomi/non-coupure perceptive non qualifiées. Prochaine action : qualification14tests AudioTrack/API puis publication du commit testé, avant VOICE06. Essai personnel ultérieur volontaire seulement, profilB/005J conservés ; ne pas désinstaller ni changer leurs clés.
