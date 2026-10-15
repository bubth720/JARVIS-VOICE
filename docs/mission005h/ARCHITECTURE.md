# Mission 005H — architecture de l'identité vocale

## P0 : isoler la bibliothèque des applications validées

`voiceIdentity` est une application distincte : `com.jarvis.voice.voiceidentity005h`,
versionCode 1, Android 26–35, ARM64. `identityRuntime` est une bibliothèque Kotlin
sans écran Android. Aucun changement dans `voiceQuality`, `voicePrototype`,
`pocketProbe`, `app` ou les sources de `pocketRuntime`. Le CI vérifie ce diff
contre le commit de référence 005G `9429a9e`.

Réutilisation en lecture : `FP32VoiceEncoder` / `CandidateVoiceEncoder`,
`PersonalVoiceCodec`, `ExperimentalModelPack`, `QualityEngine`, `FrenchFrontend`,
`ReferenceSelection`, `PrivatePcmStore` et `QualityABStore`. Le lecteur STATIC,
le décodeur MP3 corrigé, les compteurs et protections 005G sont copiés dans le
nouveau module, avec changement de package seulement. `PersonalVoiceRepository`
et son profil unique ne sont jamais appelés ou modifiés par 005H.

## Coffre et transactions

`VoiceIdentityVault` possède un index fermé version 1, sous `no_backup/identity-vault`,
et des objets immuables nommés par UUID. Un verrou de processus et un verrou de
fichier sérialisent lecture/modification entre instances. Chaque mutation écrit
les nouveaux objets, synchronise leurs données et le répertoire, valide le schéma,
écrit/synchronise l'index temporaire, effectue un renommage atomique puis synchronise
son répertoire. Les anciens objets ne sont supprimés qu'ensuite. Les objets
orphelins d'une interruption sont nettoyés lors de la réouverture. Un échec de
synchronisation empêche le nettoyage des générations dont la durabilité n'est
pas établie. Pas de repli vers une écriture non atomique.

Un index inconnu/corrompu est refusé sans réinitialisation. Les SHA256 sont
vérifiés au chargement d'un profil, à la lecture d'une référence et dans les
archives ; le simple affichage de la liste ne recalcule pas tous les hashes.
`VALID` décrit la compatibilité déclarée, pas une preuve perceptive. UUID et
extensions sont contrôlés avant résolution ; les liens symboliques d'objets
sont refusés. Les erreurs ne doivent pas déclencher une désinstallation.

Limites : dix identités, vingt références et vingt profils par identité,
512 Mio d'objets référencés au total, index 1 Mio, profil JVFP1 10 Mo maximum.
Un original choisi est limité à 32 Mio / 3 minutes ; seul un segment contigu de
5–15 secondes est décodé et conservé. WAV PCM16/float32 mono/stéréo à 8–192 kHz,
ou MP3 via le décodeur Android validé ; resampling mono 24 kHz existant, sans gain.
Le MP3/WAV original temporaire est supprimé, y compris par récupération au
redémarrage après une interruption. L'extrait est conservé jusqu'à l'encodage ;
par défaut il est supprimé après création réussie du profil. Une case explicite
permet de le garder pour un futur réencodage local. Métadonnées et profils restent.

## Liaison backend et comparaison

Une identité UUID ne contient aucun cache vocal. `VoiceBackendIdentityBinding`
exige l'engineId Pocket français 6L et le packId exact ; toute incompatibilité
refuse le chargement et demande un réencodage local depuis une source autorisée.
Chaque profil provient d'un seul extrait officiel encodé, jamais de moyenne ou
fusion d'états. Activer, désactiver et choisir le neutre principal sont explicites.
Supprimer le profil actif laisse l'identité sans profil actif ; aucun remplacement
automatique, y compris par Alba. Les annotations expressives ne pilotent pas le moteur.

A/B compare deux conditionnements de la même identité à texte, graine, LSD,
température, EOS, queue et sortie PCM16 identiques. Une empreinte canonique des
18 tenseurs distingue le conditionnement de l'ordre de sérialisation JVFP1.
Deux fichiers différents contenant les mêmes tenseurs ne constituent pas un A/B
de profils distincts. Les caches PCM et leurs métadonnées sont liés par SHA256 ;
un écrit partiel est refusé. Une réécoute exige la même identité explicitement
sélectionnée et un profil existant/valide. Deux caches A/B de 20 s et un cache de
synthèse active de 20 s maximum, tous privés, aucune reconstruction à la réécoute.

Un exécuteur unique pour tout le processus survit aux recréations d'activité :
l'ancien appel natif annulé doit fermer son modèle avant qu'une nouvelle opération
commence. Les poids ne sont jamais chargés par l'écran de bibliothèque ou les
archives. Un thread ORT, 2 Gio disponibles requis avant les opérations lourdes,
protection thermique sévère et suivi mémoire identiques à 005G. Les appels natifs
ne sont pas tous interruptibles instantanément ; le bouton Arrêt coupe l'audio
et annule le calcul aux frontières prises en charge.

## P1/P2

L'archive chiffrée est décrite dans [MIGRATION-EXPORT.md](MIGRATION-EXPORT.md).
`VoicePersonaId` est seulement un contrat pour un identifiant appartenant au CORE,
sans écrire dans CORE. `VoiceExpressionRequest` et `ExpressionCapabilities`
déclarent les champs non pris en charge. Pocket 6L ne déclare aucun contrôle
émotionnel, vitesse ou pause. Aucun moteur 24L/Chatterbox, réseau ou routage
automatique n'est intégré.
