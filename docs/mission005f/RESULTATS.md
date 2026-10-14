# Mission 005F — lecture différée, diagnostic et PCM de réécoute

## Signalement Xiaomi et portée

L'utilisateur a créé son premier profil FP32 local avec 005E 0.1.1 CI. Pour
« Demain, je prendrai le train pour Paris. », graine 42, deux essais n'ont produit
aucun son et ont échoué au drain : 40924 ms au total, CPU 38718 ms, PSS 1304,2 Mio,
thermique 0 et batterie 26,9 °C. Ces valeurs sont rapportées, pas des mesures Cloud.
Le profil privé et son PCM ne sont pas accessibles au développeur.

L'ancien AudioTrack STREAM PCM_FLOAT allouait 192000 octets = 48000 frames,
soit deux secondes à 24000 Hz. L'application appelait play après 24000 frames.
Sur Android >=31, le seuil STREAM est configurable et peut rester égal à la
capacité du tampon. Une phrase plus courte que ce seuil peut ne jamais démarrer,
malgré playState PLAYING. Le délai fixe de 25 secondes ne faisait pas la
comparaison de ce seuil et ne distinguait ni démarrage bloqué ni silence PCM.
C'est une hypothèse vérifiable pour Xiaomi, pas une cause physique encore prouvée.

## Correctif

Le lecteur **005F uniquement** utilise maintenant MODE_STATIC PCM16, adapté à
la phrase complète différée. Il charge exactement les samples, vérifie la taille
d'écriture et l'état INITIALIZED, règle le volume piste à 1, demande le focus
média, puis appelle play. Il ne préremplit plus un grand tampon STREAM. La
conversion PCM16 borne les amplitudes pour la sortie, sans modifier le PCM32 brut
conservé ni les calculs, poids ou caches FP32 du modèle.

La réussite exige que playbackHeadPosition, ou un AudioTimestamp déjà présenté
et appartenant à cette lecture, atteste de toutes les frames soumises. Le temps
écoulé n'est jamais une preuve. Compteur incohérent/réinitialisé, état pause/stop
inattendu, focus perdu, écriture partielle/nulle et absence de PCM sont des échecs.
Le démarrage sans progression et la stagnation de deux secondes sont distingués.
Le plafond global dépend de la durée réelle du PCM + quatre secondes. Aucun
échantillon manquant n'est ignoré et aucun silence de remplissage n'est ajouté.
ARRÊT annule immédiatement et pause/flush la piste ; le worker la libère.

Avant lecture : nombre de samples, 24000 Hz, durée, pic et RMS bruts, proportion
|x| < 0,0001, nombre de valeurs non finies et proportion nécessitant l'écrêtage.
Le diagnostic AudioTrack conserve état, playState, fréquence, mode, volume média
et état muet, focus, route (type/id seulement), écritures et écritures nulles,
frames soumises, playbackHeadPosition, timestamps, progression et underruns.
Les erreurs ne remplacent pas ces diagnostics dans l'interface. Un rapport
numérique borné est conservé dans noBackupFilesDir, sans texte de la phrase,
chemin personnel, nom Bluetooth ou contenu audio dans les logs.

La génération, le démarrage effectivement observé, la lecture complète, le délai
et l'annulation sont affichés séparément. Aucun compteur ne prouve que
l'utilisateur a réellement entendu une voix ou que la ressemblance est validée.
Le bouton de signal synthétique 440 Hz / 1 s n'utilise ni profil ni modèle et ne
remplace pas le PCM de réécoute. L'extrait privé reste écoutable séparément.

## Cache privé et préservation

Une seule dernière synthèse **complète** est conservée en PCM32 exact : maximum
480000 samples / vingt secondes / 1920010 octets plus petit manifeste et SHA256.
Une replacement atomique conserve la précédente sur erreur ou annulation. Le
staging ajoute au maximum un second exemplaire pendant sauvegarde ; il est
supprimé en cas d'échec. La réécoute vérifie le SHA256 et ne charge aucun modèle.
Le bouton de suppression PCM ne touche pas au profil ni au pack. La suppression
explicite du profil supprime aussi le PCM qui pourrait contenir cette voix.
Ni réseau, ni backup système, ni export automatique. Un thread ONNX par défaut,
protections thermiques/mémoire et stockage privé sont conservés.

## Signature et installation

