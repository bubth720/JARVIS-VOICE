# 005K — contrat local proposé et adaptateurs de test

`voiceBridgeContract` : Kotlin/JVM petit, JSON borné, aucun Android/service/permission ni dépendance moteur. `voiceBridgeHarness` : adaptateur JVM vers le DTO 005I et harness de mesure explicite, jamais dépendance des anciennes apps. Aucun endpoint ni E2E CORE réel. Version locale **005k.1-proposed** ; aucun schéma UNIFIED modifié. Copie identique des cinq schémas proposés au SHA de SOURCES.json, empreintes vérifiées au chargement ; leur acceptation métier n’est pas une approbation d’IPC.

## Types privés, états et limites

VoiceCapabilityQuery/Reply ne révèlent un binding qu’après autorisation, consentement, confirmation et expiration contrôlés. Par défaut, module absent/autorisation absente. Aucun catalogue universel de profils. SpeechJobRequest conserve texte exact, session/corrélation/annulation UUID canoniques, échéance **locale**, binding identité/profil/SHA/conditionnement/engine/modèle/pack et paramètres explicites. Les toString privés sont expurgés, erreurs enum fermées sans texte, URI, fichier ou exception originale. Pas de chemin ni de tenseur dans les messages.

Texte : même plafond 300 unités UTF-16 que 005I (emoji compte pour deux), Unicode bien formé, <=1200 octets UTF8 et minimum demandé, aucun contrôle sauf retour ligne ni jeton réservé. Aucune troncature, commande exécutée ou modification de texte. 50 tokens frontend et 20 s PCM restent contrôlés par le moteur/orchestrateur inchangé. Paramètres LSD1/2/3/5, température .1–.6, graine exacte ; pauses explicites 0/250/500/1000/1500 ms, 2–3 phrases complètes une par ligne. Priorité0–3 sans préemption ; pas de réglage automatique de qualité. Intentions connues seulement, non NEUTRAL = effet acoustique indisponible.

JSON : syntaxe RFC, clés dupliquées même échappées refusées avant org.json, pas de single quotes/comments/trailing data, UTF8<=32768 octets, profondeur<=12, tableaux/objets bornés ; refus des entiers sous forme booléen/flottant. Validateur des seuls mots-clés utilisés par ces cinq schémas, pas une bibliothèque JSONSchema générale. Mots-clés inconnus échouent. Dates UTC Z, UUID canoniques. Sous-ensemble plus strict et règles sémantiques ajoutées localement, sans réécriture des contrats sources.

SpeechJobResult sépare génération de playback NOT_STARTED/STARTED/COMPLETED/CANCELLED/UNKNOWN. Référence PCM opaque gardée dans VOICE ; humanAudibilityVerified vaut toujours false : une fin AudioTrack n’est pas une perception humaine. Aucune promesse d’émotion, de streaming ou de latence garantie.

## Mapping des cinq enveloppes proposées

| Enveloppe UNIFIED v1 | Traitement 005K | Lacunes ou limites explicites |
|---|---|---|
| capability-manifest | Schéma fermé, speech.synthesize1.0.0 disponible/qualifiée, engine/modèle/JVFP1 exacts, Android min26/ABI | Ne porte pas le pack/profil exact ni les pauses. Binding détaillé uniquement via contexte privé confirmé. available ne vaut ni autorisation ni admission matérielle |
| request-envelope | origin CORE/destination VOICE/capacité speech ; session/auth/content/corrélation/annulation liés au grant, résolus par AuthorizationPort **de confiance** ; profil UUID, modèle SHA et pack digest comparés au grant | Les chaînes d’origine et authorization_ref ne sont pas une authentification. Résolution du texte/pauses et transport privé à approuver. Le grant ne se désérialise pas depuis l’appelant |
| response-envelope | Corrélation + session courante conservée localement + deadline, provenance VOICE/engine/modèle ; résultat OU erreur | Pas de session/profil/pack dans cette enveloppe : rattachement au pending local obligatoire, association cryptographique future à concevoir. success signifie **généré seulement**, pas lu. Fixture refusée par défaut, acceptée seulement par option explicite de tests ; unverified refusé |
| resource-lease | Schéma/règles stop-fin-release, owner/operation/generation/class stricts | Pas de lien direct dans request-envelope, ni heartbeat/échéance de bail ni double preuve heavy/résidence ; association hors enveloppe documentée, aucune extension cachée. process/interprocess_target ne prouvent pas la présence d’un arbitre ; reserves_android_ram=false |
| health-status | État/code fermés et cohérents, diagnostic sans détails privés | available/OK ne suffit pas pour 2 Gio/senseurs/LLM libéré. Fraîcheur/transport authentifié à qualifier. unknown jamais admis |

