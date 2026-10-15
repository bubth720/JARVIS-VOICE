# Mission 005G — naturel vocal : comparaison et prototype indépendant

## Résultat actuel

Le sampler multi-étapes est implémenté conformément à `lsd_decode` de Kyutai
Pocket TTS 3.3.0. Il n'est **pas démontré qu'il améliore le naturel** du modèle
français 6 couches. Aucun enregistrement personnel n'a été utilisé dans Cloud et
aucune écoute humaine de la voix du propriétaire n'a été réalisée ici.

Le mode A reproduit 005F : une étape, température 0,3, EOS −4 et queue officielle
selon le texte. Le mode B permet 1, 2, 3 ou 5 étapes, températures 0,2/0,3/0,5,
EOS −5/−4/−3 et queue officielle/3/5 frames. Ce sont des contrôles expérimentaux,
pas des préréglages certifiés meilleurs. Le mode 1 étape du nouveau moteur et
005F produisent des PCM **bit-identiques** dans le test JVM public Alba, même
profil FP32 et graine Java 42.

## Méthode et parité

Checkpoint français exact : SHA256
`dd5fe98f5577ecfd56db9af8057cd05fe20cbc91560042aec8a56cfb01c34995`,
révision `d0ebc5deb50bc6b225f686782f124dd37e2dca0a`. Le pack FP32 existant et ses
hashes ne changent pas. Aucun nouvel export ONNX ni reconstruction de pack.

Pour N étapes : s=i/N, t=(i+1)/N, v=Flow(conditionnement,s,t,latent_courant), puis
latent_courant += v/N. Un seul bruit initial par frame, écart type sqrt(température).
Ni nouveau bruit à chaque sous-étape ni répétition de s=0,t=1 avec l'ancien latent.
La condition FlowLM reste fixe pendant ces sous-étapes et avance une fois par
frame audio, comme dans le code officiel.

72 cas de sampler à entrées identiques passent les seuils inchangés
atol=rtol=2e−4 : deux conditionnements synthétiques, trois graines, trois
températures et quatre nombres d'étapes. Écart final maximal :
3,0994415283203125e−6. Directions à chaque sous-étape également comparées.
Les traces PyTorch officielles sont utilisées par le test Kotlin : temps,
latent courant et mise à jour sont contrôlés à chaque appel. Voir
[SAMPLER-PARITE.json](SAMPLER-PARITE.json).

114 générations complètes comparent des récurrences PyTorch et ONNX indépendantes
sur deux références publiques autorisées (Alba CC BY 4.0, Selfie CC0), quatre
phrases françaises, graines 0/42/123, et variations température/EOS/queue.
Le bruit initial commun est issu de NumPy PCG64 pour isoler le calcul réseau.
Une graine PyTorch, NumPy et Java n'implique pas des nombres aléatoires identiques.
Sur Android, Java Random et la même graine sont conservés entre A et B.

114/114 sorties sont finies, non silencieuses et atteignent EOS. **66/114 passent
la parité audio stricte indépendante** ; les 48 autres échecs restent conservés,
sans seuils assouplis. La parité du sampler à entrées identiques ne devient pas
une preuve de parité stricte de toutes les récurrences audio indépendantes.
Voir [GENERATIONS.json](GENERATIONS.json).

## Comparaison mesurée — pas un classement de naturel

Sous-ensemble identique : température 0,3, EOS −4, queue 3, 24 cas par ligne.
ASR Whisper small local, INT8 uniquement pour l'outil d'évaluation séparé ; le
moteur TTS reste intégralement FP32. WER normalisé, sans ponctuation/accents.

| Étapes LSD | Audio strict | WER ASR | RTF ONNX médian | Durée audio médiane | SNR PCM16 médian |
|---|---:|---:|---:|---:|---:|
| 1 | 13/24 | 2,22 % | 0,825 | 2,36 s | 77,3 dB |
| 2 | 14/24 | 2,22 % | 0,841 | 2,24 s | 76,4 dB |
| 3 | 14/24 | 5,56 % | 0,879 | 2,20 s | 77,2 dB |
| 5 | 12/24 | 6,11 % | 0,906 | 2,16 s | 77,6 dB |

