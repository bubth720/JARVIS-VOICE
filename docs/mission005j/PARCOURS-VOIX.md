# Créer ou retrouver sa voix

L’application JARVIS Studio 005J reste hors ligne, à côté de 005I. Ne pas supprimer 005I ni sa sauvegarde.

## Vous avez déjà votre voix dans 005I

Dans 005I, activer votre profil préféré et sauvegarder l’identité en `.jvi` chiffré dans Téléchargements. Dans 005J : Créer → Importer une voix existante, choisir ce document local, confirmer le droit de cloner cette voix et saisir le mot de passe. Aucun réencodage. Comparer localement UUID/SHA256/pack affichés, puis confirmer « Utiliser cette voix ». L’archive d’origine et 005I restent intactes. Les WAV sont absents si vous ne les avez pas inclus.

## Créer une nouvelle voix

1. **Choisir** : Créer → nouvelle voix. Importer le ZIP FP32 existant si nécessaire.
2. **Identité** : nom local, note facultative, consentement légal. Cette identité est réutilisée pour tous ses fichiers ; votre ancienne voix active n’est pas remplacée.
3. **Ajouter** : choisir un MP3 ou WAV local, un fichier à la fois, de 5 s à 3 min / 32 Mio maximum. Un audio de 3–4 s est refusé immédiatement avec une aide. Aucun collage automatique.
4. **Vérifier** : la durée réelle est affichée. Sélectionner une portion contiguë de 5–15 s, l’écouter, vérifier niveau/silence/écrêtage. Étiquette et commentaire sont manuels. Confirmer consentement et même locuteur pour ce fichier, choisir explicitement si son WAV doit rester après encodage, puis valider.
5. **Créer** : encoder ce seul extrait. Attendre ou Arrêter. Les références déjà validées restent présentes en cas de refus thermique/mémoire ou d’annulation. Aucun encodage de dix fichiers imposé.
6. **Essayer** : confirmer un profil puis faire parler JARVIS. Une seule référence suffit. Ajouter un autre audio de la même identité si désiré ; comparer les profils dans Avancé, sans choix automatique du meilleur.
7. **Sauvegarder** : archive chiffrée facultative `.jvi`, WAV facultatifs s’ils existent encore. Mot de passe personnel de 12–128 caractères, conservé séparément. Pas de récupération sans lui.

Retour ne supprime aucun profil ni référence. Le brouillon et son étape sont privés et reprenables après rotation/reprise ; les cases de consentement et mots de passe ne sont pas réutilisés automatiquement. Une nouvelle identité ne remplace pas une précédente. Gérer/supprimer reste dans Mes voix, avec confirmation distincte.

## Faire parler JARVIS

Parler affiche la voix confirmée, le modèle, le texte et l’action principale. Réécouter apparaît pour un cache valide ; Arrêter apparaît pendant le travail. Options contient pauses et contexte : pauses désactivées initialement, demande explicite de 250 ms par exemple. Écrire 2–3 phrases complètes, une par ligne. Les ambiguïtés sont refusées avec une aide ; aucun découpage ou changement de texte silencieux.

Un contexte non NEUTRAL est une intention enregistrée : effet émotionnel indisponible. L’interface ne change pas le sampler pour simuler une émotion. Le son n’est pas amélioré par le changement d’interface.
