# VOICE-01 — SDK vocal local

Le SDK orchestre le moteur VOICE existant. Il n'ajoute ni moteur, ni permission,
ni worker natif, ni transport inter-APK. L'hôte conserve son admission mémoire /
thermique et son worker pendant tout l'appel synchrone à `SpeechSession.speak`.
Dans Studio, activer **Options → SDK vocal expérimental**. Ce choix reste en RAM,
désactivé au prochain démarrage du processus ; le bouton Parler historique reste
le défaut. Le parcours SDK ne sauvegarde pas de nouvelle réécoute.

```kotlin
val repository = SpeechSessionRepository(VaultQualitySpeechEngine(vault, pack))
// Sur le worker déjà admis par l'hôte, avec un plan/profil choisi explicitement :
repository.open(output).use { session ->
    val outcome = session.speak(SpeechQueue(listOf(SpeechRequest(requestId, plan))), cancel)
}
```

`VoicePackResolver` utilise le coffre d'origine et vérifie la correspondance exacte
identité/profil/pack/conditionnement. `ExpressionRenderer` utilise un seul
`QualityEngine` pour ses segments, puis le ferme. Aucun profil n'est copié,
réencodé, activé ou remplacé par ce SDK. L'absence de pack/profil donne un refus
typé avant chargement. Les intentions non supportées gardent la politique
historique : aucune émotion acoustique inventée.

Une file contient 1–3 demandes explicites, sans découpage automatique. Chaque
demande garde les limites du plan existant : 1–3 segments, 1–50 tokens par segment,
silences explicitement autorisés jusqu'à 1500 ms. La file entière ne peut produire
plus de 480000 échantillons mono/24 kHz (20 s). Le PCM complet différé est borné,
copié dans une file privée et effacé après livraison ; pas de streaming natif
promis. Si une demande ultérieure dépasse le budget, les précédentes déjà lues
ne sont pas rejouées. Un nouveau lot demande un nouvel identifiant.

`OutputAudioPort` appartient à l'hôte et rapporte les échantillons présentés et
underruns observés. Dans Studio, il réutilise le `PcmPlayer` existant, son focus,
ses preuves de progression et son nettoyage. `AudioFocusController` observe ces
événements, sans deuxième propriétaire de focus. Un manque de preuve ou une
lecture partielle n'est pas un succès. La progression AudioTrack ne prouve pas
l'audibilité humaine ni la qualité de la voix.

`requestStop` invalide la demande et transmet l'annulation existante ; elle ne
libère pas immédiatement le dispatch. `close` pendant un appel laisse STOPPING
jusqu'à son retour effectif, ignore tout PCM tardif puis clôt la session.
RELEASED signifie **session SDK clôturée**, pas mémoire Android/JNI attestée.
Une exception moteur non classifiée ou un échec de fermeture de sortie bloque
le repository ; aucun reset automatique ni fallback moteur n'est disponible.
Studio consulte ce statut avant ses opérations lourdes et ses lectures, y compris
sur le chemin historique : désactiver le SDK ne contourne pas une quarantaine.
Les seuils natifs/mémoire/thermiques existants restent strictement inchangés.
Le repository sérialise uniquement ses appels SDK, il ne remplace pas l'admission
native de l'hôte. Réutiliser une seule instance dans la composition de l'hôte.

Le TTS Android n'est jamais choisi à la place d'une identité neuronale privée.
`AndroidTtsFallbackGate` refuse NON_VERIFIED sans choix explicite et métadonnées
locales observées (`networkRequired == false`). Aucun fallback Android n'est
activé dans Studio par cette livraison.

Vérification sans poids ni appareil :

```sh
./gradlew :voiceSdk:test :voiceSdk:demo
```

La démo distingue le vrai refus de l'adaptateur (pack absent) de la simulation
explicitement synthétique des ports PCM. Elle ne valide ni synthèse native,
clonage, écoute réelle, faible latence sur appareil, ni libération JNI.

## VOICE-02 — styles logiciels et chronologie

`ProsodyPlannerAdapter` et `ExpressionStyleProfiles` réutilisent le planificateur
existant : pause/segmentation explicites uniquement. Les labels publics CORE ID-01
`context/style/tutoiement` se mappent à `PublicPersonaStyleIntent` sans accès aux
profils CORE, mémoires ou pourcentages. Le tutoiement ne réécrit pas le texte parlé.
Intensité, rythme et émotion native exigée retournent `STYLE_UNSUPPORTED`.

`ExpressionSpeechAdapter` conserve le même worker/admission/moteur et produit une
`ExpressionTimeline` calculée sur les échantillons réellement rendus et leurs
métriques cohérentes. Les cues sont des offsets PCM relatifs, sans texte/PCM/profil,
sans phonèmes ni ancre de lecture. `AvatarTimingCuePort` est une interface locale,
aucune connexion à un avatar ou IPC n'est activée.

Studio → Réglages avancés → Styles logiciels offre A neutre / B Athéna pour deux
phrases **publiques fixes**, même profil/graine/sampler, avec réécoute manuelle.
Le cache PCM32 existant préserve les bits et les validations du vault ; aucun
nouveau format de profil/archive, aucune note de qualité automatique.

`./gradlew :voiceSdk:styleDemo` exerce les pauses via ports synthétiques clairement
annoncés. La modulation native et l'écoute sur appareil restent non qualifiées :
VOICE-02 est une livraison logicielle **PARTIAL**, pas une personnalité audible
physiquement validée.
