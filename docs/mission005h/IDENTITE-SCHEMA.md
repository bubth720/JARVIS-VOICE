# JARVIS VOICE IDENTITY — schéma 1

Le schéma JSON logique est distinct du binaire JVFP1 inchangé.

| Objet | Champs persistés |
|---|---|
| Index | schema=1, activeIdentity nullable, identities |
| Identité | UUID aléatoire, nom local 1–80 caractères, createdMs, consentConfirmed, consentNote 1–500 caractères, preferredNeutralReferenceId nullable, activeProfiles par engineId:packId, références, profils |
| Référence | UUID, identityId, sourceFormat MP3/WAV, sourceSha256, sourceDurationMs, startMs, durationMs, label manuel, commentaire, consentement et confirmation même personne, date, keepAfterEncoding, analyse énergétique, bytes et SHA256 nullable du WAV |
| Profil | UUID, identityId, referenceId, engineId, packId, SHA256 du JVFP1, conditioningSha256 canonique, taille, statut VALID/DISABLED/INCOMPATIBLE, date, protocole, provenance, évaluation humaine nullable |

Les champs de premier niveau, d'identité, de référence et de profil sont fermés.
UUID canoniques uniques, liens d'identité/référence, tailles et sélection sont
contrôlés. Les champs réservés futurs demandent une migration explicite du schéma.
Les noms/commentaires ne sont jamais des noms de fichiers. Aucun chemin/URI ni
nom original personnel n'est conservé. Le hash de source permet de vérifier la
provenance locale, sans donner accès au fichier original.

Une référence sans WAV garde sa plage, analyse, consentement et provenance.
Une proposition de 5 s ajoutée depuis un WAV existant est une nouvelle référence,
avec SHA du WAV source et commentaire liant l'ancien UUID ; elle ne remplace pas
l'ancienne. Il s'agit d'une proposition d'énergie, pas d'une détection automatique
de parole ou de locuteur.

engineId actuel : `pocket-tts-french-6l-fp32-v1`. packId inchangé :
`3470a01f4784b528da7899042af757de14f1f4f8017cb8789587465d39199570`.
Le profil garde exactement les contraintes JVFP1 : 18 états, offsets 64–189,
pad nul, caches finis, Mimi/BOS officiel, extrait 5–15 s. Le sampler n'intervient
pas dans l'encodage ; le protocole le précise. Les réglages LSD sont ceux de la
synthèse et des preuves d'écoute, jamais une moyenne d'états.

Une évaluation contient cinq notes humaines 0–5, heardOnDevice=true, date et,
dans l'application, le texte/graine/réglages/PCM effectivement écouté. Aucune
évaluation n'est fabriquée lors d'une analyse RMS ou d'un appel ASR. La dernière
évaluation de chaque profil est conservée ; elle ne vaut pas preuve universelle
de fidélité ni consentement d'une autre personne.

L'identité logique est préservée lors d'une restauration réussie. Un UUID déjà
présent est refusé sans écrasement. Les packs/moteurs incompatibles sont refusés ;
leur portabilité exige un encodeur et une validation indépendants, pas la
réinterprétation des dix-huit caches.