`pack_sha256` signifie dans **cette proposition locale** le packId existant calculé sur le manifeste/verrous, pas le hash du ZIP original et pas celui d’un unique fichier ONNX. `model_sha256` est le checkpoint officiel français exact. Les trois empreintes sont distinctes : les confondre refuse la requête. Cette sémantique doit être acceptée/versionnée avant futur échange réel. `limits.max_output_tokens` doit valoir **0 pour un résultat audio** ; ce champ n’est pas détourné en budget d’entrée. Le plafond local frontend50 reste autonome. CORE doit refuser/faire choisir un extrait trop long, pas couper la réponse en secret.

Horloges : échéance sender_monotonic jamais comparée directement à nanoTime local. Le futur transport authentifié fournit un budget restant borné <=TTL/timeout<=90000 ms ; le récepteur calcule sa deadline locale, limitée par l’expiration du grant. Aucun budget confiance par simple champ auto-déclaré de payload. Réponse périmée ou ancienne session ignorée/refusée, jamais jouée sur une autre session. Métadonnées source_commit en runtime requises ; elles ne remplacent pas la vérification du signataire/UID.

## Réservation de test

SpeechJobCoordinator est **une simulation dans un seul processus** : UNKNOWN/UNAVAILABLE/READY/GENERATING/GENERATED/PLAYING/CANCEL_REQUESTED/NATIVE_DONE/UNLOADED/REFUSED. Pas de RAM réservée, pas de protection d’un autre processus, pas de broker. Admission exige dans la fixture >=2 Gio, aucun lowMemory, thermique connue0–2, autre modèle connu libéré et API26+. API26–28 ne fournit pas l’API thermique29 : politique de repli senseurs à qualifier, jamais inventer un statut0.

Un slot lourd unique ; aucun second job après annulation, timeout, décès ou nettoyage inconnu. Cancellation répétée idempotente ; preuve nativeDone et audioStopped puis modelResident=false nécessaires avant confirmUnloaded. Ancienne génération ne peut libérer la nouvelle. Tombstones bornées128 jusqu’à deadline contre rejeu du même couple session/corrélation ; saturation refuse, sans éviction d’une opération encore dans sa fenêtre. Le résolveur d’autorisation futur doit aussi gérer révocation et rejeu entre processus/redémarrages : rien de cela n’est prouvé par les tests locaux.

FallbackPolicy exige demande humaine explicite, VOICE certainement arrêtée et TTS local offline prêt. Timeout seul → WAIT_FOR_STOP ; TTS indisponible → refus, jamais retry/voix simultanée. Aucun appel système ou commande de mémoire contenu dans le texte ne peut passer par le port renderer.

## Tests et implémentation suivante

Tests de syntaxe/schémas/Unicode/autorisation/identité/API/temps/provenance/résidence/annulation/concurrence/rejeu/repli, mocks distincts du harness public réel. AdapterTests vérifie l’égalité DTO 005I et le port désactivé, pas Binder. Les futurs ports Android devront authentifier avant mapping, lier autorité réelle, pack/profil exact et arbitre global. Contrat compilé != CORE raccordé, ni permission accordée, ni profil B accessible.
