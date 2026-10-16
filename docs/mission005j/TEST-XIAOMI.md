# Essai 005J sur Xiaomi — uniquement depuis Android

005J est expérimentale. Garder 005I installée et sa sauvegarde privée ; ne jamais désinstaller une ancienne application pour cet essai. Aucun PC, USB, ADB ou envoi d’audio au Cloud nécessaire.

## Installer et retrouver votre voix B

1. Télécharger uniquement l’APK 005J signée depuis le lien de livraison indiqué dans RESULTATS.md (ZIP contenant seulement l’APK). Autoriser temporairement l’installation depuis votre navigateur/gestionnaire de fichiers si Android le demande. L’application s’appelle **JARVIS Studio 005J**, identifiant `com.jarvis.voice.voicestudio005j` ; elle doit apparaître à côté de 005I.
2. Dans 005J, importer le **ZIP FP32 privé existant**, depuis Téléchargements/stockage interne local. Il ne faut ni le reconstruire ni le publier. Le pack est vérifié par le même manifeste et les mêmes empreintes qu’en 005I. Le sélecteur refuse les fournisseurs distants ; télécharger d’abord les documents privés sur le téléphone.
3. Depuis 005I, sauvegarder l’identité contenant B en archive `.jvi` chiffrée locale, si vous ne l’avez pas déjà. Garder le mot de passe séparément. Ne jamais envoyer cette archive ici.
4. 005J → Créer → Importer une voix existante. Choisir le `.jvi` local, consentir et saisir le mot de passe. Aucune étape d’encodage ne doit être nécessaire. Dans Mes voix, comparer localement UUID/SHA256/pack et choisir explicitement le bon profil B. Ne pas transmettre ces identifiants privés.
5. Parler → même texte, graine, LSD et température que 005I (réglages dans l’engrenage). Écouter, puis Réécouter : la réécoute doit fonctionner sans nouveau calcul. Garder le volume média audible.

## Comparaison pratique avec 005I

| Essai | Action | Résultat à vérifier vous-même |
|---|---|---|
| Neutre | « Demain, je prendrai le train pour Paris. », mêmes paramètres dans I/J | Voix, articulation et fin de phrase préservées ; aucun gain sonore revendiqué |
| Pauses | Options → activer explicitement ; deux phrases complètes, une par ligne ; 250 ms | Pause audible, texte complet, aucune découpe cachée |
| Trois phrases | Trois phrases complètes, une par ligne ; 500 ms | Deux pauses, fin complète, pas de troncature |
| Refus utile | « M. Martin arrive. » puis nouvelle ligne et une seconde phrase | Ambiguïté refusée ; corriger le texte ou désactiver les pauses |
| Arrêt | Arrêter pendant génération puis pendant lecture | Arrêt audio rapide, annulation annoncée, aucun profil perdu |
| Réécoute | Après une génération terminée, Réécouter plusieurs fois | Même PCM, aucun recalcul du modèle |
| Thème/reprise | Clair/sombre, rotation, quitter et revenir | Texte et étape privés conservés ; consentements à confirmer de nouveau |
| Isolement | Ouvrir ensuite 005I | B et ses données toujours présents, application fonctionnelle |

Noter la version, le cas, le résultat entendu, CPU/PSS/thermique affichés dans Avancé, et l’éventuel message sûr. Ces métriques varient avec le téléphone. Ne pas envoyer d’audio, d’archive, de modèle ni de capture contenant des données personnelles.

## Essayer l’assistant sans remplacer B

Créer une **nouvelle** voix de test autorisée : nom/consentement → ajouter un MP3/WAV local d’au moins 5 s → sélectionner une portion contiguë 5–15 s → écouter et vérifier → consentir pour ce fichier → encoder → essayer → sauvegarder si souhaité. B doit rester actif pendant la préparation tant que vous n’avez pas confirmé un autre profil.

Ajouter ensuite un deuxième fichier à **la même identité** : deux références/profils distincts, aucune fusion. Avancé propose A/B entre deux profils, sans gagnant automatique ni changement de la voix principale. Un fichier de 3 ou 4 s doit être refusé tôt avec sa durée et une aide ; 5 s valides doivent être acceptées. Retour arrière ne supprime rien. Ne pas faire chauffer le téléphone à force d’encodages ; les protections restent actives.

Étiquettes CHALEUREUSE/SERIEUSE et contextes d’expression sont des métadonnées manuelles ; aucune émotion acoustique native n’est disponible. Aucun enregistrement micro n’est demandé.

## En cas de problème

Ne pas désinstaller 005I ni supprimer son profil. Conserver le `.jvi` et le ZIP privé. En cas d’erreur de lecture, essayer Réécouter si un PCM complet est disponible ; ne pas relancer inutilement le modèle. Laisser refroidir/libérer de la mémoire si admission refusée. Une archive invalide ou un mauvais mot de passe ne doit pas écraser la voix précédente. Rapporter uniquement le message sûr et le scénario, sans fichier personnel.
