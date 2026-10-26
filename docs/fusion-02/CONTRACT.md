# VOICE FUSION-02 — propositions et frontière de confiance

`voice.fusion02.audio/1-draft` est additif ; `voice.final01.events/1`, Binder V1
et les contrats UNIFIED restent byte-identiques. Ce module n'est pas une dépendance
runtime des applications. Aucune interface n'est exportée, aucun moteur chargé par
la CLI, aucun flux inter-APK activé.

## Chronologie locale

Clé canonique UUID session/request + génération positive <=2^53-1. Un seul job
admis, 128 tombstones par instance par défaut, aucun évincement permettant replay.
Générations croissantes par session ; requestId non réutilisable dans une session.
Ce registre RAM n'est pas un journal résistant à la mort de processus : ne jamais
l'utiliser comme autorité de bail CORE. Nouvelle instance != preuve de libération native.

`SpeechQueued -> Generating -> FirstPCM -> AudioTrackWrite -> PlayingEstimated ->
SegmentFinished`, puis prochain segment ou `Paused`, puis `Finished` uniquement
après tous les segments. Les compteurs produits/soumis/présentés doivent correspondre
avec EOS réel. `PlayingEstimated` signifie progression estimée AudioTrack, jamais
perception humaine. `RenderedOnly` Linux n'émet ni write ni Playing ni Finished.
`Cancelled` est terminal pour la parole ; peut être aggravé en `NativeUnknown`.
UNKNOWN bloque toute nouvelle admission de cette instance, sans bouton reset.
Stop ne libère pas l'admission avant le retour du worker ; il ne peut pas interrompre
magiquement un appel natif bloqué. Le SPI doit relier StopSignal à son lecteur/token
réel ; cet adaptateur Android de production n'est pas activé ici.

Horloge monotone `System.nanoTime`, relative au début de chaque opération, sans date
murale ; les tests injectés se déclarent explicitement. Aucune soustraction d'horloges
CORE/VOICE à travers processus. La mesure FirstPCM du benchmark comprend le chargement
mais exclut l'encodage/résolution public effectué avant répétitions. Pas d'audio humain
inféré d'un compteur. `nativeUnloadVerified` reste false, y compris après close.

Les événements sont retirés par snapshot immuable, 256 maximum, avec compteur dropped
et séquence ; aucun callback tiers exécuté sous verrou. Le consommateur refuse un trou
de séquence plutôt que feindre la continuité. Le propriétaire doit consommer au fil de
l'eau avant saturation ; le snapshot seul n'est pas un historique de 4000 segments.
Aucun texte, PCM, profil, chemin, URI ou exception privée dans les événements. UUID
éphémères d'opérations uniquement, pas identifiants d'identité vocale.

Les observations proviennent de producteurs locaux de confiance, pas de payload IPC.
L'origine JVM_SIMULATION/ANDROID_SYNTHETIC_AUDIO_TRACK/LINUX_NATIVE_RENDER_ONLY indique
la nature de la preuve, pas un certificat qu'un appelant pourrait s'auto-attribuer.

## CORE : proposition inerte

`voice.fusion02.speak/1-draft` : clé corrélée, énumération de deux phrases publiques,
budget 1..60000ms. Aucun champ pour texte libre, URI, fichier, profil ou cache privé.
`DisabledIntegration.speak` retourne toujours BLOCKED_NATIVE_AUTHORITY ; pas de transport.
Le test `Fusion02ProposalTests` adapte vers la vraie classe V1 PilotAuthorization,
mais n'enregistre aucune méthode V2 dans AIDL. Ce test est Robolectric, pas Binder kernel.

V1 conserve : UID kernel -> package unique autorisé -> certificat unique actuel +
installation/mise à jour -> activité visible/opt-in -> grant 60s -> requête unique,
budget restant, corrélation exacte. Ni lease_id/core_epoch d'un payload ni une signature
partagée ne prouvent l'admission globale native. Même correctement authentifié, V1
retourne NATIVE_AUTHORITY_UNAVAILABLE. Nouveau protocole à revoir avec CORE/UNIFIED
avant tout dispatch ; pas d'auto-composition avec VOICE-07 divergent.

## INTERFACE / STT / émotions

AvatarObserver propose IDLE/SPEAKING_ESTIMATED/PAUSED/STOPPED/UNKNOWN par opération,
refuse doublons, trous et résultats d'une autre génération. Fin de segment != phonème.
Pas de timestamp de bouche ou lip-sync qualifié. Pas de branchement INTERFACE réel.
STT reste UNSUPPORTED_CORE_WHISPER_NOT_CONNECTED ; aucun deuxième Whisper ni permission
micro. NEUTRAL = baseline du moteur existant ; toutes autres émotions UNSUPPORTED.
Aucune nouvelle qualification acoustique/humaine.