La clé privée de l'APK 005E 0.1.1 GitHub (certificat
152c7bd6684521a92d85a638115ce8696f5b8c500339095b01d72f338f6b515e) n'est pas
présente ; on ne peut pas signer une mise à jour compatible. **Ne pas désinstaller
005E ni 005D** pour cet essai. Le build parallèle 005F conserve les installations
existantes, sous com.jarvis.voice.voiceprototype005f.

Une nouvelle clé stable RSA 3072 est conservée dans le workspace privé, hors
Git et hors artefacts publics. Les futures livraisons signées avec cette clé
pourront mettre à jour 005F sans effacer son profil. La clé doit rester disponible
ou être sauvegardée dans un stockage privé autorisé ; elle n'est pas reproductible
si le workspace est détruit. Aucun envoi externe de clé n'est effectué ici.
La CI utilise ses clés éphémères pour les tests uniquement : son APK diagnostic
n'est pas la livraison stable. Le ZIP de livraison publique ne contient qu'une
APK signée, aucun modèle, audio, profil ou clé. Son certificat, son SHA256 et
l'absence de poids sont vérifiés de nouveau en CI.

Le même code peut construire le package 005E sans prototypeSidecar, mais cela ne
résout pas l'absence de l'ancienne clé. 005F importe le même ZIP FP32 déjà validé
et un profil JVFP1 déjà créé, sans réencodage. Voir TEST-XIAOMI.md pour la copie
entièrement locale sur Android.

## Validation

Tests JVM : statistiques non finies/silence, fin sur la dernière frame, démarrage
bloqué, compteur terminal stagnant, pause, reset et compteur incohérent, preuve
indépendante par timestamp présenté, cache exact, corruption, limite et annulation.
Tests Android : phrases 5 ms, 100 ms, 1 s et 3 s en STATIC, absence de mutation
PCM, Arrêt, non-finis, aucune donnée, préannulation. Un test STREAM synthétique
force le seuil à deux secondes pour reproduire l'absence de progression avec une
seconde de PCM, et consigne aussi le seuil initial de l'émulateur.

Le pack FP32 original n'est pas reconstruit. 005D, son lecteur, son encodeur,
la génération FP32 et les formats des 18 états ne sont pas modifiés. Les tests
sur appareil Xiaomi et l'évaluation perceptive restent à effectuer.

## Résultats vérifiés de livraison

29/41 tests JVM réussis ; 12 nécessitant d'autres modèles/fixtures privées sautés,
zéro échec. Les cinq nouveaux tests ciblés 005F réussissent séparément.
10/10 tests Android 15 x86_64 réussissent, sans saut, sur deux passages.
L'émulateur annonce initialement STREAM threshold=48000 frames et buffer=48000.
Avec 24000 frames chargées, le compteur reste à zéro après play. En STATIC, les
120, 2400, 24000 et 72000 frames sont toutes présentées, avec zéro underrun. Les
preuves synthétiques exactes sont dans AUDIO-EMULATEUR.txt ; aucune voix privée.

La CI 005F [38647931807](https://github.com/bubth720/JARVIS-VOICE/actions/runs/38647931807)
est entièrement réussie (build, lint, audit de l'APK stable, Android). La CI
Android stable réussit. Build/inférence 005D réussissent ; son test AudioTrack
sur émulateur déjà connu échoue encore (4/5, underrun=1, 72000 frames envoyées
et soumises). Aucune modification de ce lecteur ni de la génération n'est faite.

[Livraison **stable**](https://github.com/bubth720/JARVIS-VOICE/actions/runs/38647931807/artifacts/11933028756) :
extraire `JARVIS-VOICE-005F-stable.apk`. Ne pas installer l'APK CI diagnostic.
L'artefact final a été téléchargé et le SHA256 de son APK comparé au build local :
`af9f1b3974163c8c255f551c88bdb65a9e9bd67adf75f43d75b0edd1755bf083`,
29156604 octets, certificat stable
`aba9ee43361fd342e119a33ae5a628e63199b3b6ef4f14d3a3619f6d5678d89e`.
Les clés privées restent dans `work/signing/voice-prototype-stable/`, permissions
0600, hors Git, hors APK et hors artefacts. Pour les futures livraisons, définir
JARVIS_PROTOTYPE_KEYSTORE et JARVIS_PROTOTYPE_PASSWORD_FILE vers les fichiers
privés existants et construire avec `-PprototypeSidecar=true`. Vérifier exactement
ce certificat ; ne pas générer une nouvelle clé si les fichiers sont absents.
La migration et l'audibilité du profil réel sur Xiaomi restent à valider.
