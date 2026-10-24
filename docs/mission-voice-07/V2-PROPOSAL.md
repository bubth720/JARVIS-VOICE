# Proposition V2 publique — NON RACCORDÉE, NON ENREGISTRÉE UNIFIED

Cette proposition locale `voice.speech.public-lab/2-draft` ne change **aucun octet** de la wire `voice.bridge.fixture/1`, AIDL, PIN ou permissions. Elle n'a ni codecJSON de transport ni nouvelle transaction. Aucune application installée ne sait la recevoir. Toute implémentation d'un nouvel endpoint requiert une revue/validation distincte du diff et des conditions natives.

Requête immuable : `version`, `key{requestId UUID canonique,sessionId UUID canonique,generation1..2^53−1}`, `fixture HELLO|TRAIN`, `style NEUTRAL|SOFTWARE_EXPERIMENT_UNAPPROVED`. Le texte vient de deux constantes publiques françaises<=300UTF16/1200UTF8. **Pas de texte libre, URI, fichier, profil, empreinte vocale, audio ou face.** Déclarer public=true ne peut pas déclassifier une conversation privée.

Réponse corrélée : même clé, modeSPEECH_BLOCKED, phaseREFUSED/CANCELLED_LOGICAL_ONLY, codeNATIVE_AUTHORITY_UNAVAILABLE/STYLE_UNSUPPORTED, libération **logique** du refus=true, observation **nativeUNKNOWN**, cuesPCM vides. Aucun FIRST_PCM/FIRST_WRITE/PLAYING/RELEASED natif fictif. TEXT_ONLY est l'état initial client ; TTS_LOCAL_AVAILABLE n'est jamais annoncé par ce gateway bloqué.

Le helper non raccordé convertit seulement cette proposition mémoire vers les champs **V1 existants de fixture**, avec validation complète par PilotWire puis PilotAuthorization. Il exige l'authentification avant ses contrôles, résout le Principal à nouveau par appel via le PilotIdentity/BinderUID existant dans sa factory non appelée, conserve les refus de permissions/signatures/installation/grant/session/délai/rejeu. Le payload n'apporte ni UID ni certificat.

Ajouts : anti-réutilisation128IDs/60s, génération monotone par session bornée128, statut/annulation corrélés et idempotents. Ces références anti-rejeu ne deviennent jamais des baux JNI. Le client n'a qu'une requête logique en vol ; disconnect invalide une réponse tardive sans bloquer sur le transport ni prétendre avoir arrêté un moteur.

Le tracker natif ne possède **aucune méthode** permettant de confirmer un arrêt/déchargement depuis un booléen de requête, un timeout ou un événement métier. Il resteUNKNOWN. Toute demande authentifiée finit donc refusée, sans import de voix, modèle, AudioTrack ou moteur. Le moduleclient séparé ne dépend pas d'Android et n'établit aucune liaison Binder par lui-même.
