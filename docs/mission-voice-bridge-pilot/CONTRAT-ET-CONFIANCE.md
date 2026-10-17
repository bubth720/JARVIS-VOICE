# Contrat et frontière de confiance V1

Voir `voiceBridgePilot/contract/CONTRACT.md` et `PIN.json` pour les champs exacts. Extension fixture distincte, pas une mutation silencieuse005K/UNIFIED. Contrat identique dans la branche CORE sœur, sous docs uniquement tant que C3 est arrêtée.

## Autorisation réelle implémentée, preuve Android encore à exécuter

Le service est exporté uniquement pour Binder, protégé par `com.jarvis.voice.bridgepilot.permission.BIND` de niveau signature. Le contrôle d'entrée lit `Binder.getCallingUid()` avant désérialisation et sans clearCallingIdentity ; il exige package unique attendu, UID réel de ce package, certificat courant unique égal au certificat dédié du pilote, sans rotation. API26/27 utilisent le certificat unique GET_SIGNATURES ; API28+SigningInfo refuse plusieurs signataires et historique de rotation.

La permission ne suffit pas : le même certificat avec un autre package est refusé par l'application. Le nom du package ou un nonce envoyé dans le JSON ne suffit pas. Un certificat distinct est refusé ; l'appairage inter-signataires sur appareil n'est **pas implémenté**, aucune prétendue permission signature n'est accordée à des clés différentes.

L'Activity locale affiche le package et son SHA public, puis l'utilisateur confirme l'enrôlement de la seule fixture autorisée. La confirmation relit l'identité avant armement. Autorité60s au plus, liée à UID/package/certificat/installation/update ; une réinstallation ou mise à jour invalide l'ancien grant. Pause, révocation, unbind, décès du client et destruction du service retirent l'autorisation ; redémarrage=aucun consentement. Les données de grant restent volatiles. Le binder de liveness est enregistré après authentification.

Une session par armement et un grant aléatoire consommable une fois, borné par horloge monotone locale. Une répétition de hello après consommation ne réarme pas. Les identifiants session/corrélation/annulation/epoch/bail/génération sont comparés exactement. Les références de bail sont **non fiables** et n'accordent aucun accès natif. Le code ne persiste ni texte ni profil ; aucun fichier/tensor/PCM n'est exporté. Exceptions/logs ne contiennent pas le texte.

JSON plat <=4096octets, Parcel <=8192 avant AIDL, UTF8 strict, clés fermées, valeurs chaînes, duplicata y compris clés échappées et Unicode invalide refusés. Texte exact300UTF16/1200UTF8 ; conformité au validateur005K testée sans modifier ce dernier. Aucun profil, URI, audio, ancien résultat ni champ inconnu accepté.

## Signature et déploiement de laboratoire

Build par défaut **non signé** si `VOICE_PILOT_KEYSTORE` absent. Tests locaux construits avec deux clés éphémères créées hors Git, indépendantes du certificat005J. Alias `voice-pilot`, certificat commun endpoint/authorized/wrongPackage ; wrongSignature distinct. Les empreintes publiques des APK audités figurent dans APK-AUDIT.json, aucune clé privée n'est livrée. Ces clés de test ne sont pas une identité durable à installer sur appareil personnel.

Une future paire physique requiert une clé dédiée durable provisionnée par canal privé ou un vrai protocole d'appairage distinct explicitement validé. Ne pas changer la signature005J ou CORE, ni transférer B vers le cloud. Le pilote n'a aucun accès à la sandbox005J. Importer B serait une mission locale future avec consentement supplémentaire, jamais une conséquence de cet endpoint.

## Limites

Robolectric simule PackageManager/Binder UID : il ne prouve pas l'application de la permission signature par le noyau. Un audit de deux APK signés ne prouve pas un bind accepté/refusé à l'exécution. Pas d'arbitre global vivant, de génération, de mémoire disponible garantie ou de preuve de déchargement dans V1. V2/V3 interdites tant que CORE ne fournit pas les attestations nécessaires.
