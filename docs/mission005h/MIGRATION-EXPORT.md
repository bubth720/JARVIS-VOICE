# Migration et archivage manuel chiffré

## Aucun changement des applications précédentes

005H ne peut pas lire la sandbox 005G, même si son certificat est identique.
Elle ne modifie pas ses données. Le parcours prévu ne demande ni PC, ni Termux,
ni ADB : importer le ZIP FP32 déjà dans Téléchargements et créer les profils
005H depuis les MP3 consentis présents sur le Xiaomi. Garder 005G installée.
Pas de transfert Cloud ni nouvel export du modèle. Le format JVFP1 et son packId
restent inchangés ; la nouvelle archive ajoute la bibliothèque logique autour.

## JVIENC01

L'export concerne une seule identité et tous ses profils. Les WAV encore présents
sont optionnels et doivent être explicitement cochés. Le modèle n'est jamais
inclus. Archive intérieure ZIP : manifest.json en premier, NOTICES.txt et les
objets profiles/UUID.bin et references/UUID.wav strictement déclarés. Noms fermés,
aucun dossier ni doublon, limites de taille et nombre d'objets, contrôle des
répertoires central/local et SHA256 des objets. Une erreur refuse tout l'import.

Enveloppe binaire : huit octets ASCII JVIENC01, entier big-endian 600000,
sel aléatoire 16 octets, nonce aléatoire 12 octets, puis ZIP chiffré et tag GCM
16 octets. Le header entier de 40 octets est AAD. AES-256-GCM ; clé dérivée par
PBKDF2-HMAC-SHA256, 600000 itérations fixes, mot de passe de 12–128 caractères.
Les coûts différents sont refusés avant dérivation, pour éviter un coût imposé
par une entrée hostile. Argon2id n'a pas été ajouté faute de dépendance Android
déjà validée ; PBKDF2 utilise les fournisseurs standards Java/Android. Sel et nonce
SecureRandom frais à chaque export, y compris du même contenu/mot de passe.

Le ZIP clair et le chiffré complet sont préparés dans l'espace privé ; seuls les
octets chiffrés sont écrits dans le document SAF local choisi. À l'import, au
maximum 256 Mio chiffrés sont copiés en privé. L'authentification GCM complète
précède l'analyse du ZIP, toute extraction et toute mutation du coffre. Les noms
ZIP ne sont jamais résolus comme chemins de destination : staging privé à UUID,
puis contrôle du manifeste fermé, SHA, format JVFP1, WAV, quotas et liaisons.
La taille décompressée cumulée est bornée à 256 Mio, celle de chaque entrée à la
taille déclarée autorisée ; un manifeste est limité à 1 Mio / profondeur 32.

Un mauvais mot de passe, tag altéré, profil corrompu, pack différent ou UUID déjà
présent laisse le coffre précédent inchangé. La restauration d'un UUID existant
n'est jamais un remplacement. Tester d'abord dans une installation 005H vierge
ou avec une identité synthétique distincte ; ne pas supprimer sa seule identité
personnelle pour essayer une sauvegarde non encore vérifiée sur appareil.

## Coûts et limites

Les dérivations PBKDF2 et doFinal GCM peuvent n'être annulables qu'à leur retour.
Selon le fournisseur, le déchiffrement authentifié peut tamponner jusqu'à la
limite de 256 Mio en RAM. Les archives sont traitées sans modèle ONNX chargé,
avec la garde RAM 2 Gio et les gardes thermiques conservées. L'espace disque
nécessaire comprend le chiffré, le ZIP clair, les objets de staging et leurs
copies finales. Un échec de stockage/transaction ne supprime pas l'ancien coffre.
Ces contraintes sont distinctes du PSS du moteur et doivent être mesurées sur le
Xiaomi. Il n'y a pas de service distant de récupération du mot de passe.

Les tests JVM et Android doivent vérifier restauration, mauvais mot de passe,
altération, refus des noms malicieux, absence d'écrasement, annulation et lecture
d'un fixture JVM sur le fournisseur Android. Il s'agit de tests et d'une revue
de code, pas d'un audit cryptographique indépendant. Aucun ZIP clair de données
vocales n'est exporté et aucun format de secours non chiffré n'est disponible.
