# 005E — import privé FP32, version 0.1.1

## Constat et limites

Le Xiaomi signale « Entrée ZIP FP32 inconnue ou dupliquée » après 178 ms.
Ce message réunissait trois branches : répertoire, nom absent du manifeste et
doublon. Il ne permet pas de savoir laquelle a réellement échoué, ni si le
fournisseur Android a ouvert le document vérifié par l'utilisateur.

Le ZIP privé original de 234932686 octets est conservé, sans reconstruction.
SHA256 : `9436555c6965e3480c06f51fedced0f512a8316e482a7e54df0f4ee83d0a01cd`.
Ses dix noms correspondent exactement au manifeste, dans le répertoire central
et les en-têtes locaux. Le manifeste de l'APK locale précédente est identique
au source : SHA256 `4afe48a1616af4ddbe0fe8cb1d71c690477e171b860880790e91fff6777d1fa3`.
Le test d'installation réelle avec **le code précédent** réussit sur JVM.
Le refus Xiaomi n'est donc pas reproduit et sa cause précise reste inconnue.
Cette livraison améliore le diagnostic ; elle ne prétend pas résoudre une cause
Xiaomi démontrée ni valider le clonage.

## Modifications

Le manifeste embarqué est contrôlé par son empreinte avant utilisation. Aucun
nom, taille ou hash de modèle n'est modifié. Le lecteur `ZipInputStream` reste
utilisé avec un tampon de 64 Kio, après vérification de la signature ZIP locale.
Les noms acceptés restent exacts : pas de normalisation permissive, pas de
répertoires, doublons ou traversées. L'extraction reste bornée par fichier,
vérifiée par SHA256, puis activée atomiquement. Tout échec conserve l'ancien pack.

Le sélecteur reste `ACTION_OPEN_DOCUMENT`, `CATEGORY_OPENABLE`, `application/zip`.
Le MIME fourni par Android n'est pas une preuve du contenu : l'en-tête du flux
est vérifié. Le flux effectivement retourné par `ContentResolver` est fermé.
Aucun nom du document sélectionné, URI ou chemin personnel n'est affiché.
Pour une entrée refusée, seul son nom de base ASCII borné est montré, sans ses
répertoires ni caractères de contrôle ; le nom brut reste utilisé pour le refus.
Les messages d'erreur provenant du fournisseur ne sont pas repris.

| Code | Cause précise |
|---|---|
| DOCUMENT_INACCESSIBLE | Le fournisseur ne retourne pas de flux lisible |
| DOCUMENT_NON_ZIP | Le document sélectionné n'a pas la signature ZIP requise |
| NOM_INCONNU | Le nom exact de l'entrée n'est pas dans les dix noms embarqués |
| DOUBLON | Le même nom accepté a déjà été lu |
| REPERTOIRE | L'entrée est un répertoire |
| ZIP_INVALIDE | Structure, compression, CRC ou en-tête tronqué |
| LECTURE | Lecture/écriture interrompue ou stockage indisponible |
| TAILLE | Taille décompressée trop grande ou différente du manifeste |
| MANQUANT | Une ou plusieurs entrées attendues sont absentes |
| SHA256 | Contenu d'un fichier différent de l'empreinte attendue |
| MANIFESTE_EMBARQUE | Manifeste de l'APK absent ou incompatible |
| MANIFESTE_PACK | Identité, FP32, checkpoint, BOS ou fréquence incompatible |

Un ZIP différent ne suffit pas à conclure « mauvais document sélectionné » :
le code rapporte le refus observé, sans deviner les gestes de l'utilisateur.

## Validation et essais Xiaomi

Le test privé `originalPrivateZipInstallsWithEmbeddedManifest` importe réellement
le ZIP original et vérifie les dix fichiers. Il est conditionnel à
`POCKET_EXPERIMENTAL_ZIP`, jamais téléchargé dans une CI publique.
Les tests synthétiques couvrent les dix noms lus en continu, les causes de refus,
les doublons réels dans les en-têtes locaux, la suppression du staging et la
préservation d'une installation précédente. Les tests instrumentés Android
utilisent la bibliothèque JSON de la plateforme, son `ZipInputStream` et le
callback d'import de l'activité. Aucun modèle privé n'est inclus dans les tests
publics ni dans l'APK.

Sur Xiaomi : installer 0.1.1, ouvrir « Importer le pack privé FP32 », sélectionner
le **ZIP complet** dans Téléchargements, pas `.part1/2/3`, puis relever le code
et le nom d'entrée affichés en cas de refus. Rien ne doit être envoyé du pack.
La taille et l'empreinte déjà vérifiées sur le téléphone restent valables : il
n'est pas nécessaire de reconstruire ou retélécharger le modèle.

L'identifiant reste `com.jarvis.voice.voiceprototype005e`, versionCode 2.
005D et ses données ne sont pas modifiés. Une mise à jour Android nécessite la
même clé de signature que l'APK 005E installée. Les builds debug GitHub sur des
runners éphémères ne garantissent pas cette continuité ; en cas de conflit de
signature, ne jamais désinstaller 005D. Aucune suppression de données 005E n'est
effectuée automatiquement.

## Résultats de livraison

Commit vérifié : `0aa253ea37839ffa81cb0654949aa27864c220f7`.
25 tests JVM réussis, 11 tests nécessitant d'autres modèles/fixtures sautés,
zéro échec (36 au total). Les quatre tests d'import ciblés, dont le ZIP privé
original, ne sont pas sautés. Build, lint et contrôles APK réussis.
Six tests instrumentés réussis sur Android 15 x86_64, sans poids privés.
L'ancienne APK **effectivement livrée** (artefact 11925184648) a été téléchargée
et son manifeste est identique au source et au ZIP privé. Aucun essai physique
Xiaomi n'a été effectué dans cette session.

[APK 0.1.1 et rapports](https://github.com/bubth720/JARVIS-VOICE/actions/runs/38639491087/artifacts/11930315013),
accessible jusqu'au 28 octobre 2026. Installer `voicePrototype-debug.apk`, pas
l'APK `androidTest`. SHA256 de l'APK principale :
`63102c11cf56d99d213e3ab0d73ca94abde289b917dce25c021fafdc1010fe10`.

La signature CI précédente et celle-ci sont effectivement différentes.
La mise à jour directe de l'ancienne APK CI est donc impossible : essayer cette
APK exige une réinstallation **005E seulement**, avec perte des données locales
005E. Le ZIP dans Téléchargements et l'application 005D restent indépendants.
L'ancienne clé CI n'est pas disponible pour signer une mise à jour compatible.
