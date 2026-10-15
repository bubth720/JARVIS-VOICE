# Confidentialité et modèle de menace

Tout audio, état vocal, cache PCM, métadonnée et extrait est dans `noBackupFilesDir`.
L'APK n'a ni INTERNET, ni RECORD_AUDIO, ni accès large au stockage ; sauvegarde et
transfert système sont exclus. Pas de crash-reporter ni journalisation d'audio,
URI, nom de document ou mot de passe. Le test Cloud utilise uniquement des
signaux synthétiques et deux plages de la voix publique Alba CC BY 4.0, dont le
SHA256 est épinglé. Aucun fichier personnel n'est demandé ou traité dans Cloud.

Le sélecteur SAF accède seulement au document explicitement choisi. 005H refuse
les URI file:// et les fournisseurs distants/inconnus. Les autorités autorisées
sont les fournisseurs Android de documents du stockage interne, Téléchargements
et médias. Sur un Xiaomi modifié, un fournisseur local différent peut être
refusé : choisir le fournisseur système ; ne pas contourner le contrôle avec un
transfert cloud. L'absence d'INTERNET dans l'APK ne suffirait pas à empêcher un
fournisseur Drive d'envoyer un document, d'où ce contrôle supplémentaire.

Consentement au niveau de l'identité et de chaque référence, même personne
confirmée manuellement, réinitialisation des cases après sélection/import.
Aucun vérificateur fiable de locuteur n'est disponible. NEUTRE/EXPRESSIVE/etc.
sont des annotations humaines, pas une classification ni un contrôle émotionnel.

Les noms, notes et états vocaux sont des données sensibles. L'écran est protégé
par FLAG_SECURE. Les champs de mot de passe n'ont ni sauvegarde de vue, ni
autofill, et demandent l'absence d'apprentissage au clavier. Les tableaux de
caractères, la clé dérivée temporaire et PBEKeySpec sont effacés au mieux ; Java,
les fournisseurs cryptographiques et le clavier peuvent conserver des copies
en mémoire. Aucune promesse d'effacement parfait de la RAM.

La sandbox dépend du verrouillage/chiffrement Android et des autorisations du
système. Cette APK de test est debuggable ; root, un débogueur autorisé ou un
système compromis peuvent lire sa mémoire et ses fichiers. Le chiffrement AEAD
de l'archive protège la copie exportée, pas automatiquement les objets déjà
décryptés dans la sandbox. La suppression de fichiers n'est pas un effacement
physique garanti sur mémoire flash. Éviter un dossier de destination synchronisé.

Suppression confirmée puis index durablement validé avant nettoyage des objets.
Une suppression de référence liée à un profil est refusée ; supprimer seulement
son WAV est possible sans supprimer les profils. Une identité supprimée n'affecte
pas les autres. Le dernier état de notes est dans l'index et les PCM sont privés
et bornés. Les originaux temporaires et les espaces d'archive interrompus sont
nettoyés à la réouverture ; un échec de nettoyage est signalé, pas assimilé à un
effacement réussi. La stabilité Xiaomi, la chauffe et l'audibilité 005H exigent
encore un essai physique.

Les MP3/WAV originaux choisis et les archives .jvi exportées manuellement sont
des copies indépendantes, extérieures à la sandbox. Supprimer une identité dans
005H ne les supprime pas ; les inspecter/effacer séparément dans l'application
Fichiers, et vérifier l'absence de synchronisation de leur dossier.
