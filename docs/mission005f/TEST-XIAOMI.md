# Essai 005F sur Xiaomi — aucun PC, aucun envoi du profil

1. Garder **005E 0.1.1** et **005D** installées. Installer uniquement l'APK
   **005F stable** (package com.jarvis.voice.voiceprototype005f). L'APK CI diagnostic
   utilise une autre clé : ne pas la mélanger avec la livraison stable.
2. Dans 005F, lancer le bouton **Tester le lecteur : signal synthétique 1 seconde**.
   Vérifier le volume média et la route affichés. Noter séparément « son entendu »
   et « lecture terminée » : les compteurs ne prouvent pas l'audibilité physique.
3. Importer dans 005F le **ZIP complet FP32 déjà présent** dans Téléchargements.
   Aucun nouveau téléchargement, export de modèle ou reconstruction n'est requis.
4. Copier le **profil déjà créé** depuis 005E comme ci-dessous, puis cocher le
   consentement et utiliser **Importer un profil FP32 déjà créé — sans réencodage**.
   Le profil original reste dans 005E. Ne pas utiliser « créer/remplacer » pour
   cette migration. Le fichier JVFP1 est contrôlé : packId, 18 états, types,
   dimensions et valeurs finies. Le WAV privé n'est pas nécessaire à la synthèse.
5. Choisir le profil personnel, saisir la phrase « Demain, je prendrai le train
   pour Paris. », graine 42, et générer une seule fois.
6. Avant lecture, noter samples/durée/pic/RMS/silence/non-finis. Ensuite noter
   AudioTrack : état, playState, route, volume, submitted, head et progression.
   En cas d'échec, utiliser **Réécouter la dernière synthèse privée — sans recalcul**.
7. Essayer ARRÊT pendant la réécoute, puis relancer. Comparer ensuite Alba FP32 et
   le profil personnel sur la même phrase. Ne déclarer le clonage validé qu'après
   audibilité, intelligibilité et ressemblance constatées sur Xiaomi.

## Copier le profil de 005E en restant sur le même téléphone

Cette étape demande Termux depuis F-Droid et le **débogage sans fil Android**,
qui nécessite habituellement une connexion Wi-Fi. Aucun ordinateur ou serveur
externe n'est utilisé : ADB se connecte au service de débogage du même téléphone.
005E est une APK debug ; run-as permet l'accès local à son sandbox. Si run-as est
refusé ou si le débogage sans fil est indisponible, arrêter la migration et
conserver 005E et son profil ; ne pas désinstaller et ne pas réencoder par défaut.

Dans Termux :

```sh
pkg install android-tools
termux-setup-storage
```

Activer les options développeur puis **Débogage sans fil**. Ouvrir « Associer
avec un code » et utiliser les ports montrés par Android (ils sont différents) :

```sh
adb pair 127.0.0.1:PORT_ASSOCIATION
adb connect 127.0.0.1:PORT_CONNEXION
```

Saisir le code dans Termux uniquement, jamais dans le chat. Si le service n'écoute
pas sur loopback, utiliser l'adresse de **ce même téléphone** affichée par Android.
Une fois connecté, vérifier qu'il existe exactement un state.bin :

```sh
adb shell "run-as com.jarvis.voice.voiceprototype005e sh -c 'ls no_backup/profiles/*/state.bin'"
```

Si plusieurs fichiers sont listés, arrêter et ne pas concaténer les profils.
S'il existe un seul fichier :

```sh
adb exec-out "run-as com.jarvis.voice.voiceprototype005e sh -c 'cat no_backup/profiles/*/state.bin'" > ~/storage/downloads/JARVIS-personal-FP32.bin
sha256sum ~/storage/downloads/JARVIS-personal-FP32.bin
```

Comparer avec l'empreinte locale de la source affichée par :

```sh
adb shell "run-as com.jarvis.voice.voiceprototype005e sh -c 'sha256sum no_backup/profiles/*/state.bin'"
```

Le **nom de chemin montré dans Termux ne doit pas être envoyé**. Seules les deux
empreintes doivent être comparées localement. Ne poursuivre que si elles sont
identiques. Importer le .bin local dans 005F. Ensuite supprimer ce fichier
intermédiaire de Téléchargements, conserver les copies dans les sandboxes des
applications et désactiver le débogage sans fil. Ne pas déplacer le profil vers
Google Drive ou un autre cloud. Choisir un dossier Téléchargements local non
synchronisé ; cette copie temporaire reste sous le contrôle du propriétaire.

Cette procédure protège le profil existant mais la migration locale n'a pas
encore été exécutée sur ce Xiaomi. La compatibilité JVFP1 est testée dans le code ;
la disponibilité de run-as / ADB dépend de la configuration du téléphone.
