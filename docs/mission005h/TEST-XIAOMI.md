# Recette 005H — entièrement depuis le Xiaomi

Conserver 005G, 005F et 005D. Installer uniquement l'APK **005H signée** dans
la nouvelle application « JARVIS VOICE IDENTITY 005H ». Aucun remplacement ni
désinstallation. Commencer téléphone refroidi, volume média et route audio fixes.
Toutes les opérations utilisent un thread CPU et les gardes RAM/thermiques.

1. Importer le ZIP FP32 déjà validé depuis **Téléchargements local**. Pas de
   reconstruction ni nouvelle demande de modèle Cloud. Son SHA256 reste
   `9436555c6965e3480c06f51fedced0f512a8316e482a7e54df0f4ee83d0a01cd`.
2. Donner un nom local à l'identité, une note de consentement et confirmer la
   propriété/permission explicite de la voix. Créer l'identité.
3. Confirmer de nouveau consentement et même personne pour chaque référence.
   Choisir le début et une durée **contiguë 5–15 s**, puis ajouter un MP3/WAV
   local. Le WAV accepte mono/stéréo PCM16 ou float32 ; le MP3 est décodé sur le
   téléphone. Aucun nombre minimal ; répéter pour deux fichiers, puis 5–10 si utile.
4. Lire l'analyse de niveau/silence/écrêtage, écouter chaque extrait. Les labels
   sont manuels : annoter le premier NEUTRE et le second selon votre jugement.
   « Proposition 5 s » ajoute une nouvelle référence sans remplacer l'ancienne ;
   ce n'est pas une reconnaissance automatique de parole ou de locuteur.
5. Avant chaque import, cocher « Conserver cet extrait… » si un réencodage futur
   est souhaité. Sinon, l'extrait reste privé jusqu'à l'encodage réussi puis est
   supprimé. Le MP3 original n'est jamais gardé par l'application après décodage.
   Le fichier original présent dans Téléchargements n'est pas supprimé.
6. Sélectionner la première référence, créer son profil ; sélectionner la seconde
   et créer un autre profil. Vérifier que la bibliothèque affiche **deux UUID**.
   Choisir le profil neutre comme **NEUTRE PRINCIPAL et actif**. Aucune moyenne,
   fusion de caches ou activation d'une variante n'est automatique.
7. Générer une phrase nouvelle avec le profil actif. Les réglages 005G sont
   disponibles : LSD 1/2/3/5, températures 0,2/0,3/0,5, EOS −4, queue officielle.
   Reprendre les mêmes étapes/température que votre essai 005G pour isoler
   l'effet du choix de profil.
   Arrêt coupe la lecture ; un calcul natif peut finir son appel avant annulation.
8. Pour A/B, choisir les deux profils de **la même identité**, texte identique,
   graine **42**, mêmes étapes/température et PCM16. Générer A puis B. Si les
   paramètres changent, régénérer A avant B. Deux conditionnements identiques
   sont refusés même si leurs fichiers ont des hashes différents.
9. Réécouter A/B sans recalcul. Alterner l'ordre d'écoute. Après chaque écoute
   réellement audible, confirmer et noter naturel, ressemblance, constance de
   timbre, intelligibilité et artefacts métalliques. Les notes restent privées,
   liées au profil et au PCM réellement écouté. Aucun score automatique de fidélité.
10. Fermer/réouvrir l'application : retrouver identité active, références,
    profils et profil actif. Supprimer **un seul profil** avec confirmation ;
    vérifier que l'autre et les applications 005G/F/D restent intacts. Supprimer
    le profil actif laisse « aucun profil actif » jusqu'au prochain choix explicite.

Textes communs : « Demain, je prendrai le train pour Paris. » ; « Je garde ma
voix et mes fichiers sur mon téléphone. » ; « Vraiment ? Je pensais arriver
plus tôt ! ». Comparer les mêmes textes, paramètres et ordre de mesure. Noter
durée/RTF, CPU, PSS échantillonné, thermique et batterie. Les compteurs batterie
peuvent être indisponibles ou trop grossiers ; aucune optimisation énergétique
n'est présumée. Aucun enregistrement personnel ne doit être envoyé au développeur.

## Archive chiffrée facultative

Choisir « Sauvegarder cette identité », éventuellement inclure les WAV encore
conservés, puis un document dans **Téléchargements/stockage interne local**.
Créer et confirmer un mot de passe fort (12–128 caractères), gardé séparément.
Le fichier .jvi ne contient aucun modèle. Ne pas choisir Drive ou un dossier
synchronisé. Aucun PC, Termux, ADB ou débogage sans fil n'est nécessaire.

Pour tester sans mettre en danger l'identité personnelle :

1. Archiver l'identité personnelle, puis tenter l'import de sa propre archive
   sans rien supprimer. Un mauvais mot de passe doit être refusé. Avec le bon
   mot de passe, le message « Identité déjà présente » est le résultat attendu :
   l'authentification, les SHA et les formats sont contrôlés avant ce refus.
   Cela vérifie le contenu, **pas une écriture de restauration**.
2. Créer une seconde identité nommée TEST ARCHIVE, idéalement avec un petit
   extrait consenti et un profil. L'archiver, puis supprimer **seulement cette
   identité de test**, jamais l'identité personnelle principale.
3. Restaurer l'archive TEST ARCHIVE : retrouver son UUID, sa référence et son
   profil. Revenir ensuite explicitement à l'identité personnelle principale.

Un UUID déjà présent est toujours refusé, sans remplacement. Ne pas désinstaller
l'application contenant son unique copie de profil pour tester l'archive.
Voir [MIGRATION-EXPORT.md](MIGRATION-EXPORT.md).
Confirmer le consentement légal des voix de l'archive avant toute restauration ;
les confirmations et notes d'origine de chaque référence restent conservées.

005H n'est pas encore validée physiquement sur ce Xiaomi par le développeur.
L'amélioration 005G rapportée par le propriétaire ne valide pas automatiquement
la bibliothèque et les archives 005H.
