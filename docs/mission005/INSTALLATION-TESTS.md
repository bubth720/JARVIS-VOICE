# Installer et tester Mission 005 sans PC

## Quel APK choisir ?

**Commencer par `JARVIS-Pocket-Probe-M005-LotA.apk` (0.2.0-lotA)** pour isoler la correction audio. Après validation, `JARVIS-Pocket-Probe-M005-Reference.apk` (0.3.0-reference) ajoute l'import/analyse MP3 et conserve la même correction. Le clonage est désactivé dans les deux versions.

1. Télécharger l'APK depuis la livraison, puis l'ouvrir dans Téléchargements. Vérifier le nom **JARVIS Pocket Probe** avant installation. Il met à jour Pocket Probe ; JARVIS VOICE et CORE ne sont pas remplacés.
2. Conserver le pack français actuel : les modèles n'ont pas changé. S'il a disparu, importer `JARVIS-Pocket-Probe-fr-6l-alba.zip` sans l'extraire.
3. Si Android refuse une mise à jour à cause d'une signature différente, désinstaller **uniquement Pocket Probe**, puis installer cet APK et réimporter le pack. Attention : désinstaller efface modèles et références privés. Conserver le MP3 original dans vos fichiers ; aucun profil créé n'existe dans cette livraison. Ne pas désinstaller JARVIS VOICE.
4. En cas d'avertissement pour développeur inconnu, utiliser « Installer quand même » seulement après vérification de la provenance si cette option est proposée. Ne pas désactiver Play Protect globalement.

Les APK directs de cette livraison utilisent le même certificat cloud que la livraison M004 directe. Les APK GitHub Actions utilisent généralement un autre certificat debug, qui peut aussi changer entre exécutions. Pour les artefacts GitHub : ouvrir la PR brouillon → Checks → exécution Pocket Probe réussie → Artifacts → `jarvis-pocket-probe-apk`. Extraire le ZIP enveloppe et installer **pocketProbe-debug.apk**. Le fichier `pocketProbe-debug-androidTest.apk` est un outil de test d'ingénierie, pas l'application à lancer. Le ZIP `pocket-fr-6l-alba-model` enveloppe un ZIP modèle qui doit rester intact. Rétention 14 jours et connexion GitHub requise. Aucun PC nécessaire pour ces opérations.

## Test prioritaire : fluidité du lot A

Téléphone suffisamment chargé, débranché, refroidi ; volume multimédia stable, haut-parleur interne, aucune autre lecture audio. Noter si un casque/Bluetooth est utilisé. Ne pas partager de MP3 ou de référence privée pour transmettre les résultats.

1. Choisir **Pocket TTS français — CPU**, **mode économique activé** et **lecture progressive désactivée**. Conserver le texte initial. L'attente avant le son est normale.
2. Effectuer une lecture à froid, puis cinq lectures du même texte à chaud. Vérifier chaque phrase complète : zéro coupure ou répétition audible. Noter tous les diagnostics après chaque essai.
3. Tester « Oui. », « Le café est prêt, vous pouvez arrêter la lecture. » et une nouvelle phrase courte. La limite reste 300 caractères / 50 jetons / 20 secondes d'audio.
4. Toucher **Arrêt** immédiatement après Lecture, pendant l'attente ; aucun son ne doit commencer ensuite. Retester Arrêt pendant le son. Relancer Lecture pour vérifier la récupération. Retourner à l'accueil pendant la génération : traitement annulé.
5. Désactiver le mode économique (2 threads), attendre le refroidissement et répéter les mêmes cinq lectures. Les sessions sont rechargées au changement de threads, puis réutilisées.
6. Activer le mode avion **et couper Wi-Fi/Bluetooth** ; refaire Lecture/Arrêt avec le pack déjà importé. Choisir ensuite **Android TTS — secours explicite** et vérifier ses trois tests antérieurs. Une erreur Pocket ne doit pas changer le moteur.
7. Optionnel, après réussite différée : tester la lecture progressive. Comparer les réserves/rechargements et les sous-alimentations. Si elle saccade, revenir au différé, même si le départ est plus lent.

### Tableau à transmettre (sans audio personnel)

| Mode / threads / froid ou chaud | Génération ms | Audio s | RTF | Premier avancement matériel ms | PSS max Mio | Thermique max | Underruns / recharges | Coupures entendues |
|---|---:|---:|---:|---:|---:|---:|---|---|
| différé / 1 / froid | | | | | | | | |
| différé / 1 / chaud ×5 | | | | | | | | |
| différé / 2 / chaud ×5 | | | | | | | | |
| progressif / 1 ou 2 | | | | | | | | |

RTF > 1 signifie génération plus lente que l'audio : garder le différé. Le compteur d'avancement matériel estime le premier son ; il ne mesure pas le haut-parleur acoustiquement. Les attentes d'écriture à zéro sont une régulation normale du buffer ; les underruns et coupures sont des problèmes. Objectif : **aucune coupure**, compteur underruns 0 en différé, Arrêt efficace puis récupération. Un compteur nul seul ne prouve pas une bonne qualité perceptive.

Pour consommation/température : comparer des séries de 5 minutes avec les mêmes phrases et pauses, luminosité fixe et appareil débranché, depuis une température similaire ; relever batterie % avant/après et état thermique 0–6 affiché. Le % batterie est grossier et ne permet pas de calculer une énergie précise. Android ne donne pas une température CPU fiable en degrés sur tous les appareils ; ne pas confondre état thermique et °C. Arrêter si l'état devient sévère (≥3), si le téléphone chauffe fortement ou ralentit. L'application annule le calcul si l'état sévère est observé ; aucun test de stress prolongé n'est demandé.

## Après lot A : infrastructure MP3 de la version Reference

1. Installer l'APK Reference, confirmer l'autorisation puis **Importer un MP3**. Choisir votre fichier déjà sur le téléphone. Aucun envoi au cloud.
2. Début : choisir une partie parlée sans musique ni autre personne ; durée : commencer à 10 s, ajustable de 5 à 15 s. Toucher **Convertir et analyser**, puis **Écouter l'extrait local**. Noter durée/niveau/écrêtage, vérifier la qualité à l'écoute.
3. Tester Arrêt pendant conversion et écoute, puis relancer. Un MP3 invalide/tronqué doit être refusé sans remplacer la référence précédente.
4. Fermer complètement puis rouvrir : le MP3 privé et le choix début/durée restent présents. Refaire l'extrait en mode avion. **Supprimer ma référence** doit l'effacer ; après redémarrage, elle doit être absente.
5. **Créer le profil** et **Profil personnalisé** restent désactivés : ce résultat est attendu. Alba reste la seule voix Pocket disponible. Un extrait WAV n'est pas une voix clonée.

Ne pas envoyer le MP3, le WAV ou de futurs profils dans la PR, une issue ou un journal de compilation. Pour diagnostiquer, seules les valeurs de performance et l'erreur générique sont nécessaires.
