# VOICE-06 — effets PCM exécutables, émotions NON VALIDÉES

Issue#15 entièrement lue et lancée explicitement. Branche `feat/voice-06-voice-delivery`, base publiée/qualifiéeVOICE05 `1a9f713dff28f6e4fb145728815e9ed118f5a929` (codeb90cea0,14/14 parAPI26/35). Aucun nouveau modèle téléchargé, aucune installation personnelle, aucune modification JNI/permissions/signatures critiques ni fusion.

## Ce qui est effectivement programmé

Nouveaux fichiers expressionRuntime : VoiceEmotionController/EmotionRenderPlan, EmotionCapabilityMatrix, BoundedPcmProsody et EmotionAvatarCues. Les anciens fichiers d'expression ne changent pas. `ExpressionStyleProfiles.neutral`, `LocalProsodyPlanner.requireExactText`, `ExpressionSpeechAdapter` et le renderer/SDK existants sont réutilisés pour A. Aucun prompt de style n'entre dans le texte audible, aucun second moteur.

| Intention | Code / différence PCM | Emotion audible reconnue |
|---|---|---|
|NEUTRAL|Copie exacte bit à bit, y compris zéro signé ; aucun gain/correcteur appliqué|Référence, pas une émotion|
|JOY|Enveloppe d'attaque/énergie lissée, gain limité, intensité0–2|**Non testée humainement**, effet logiciel uniquement|
|SURPRISE|Attaque/relâchement d'énergie lissés différents deJOY|**Non testée humainement**, effet logiciel uniquement|
|CALM|Atténuation progressive lissée, durée inchangée|**Non testée humainement**, effet logiciel uniquement|
|CONCERN/THOUGHTFUL/SADNESS|STYLE_UNSUPPORTED ; fallback neutre exact uniquement demandé explicitement|NO-GO sur ce moteur actuel|
|LAUGH_OPTIONAL|STYLE_UNSUPPORTED ; aucun rire préenregistré ou synthétique ajouté|NO-GO|
|Hauteur/débit/time-stretch|Non implémentés, non annoncés|NO-GO sans DSP qualitatif validé|

Ces trois transformations d'énergie produisent une vraie waveform différente ; **cela ne prouve ni surprise/joie/tristesse, ni naturel, ni fidélité perceptive**. Le système ne peut pas afficher EMOTION_AUDIBLE_VALIDATED. Intensité0 signifieNEUTRAL/effectif sans transformation, pas une fausse intensité émotionnelle. Aucun filtre ne prétend corriger le défaut métallique. Les champs natifs restent non supportés.

`AudibleComparisonSession` retient seulement deux PCM<=20s chacun en RAM privée, liés au binding exact. B provient du PCM A, même phrase/profil/graine/sampler, sans deuxième génération coûteuse. Changement de profil/effacement supprime la session et efface ses tableaux ; mauvaise identité, note sans deux présentations et réécoute après fermeture refusées. L'observation d'écoute explicite est une déclaration locale, jamais une preuve automatique d'émotion ni un upload.

UI dans l'APK `-PvoiceLab=06`, ID **com.jarvis.voice.studio.voice06** : cinq phrases publiques, générationA, consentement logiciel, calculB, réécoutes et note locale. La sélection des émotions natives reste grisée. Génération/écoute utilisent le même worker/admission/AudioFocus/Arrêt ; aucune donnée d'archive ou de profilB modifiée. La clé de005J n'est pas utilisée/changée ; APK de laboratoire indépendante.

## Démonstration réelle et preuves

[20 sorties WAV publiques](public-generated/README.md), cinq phrases x NEUTRAL/JOY/SURPRISE/CALM. Sources : WAV neuronaux publicsVOICE05, SHA épinglés à son reçu ; pas de fichier personnel ni nouveau modèle. NEUTRAL est exact en PCM **et copie des octetsWAV** ; source et longueurs conservées. [Mesures](proofs/PUBLIC-DSP.json) : nombre de samples changés, RMS entrée/sortie, différence maximale, coûtDSP et SHA de chaque sortie, `humanListening=false`/`humanValidated=false` partout.

Première phrase :113280samples,113047changés pour chacun des trois effets ; coûts Linux observés environ2,5–2,8ms (neutre0,8ms) dans cette exploration. Ce ne sont ni une latenceTTS ni des chiffres Android. Les coûts variables de JIT/GC n'autorisent aucune promesse de gain énergétique ou de vitesse sur Xiaomi. Le calculB évite une seconde génération par construction ; cela n'accélère pas la première parole.

Les WAV sont volontairement ajoutés malgré la règle générale *.wav ignorée : **uniquement ces20 dérivés publics vérifiés**, dans public-generated, attributionCCBY4.0/Kyutai/Alba et modifications fournies. Aucun poids gated ou enregistrement personnel publié. Ils ne sont pas embarqués dans l'APK. Les noms sont révélés : l'écoute comparative offerte n'est pas une étude aveugle ; une étude doit masquer les noms/contrebalancer les ordres.

Tests JVM réels : neutral/fallback exacts, trois waveforms différentes, déterminisme, intensité0, pas de modification source, bornes20s, clipping/non-finis, annulation et refus de capacités/consentement. Test public sur les cinq phrases. Build/Lint et APKtests locaux réussis ; CIAPI26/35 en cours à la publication initiale. Les observations humaines sont simulées comme entrées de test, **aucune écoute humaine réelle déduite** de ces tests.

## Cues AVA-05 et moteurs alternatifs

Cues relatifs en échantillons au PCM effectivement rendu : plage du segment et plage d'enveloppe logicielle. Pas de transport texte/audio/UUID/visage. Aucun sourire/rire ou émotion reconnu localisé n'est inventé ; ces cues faciaux sont NO-GO. Pas d'ancre d'horloge audiovisuelle réelle fournie.

`ExpressiveBackendSelection` refuse Qwen3-TTS et Chatterbox ; aucune factory, poids ni deuxième moteur activés. L'évaluation officielle précédente est conservée : [VOICE04/EMOTIONS](../mission-voice-04/EMOTIONS.md), licencesApache2/MIT, français selon variantes ; fichiers sélectionnés environ2,51Go Qwen0,6BBase+codec,4,54Go1,7BBase,3,21GoChatterboxV3, **pas des mesures de PSS**. CustomVoice à instructions n'est pas Base clonantB ; exaggeration n'est pas une classe surprise. Aucun portARM64/CPU/budget thermique qualifié. JVFP1 B n'est pas portable automatiquement. Recommandation : étude future sur matériel dédié, après autorisation de poids/port distincte.

## Reproduire et prochaine action

```sh
./gradlew -PvoiceLab=06 :expressionRuntime:test :voiceSdk:test :voice04Runtime:test :voice04Studio:assembleDebug :voice04Studio:assembleDebugAndroidTest :voice04Studio:lintDebug --no-daemon --max-workers=2
python scripts/voice06/check_scope.py
ANDROID_SERIAL=emulator-5554 bash scripts/voice06/android_audio.sh
# Démo publique sans modèle :
./gradlew :expressionRuntime:test --tests '*EmotionPublicDemoTests' --no-daemon
```

Statut PARTIEL : effets logiciels et comparaison exécutables ; **émotions sémantiques et qualité perceptive NO-GO/non validées**. Prochaine action : écouter les sorties publiques sans envoyerB, puis évaluer en aveugle émotion reconnue/naturel/timbre/intelligibilité/artefacts. L'essai B/installation personnelle reste ultérieur, volontaire et hors Cloud. Aucun résultat Xiaomi inventé.
