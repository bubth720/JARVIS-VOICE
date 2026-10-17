# voice.bridge.fixture/1

Extension expérimentale de transport, distincte de005k.1-proposed et des schémas UNIFIED inchangés. Périmètre V1 : authentification et refus sûrs, aucune génération native, aucun profil/PCM/fichier exporté. Cette extension n'est pas un contrat de production approuvé.

Endpoint explicite : com.jarvis.voice.bridgepilot/com.jarvis.voice.bridgepilot.VoicePilotService.
Client de laboratoire : com.jarvis.core.voicepilot.fixture ; aucun enrôlement du CORE historique.
Permission com.jarvis.voice.bridgepilot.permission.BIND, signature ; même certificat dédié de laboratoire requis, UID unique/package exact, certificat unique courant sans rotation, installation/version inchangées. Les certificats distincts sont refusés ; appairage inter-signataires futur non activé.

AIDL IVoicePilotFixture : hello(byte[], IBinder), request(byte[]), status(byte[]), cancel(byte[]). Pas de méthode publique d'activation, de grant, d'importation ou de preuve native. Chaque transaction authentifie le véritable Binder.getCallingUid avant désérialisation. Autorisation locale visible révocable de60s, perdue sur pause/décès/restart. Aucun service permanent ou réseau/microphone.

Payloads JSON stricts UTF8 <=4096octets ; objet plat de chaînes uniquement, clés inconnues/manquantes/dupliquées, JSON malformé, Unicode invalide refusés. version=voice.bridge.fixture/1 ; contract_sha256=SHA256 des fichiers CONTRACT.md et IVoicePilotFixture.aidl, triés par nom : nom UTF8,NUL,octets exacts,NUL.

hello champs : version,contract_sha256,session_id. UUID canonique minuscule. Réponse : version,contract_sha256,session_id,voice_epoch,grant_ref,expires_elapsed_ms,scope,native_generation_available,global_admission_available. scope=AUTHENTICATED_FIXTURE_ONLY ; les deux disponibilités=false. hello ne prouve aucune fin native. Une seule session par activation locale.

request champs : version,contract_sha256,session_id,correlation_id,cancellation_id,grant_ref,core_epoch,lease_id,generation,text,remaining_ms. Tous les identifiants UUID canoniques ; génération décimale1..9007199254740991 ; remaining_ms1..90000 borné par l'expiration locale du grant. text exact max300UTF16/1200UTF8, Unicode valide, pas de contrôles saufLF, pas de tokens réservés unk/s/pad. Les références client de bail/epoch sont NON FIABLES ; aucune autorité ne dérive de leur présence. Un grant ne remplace jamais UID/certificat/consentement. Aucun champ profil, chemin, URI, archive, tenseur, audio ou booléen resourceFree admis.

Le grant est local, aléatoire, éphémère, lié à l'identité réelle et à une session, consommable une seule fois ; pas de réémission sur simple répétition de hello après consommation. Rejeu et saturation refusés. request valide retourne uniquement phase=REFUSED et code=NATIVE_AUTHORITY_UNAVAILABLE : aucun fournisseur CORE attesté n'existe dans V1. Une demande refusée ne charge aucun moteur et ne joue aucun audio.

status/cancel utilisent exactement les champs request sans text/remaining_ms. Réponse reprend cette clé complète et ajoute voice_epoch,phase,code. L'ancienne clé/session/génération n'est jamais rapprochée d'une nouvelle opération. cancel est idempotent sur cette requête refusée, sans prétendre arrêter un natif inexistant.

États futurs GENERATING/GENERATED/PLAYING/PLAYBACK_COMPLETED/CANCELLED/UNKNOWN : non émis par V1. Timeout/décès/annulation ne prouvent jamais le déchargement. V2/V3 exigent contrat de profil et arbitre CORE authentifié, preuve de libération LLM/Whisper et validation Android entre APK avant raccordement.
