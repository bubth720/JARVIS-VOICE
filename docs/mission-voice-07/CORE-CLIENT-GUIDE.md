# Client CORE de référence — laboratoire public uniquement

Ce guide ne demande aucune installation sur un téléphone. Le CLI Java 17+ fonctionne sans Android et sans modèle. Il ne se connecte ni à CORE ni à Binder. Sur Android personnel, aucune action n’est nécessaire avant une mission de raccordement autorisée.

## Exécuter le binaire livré

Extraire voiceBridgeClient.zip, puis exécuter `bash voiceBridgeClient/bin/voiceBridgeClient` avec Java 17+ disponible. Sous Windows : script .bat. La sortie attendue contient SYNTHETIC_IN_MEMORY_TRANSPORT_ONLY, REFUSED, NATIVE_AUTHORITY_UNAVAILABLE, audio=false et coreConnected=false. elapsedMs mesure uniquement l’échange en mémoire.

La distribution locale a aussi été extraite et exécutée hors Gradle : sortie de refus attendue, 1,413959 ms sur le Cloud. Ce chiffre n’est pas une latence audio. Les octets/hash du ZIP construit localement ne sont pas présumés identiques à ceux de CI ; les deux environnements sont distingués dans les preuves.

## Point d’intégration, sans modification du dépôt CORE

La bibliothèque expose CoreSpeechClientPort(PublicSpeechTransport). Construire PublicSpeechJobRequestV2 avec deux UUID canoniques distincts, génération positive et PublicUtterance.HELLO ou TRAIN ; appeler submit. Le transport de démo retourne seulement une enveloppe corrélée refusée. Le port démarre TEXT_ONLY et ne peut recevoir que SPEECH_BLOCKED. disconnect invalide le résultat tardif sans libérer l’admission tant que le transport n’est pas revenu.

Ne pas écrire un transport Binder improvisé : aucun codec ni transaction V2 n’est autorisé ou livré. Ne pas envoyer un texte privé en remplaçant les constantes publiques. La permission signature et l’allowlist V1 restent la frontière de confiance ; ce port JVM ne les établit pas. Ne pas convertir logicalJobReleased en nativeReleaseVerified.

## Conditions de raccordement futur à faire examiner

1. Approuver un contrat V2 fermé et son diff Android, sans modifier la wire V1 figée.
2. Établir la confiance des APK réellement installées : signataires observés ou appairage formel revu ; aucune clé éphémère de fixture en production.
3. Prouver l’admission exclusive et la fin/décharge native à partir des composants responsables, pas d’un événement métier, booléen, timeout ou retour close.
4. Obtenir le consentement explicite au chemin audio inter-applications et au laboratoire concerné.
5. Seulement ensuite implémenter le transport et tester permission, UID/certificat, grant, révocation, mort Binder, rejeu, concurrence, audio public, fin et UNKNOWN avant toute installation personnelle.

Le gateway actuel appelle les contrôles V1 et refuse honnêtement. Ses tests JVM ne constituent pas des tests Binder V2. Les preuves kernel de cette livraison concernent exclusivement V1. Les états parlant/avatar restent absents puisqu’aucun PCM n’est produit.