Aucun écrêtage dans ce sous-ensemble public. Davantage d'étapes n'améliore donc
pas systématiquement l'intelligibilité automatique ; aucune supériorité de 3/5
étapes n'est annoncée. Le WER ne mesure ni le naturel, ni le timbre, ni les
artefacts métalliques. Les pics, RMS, durée, proxy de hauteur et énergie spectrale
sont conservés comme observations acoustiques, pas comme notes perceptives.
Les données et transcriptions sont dans [INTELLIGIBILITE-ASR.json](INTELLIGIBILITE-ASR.json)
et la synthèse dans [SYNTHESE.json](SYNTHESE.json).

Les temps sont indicatifs sur CPU Cloud, avec PyTorch et ONNX dans le même
processus et certaines autres tâches de validation concurrentes. Le pic RSS
combiné est 2928,7 Mio ; **ce n'est pas le PSS Android de 005G**. La consommation,
le comportement thermique et le PSS Xiaomi restent à mesurer. Chaque génération
A/B dans l'APK dispose de ses mesures CPU, PSS échantillonné, température et
compteurs batterie ; une valeur indisponible/grossière n'est pas une preuve
énergétique. Répéter et contrebalancer l'ordre pour comparer les latences.

## PCM brut et conversion

Le PCM32 brut est conservé dans deux caches privés A/B, vingt secondes chacun.
Le même cache peut être relu en STATIC PCM16 (comme 005F) ou FLOAT32 expérimental.
La borne d'amplitude −1..1 est identique dans les deux chemins ; aucun filtre,
équaliseur, réduction de bruit, normalisation automatique ou nouveau poids.
Le SNR cité compare la conversion PCM16 exacte (arrondi Java, échelle 32767,
restitution /32768) au même FLOAT borné. Pics et écrêtage sont mesurés avant la
conversion. Ces chiffres sur références publiques ne prouvent pas l'absence de
défaut acoustique dans le PCM du profil personnel. La comparaison d'écoute doit
être réalisée sur le Xiaomi, à même route et même volume.

## Référence locale et extraits longs

Analyse locale par fenêtres de 20 ms : RMS, pic, écrêtage, proportion de fenêtres
sous −45 dBFS, silence en début/fin. Une portion contiguë de cinq secondes peut
être suggérée sans gain ni assemblage de segments. Elle ne remplace pas le profil
et doit être écoutée avant toute création explicite d'un autre profil dans 005G.
L'énergie ne distingue pas parole, musique, bruit ou plusieurs locuteurs ; les
avertissements le disent explicitement.

Étude numérique, avec répétition d'une référence **publique**, de 15/20/30 s :
les 18 états passent la parité stricte, respectivement 189/251/376 frames avec BOS.
Ce n'est pas une preuve d'amélioration de la référence ni une mesure de mémoire
Android. Le format JVFP1 partagé avec 005F borne les états à 189 et le fichier à
10 Mo : **la création dans l'APK reste limitée à 5–15 s**. Étendre ce format et
ses budgets demanderait une validation distincte. Voir [REFERENCE-LONGUE.json](REFERENCE-LONGUE.json).

## Protection de 005F et évolutivité

Nouvelle application `com.jarvis.voice.voicequality005g`, même clé privée conservée
que 005F, autre sandbox. Les fichiers du module voicePrototype, de 005D, de JARVIS
CORE, l'ExperimentalEngine stable et les formats de profil existants ne changent
pas. Le profil est importable sans réencodage ; la comparaison refuse un texte,
une graine ou un seul octet de tenseur vocal différent entre A et B. Deux PCM
complets privés, SHA256 et métadonnées vérifiées, lecture différée, Arrêt,
un thread ONNX, garde RAM 2 Gio et protections thermiques/mémoire conservés.
Aucun accès réseau, microphone, sauvegarde système ou transfert automatique.
Les cinquante dernières notes humaines restent privées sur le téléphone, liées à
l'audio effectivement relu et à son format de sortie ; aucune note préremplie.

