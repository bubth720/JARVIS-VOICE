# VOICE FINAL-01 local SDK v1

Module Kotlin/JVM sans Android, ONNX, profil vocal, texte privé, réseau ou Binder. Dans ce dépôt : `implementation(project(":voiceFinalSdk"))` ; produire le JAR avec `:voiceFinalSdk:jar` (Kotlin stdlib requise). Le JAR est aussi fourni avec l'APK de laboratoire.

`VoiceEventType` : START, FIRST_AUDIO, PROGRESS, PAUSE, COMPLETE, CANCELLED, ERROR, UNKNOWN. `VoiceEvent.metadata()` produit le schéma `voice.final01.events/1`, des compteurs et éventuellement un nombre d'échantillons observé. `completed` compte les segments présentés avec succès, `skipped` les omissions expressément choisies. COMPLETE signifie traitement terminé ; ne pas dire tout le texte prononcé si skipped>0.

FIRST_AUDIO est une présentation AudioTrack estimée par le lecteur ; aucune audibilité humaine ou unité phonétique n'est revendiquée. Dans cette version le compteur d'échantillons peut rester null. PROGRESS localise une fin de segment, pas une bouche ou un phonème. Aucun événement ne prouve la libération native. UNKNOWN est bloquant, sans reset au timeout.

`LocalVoiceControl` expose `pauseAfterSegment()` / `stop()` en processus local. `DocumentSession` implémente le déroulement dans voiceFinalRuntime ; FinalCoordinator est l'adaptateur Android et utilise le worker, le coffre, le moteur et le lecteur existants. Les callbacks doivent rester courts ; une exception d'observateur ne casse pas la propriété de la ressource. La reprise est explicite, avec décision pour un segment interrompu ambigu. Pas de restauration automatique du document après mort de processus.

Aucune liaison CORE/INTERFACE ou codec IPC livré/activé. Ne pas brancher ce SDK sur un service exporté sans mission de confiance et contrats distincte.
