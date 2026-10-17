# Tests Android — reprise, preuves et limites

## Code soumis à qualification

V1 commun242eb65, reprise370aa1d puis63838b2. Les dix-sept tests Robolectric API35 du pilote passent dans ce laboratoire après provision d'un SDK public vérifiéSHA256 en cacheworkspace (optionpilotRobolectricDir). Ce sont des simulations Android JVM ; aucun UID simulé n'est présenté comme preuve Binder. Les119 tests JVM historiques ont aussi été réexécutés :103 réussis,16 ignorés,0 échec. Le code historique n'a pas changé.

Les APK serveur, trois clients authorized/wrongPackage/wrongSignature et leurs trois APK d'instrumentation sont construits et audités. Signataires dédiés éphémères, distincts des clés de livraison historiques. Lint serveur :0 erreur,1 avertissement d'icône non définie. Les hashes/certificats publics sont dans l'audit APK actualisé ; aucune clé privée ni modèle n'est livré.

## Neuf scénarios Binder réels par variante

Instrumentation dans les APK clientes, UID distinct du serveur, aucun remplacement de l'Application/du résolveur d'identité. Consentement par les vrais contrôles locaux : boutons à identifiants fixes et dialogue certificat, sans API d'appairage distante.

1. Permission signature accordée/refusée effectivement par Android.
2. Refus appelant non autorisé avant payload malformé.
3. Demande publique corrélée, refus natif, anti-rejeu et révocation.
4. Parcel trop grand refusé avant désérialisation.
5. UTF8 invalide, doublon échappé, champ profil/URI interdit et mauvais hash.
6. Mauvais grant, génération/résultat périmés, cancel idempotent sans faux nativeStopped.
7. Expiration serveur réelle60s, sans horloge injectée.
8. Retour arrière-plan révoquant les appels Binder.
9. Décès réel d'un Binder porté par un second processus client : PID différent, kill de ce seul processus de fixture, réception kernel de mort, grant révoqué.

Le LifetimeService du client est **non exporté**, sous le même UID et dans son processus:lifetime. Sa seule transaction protégée par UID retourne son PID de test ; pas de contenu vocal ni d'autorité native. Le processus du runner n'est pas tué. Les deux variantes adverses exécutent les2 tests de refus applicables et ignorent explicitement les7 parcours réservés au client autorisé. Attendu par API :13 réussis,14 ignorés sur27 déclarés, jamais27 succès.

Réinstallation et rotation de signature : comparaison de principaux simulés et contrôle statique/PackageManager ; pas encore de réinstallation/rotation sous grant actif qualifiée sur le noyau Android. Ne pas annoncer une couverture Android complète de ces cas. Un grant/liveness de fixture ne prouve aucune fin native, ni la propriété du Binder arbitraire transmis par un client.

## Laboratoire local et échecs de mise au point

Émulateur35 AOSP x86_64 installé souswork/, sans/dev/kvm ; logiciel uniquement. HOME n'a pas été remplacé et aucun répertoire personnel n'a été modifié. L'outil SDK adb exige un répertoire fixe en lecture seule ; adb-shell0.4.4 parle directement au port local5555 de cet émulateur. Le script vérifie qemu et boot_completed avant installation, jamais le Xiaomi.

Première exécution :9 erreurs de setup, avant les appels Binder. L'inspection réelle de l'arbre UI montre une alerte «System UI isn't responding» du laboratoire logiciel, puis après attente les boutons «RÉVOQUER»/«AUTORISER» transformés en majuscules. Les sélecteurs de texte ont été remplacés par des identifiants de ressources ; le test réveille et déverrouille uniquement l'émulateur de fixture. Aucun délai de grant, contrôle d'identité, seuil ou assertion de sécurité abaissé. Une erreur d’argument du client Python streaming a aussi été corrigée ; elle n’a exécuté aucun test. La première CI63838b2 a démarré les deux émulateursKVM mais a arrêté le script avant les tests faute de commande rg sur les runners ; le contrôle qemu utilise maintenant une comparaison Bash native, avec le même refus strict.

Après la correction UI, une passe locale a réellement réussi8 des9 scénarios (dont Binder death et expiry), avec1 erreur de setup initial. Elle reste comptée en échec ; la passe complète suivante réussit13 cas et en ignore14 (9+2+2 succès, 7+7 sauts par variante adverse), sans échec. Les journaux et comptes exacts sont conservés sous proofs/LOCAL-IPC-API35.json. Les exécutions locales perturbées par SystemUI ne valent pas succès. Les résultats CI accélérés par KVM surAPI26 et35 confirment maintenant13 succès/14 sauts/0 échec chacun (run39060156787). Les preuves instrumentées sont dans proofs/CI-REPRISE.json ; une compilation seule ne suffirait pas.

## Commandes de reproduction

Générer des clés de fixture hors Git (aliasvoice-pilot), fournirVOICE_PILOT_KEYSTORE, VOICE_PILOT_REFUSED_KEYSTORE, VOICE_PILOT_KEY_PASSWORD. Ne pas réutiliser les clés005J.

```sh
./gradlew -PincludeVoicePilot=true :voiceBridgePilot:testDebugUnitTest :voiceBridgePilot:assembleDebug :voiceBridgePilot:lintDebug --no-daemon --max-workers=2
python scripts/bridge/verify_pilot_scope.py --core-root ../JAVIS-Local-v2
python scripts/bridge/audit_pilot_apks.py --build-tools "$ANDROID_HOME/build-tools/35.0.0"
# Émulateur explicitement choisi ; pas de connectedAndroidTest visant tous les appareils.
ANDROID_SERIAL=emulator-5554 bash scripts/bridge/test_pilot_android.sh
# Alternative locale si le SDK adb ne peut écrire sa configuration :
work/venv/bin/python scripts/bridge/test_pilot_direct_adb.py
```

Le script contrôle les codes instrumentation et les comptes exacts ; am instrument peut retourner zéro au shell malgré des échecs. La CI ajouteAPI26 et35 sans téléchargement de poids. Les APK de fixture ne sont publiés par ce workflow qu'après succès des assertions, Lint et audit.

## Limites

Binder fixture ≠ E2E moteur FP32 public ≠ XiaomiB. NativeDone/unload global, mémoire/thermique modèle, focus/foreground audio35, PCM bit-identique005J et audibilité :non exécutés ; V2/V3 stoppées par CORE C2. Les suites J24/24,I31/31,H18/18,G14/14,F10/10 ont été réexécutées avec succès, D4/5 conserve son échec connu ; détails et runs dans RESULTATS.md. Pas de baisse de budgets ni de modèle résident.