`ExperimentalSpeechBackend` sépare génération et restitution/mesure. Le moteur
6L est le seul implémenté. Chaque futur moteur devra avoir son propre pack,
identité, profil et validation CPU/RAM/thermique ; les 18 caches 6L ne sont pas un
profil universel. Aucun remplacement automatique et aucun mode distant activé.
Voir [MOTEURS-SUPERIEURS.md](MOTEURS-SUPERIEURS.md) et [TEST-XIAOMI.md](TEST-XIAOMI.md).

## Livraison et vérifications du 15 octobre 2026

[Workflow 005G réussi](https://github.com/bubth720/JARVIS-VOICE/actions/runs/38718352623),
commit testé `b0a3a477012bb6c953372a6a8e26ec3f997c5cbc` : build ARM64 et
AndroidTest, lint sans erreur (29 avertissements conservés), audits signature,
manifestes, absence de poids et alignements ZIP/ELF 16 Ko. JVM publique :
33 réussis, 13 ignorés explicitement sur 46, aucun échec. Avec le pack privé en
local : 35 réussis, 11 ignorés sur 46, dont l'égalité exacte 1 étape avec 005F.
Les tests ignorés ne sont pas comptés comme réussis.

Sur Android 35 x86_64 : **14/14 tests instrumentés réussis**, aucun ignoré.
Lecture STATIC FLOAT32 et PCM16, phrases courtes, toutes les frames présentées,
Arrêt, PCM invalide, import ZIP, confidentialité, sampler sur ART et cache A/B
ont été exercés. Le test des notes vérifie le refus sans écoute et sans notes
explicites. Aucun poids gated ou profil personnel dans l'émulateur CI.
Ce résultat ne valide pas une génération 005G ou son naturel sur Xiaomi.
Les [preuves structurées](VALIDATION-CI.json) distinguent ces résultats des
[tests locaux](VALIDATION-LOCALE.json) et des évaluations numériques ci-dessus.

[Télécharger l'APK signée et les rapports](https://github.com/bubth720/JARVIS-VOICE/actions/runs/38718352623/artifacts/11964480867)
(artefact jusqu'au 14 novembre 2026). Installer uniquement
`voiceQuality/build/delivery/JARVIS-VOICE-005G-experimental.apk` extrait de ce ZIP.
Une copie durable de cette même APK est conservée dans
[`deliveries/mission005g/005g-experimental-apk.zip`](../../deliveries/mission005g/005g-experimental-apk.zip).
Il ne s'agit pas d'un pack modèle : réutiliser le ZIP FP32 privé existant.

APK : **29277161 octets**, SHA256
`ab690aa2d4dfad8f80a226c28d59c37e11f994f58e2f45963210c24644bd843c`.
Certificat SHA256
`aba9ee43361fd342e119a33ae5a628e63199b3b6ef4f14d3a3619f6d5678d89e`.
La clé privée stable reste dans l'espace privé Cloud ; elle n'est pas incluse
dans Git ou les artefacts. Les prochaines mises à jour 005G devront conserver
ce certificat et augmenter versionCode. L'APK livrée a été retéléchargée depuis
GitHub puis vérifiée : taille, SHA256, signature et alignements identiques.
Voir [VERIFICATION-TELECHARGEMENT.json](VERIFICATION-TELECHARGEMENT.json).

005F conserve son APK et réussit son
[workflow de non-régression](https://github.com/bubth720/JARVIS-VOICE/actions/runs/38718352618).
Les fichiers 005D et JARVIS CORE ne sont pas modifiés par 005G. Le test émulateur
005D de fin AudioTrack conserve son échec connu `count=1 frames=72000 submitted=72000`
(4/5 tests au run 38717652861) ; il n'est ni masqué ni corrigé dans cette mission.
La PR #5 reste en brouillon, sans fusion dans main.
