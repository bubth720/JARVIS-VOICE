# Fiche future CORE — pilote de sortie vocale opt-in

**Proposition à approuver, non lancée.** Dépôt `bubth720/JAVIS-Local-v2`, base auditée main226e2e8 ; revérifier le HEAD réel avant toute mission. Branche dédiée proposée `feat/core-voice-pilot`. Aucun changement dans CORE au titre de005K.

Prérequis : accord explicite sur DECISIONS-IPC.md (transport, confiance/signatures, déploiement distinct, arbitre/fin native, API et fallback). Contrat à vérifier des deux côtés : `005k.1-proposed`, bundle SHA256 **9c018ecfee1b62eb0c3cea36b580edb9edcbda43f3772135b01191018d5eacad**, [CONTRACT-PIN.json](CONTRACT-PIN.json). Un hash n’est ni authentification ni approbation. Si le contrat doit changer, négocier et revalider avant code d’intégration ; ne pas éditer les schémas UNIFIED silencieusement.

## Travail et ordre proposés

1. Introduire SpeechOutputPort/session/corrélation dans CORE sans changer SQLite, commandes, PDF/RAG, mémoire ou données. Par défaut garder LocalTts ; l’activation du pilote est un choix visible, jamais un remplacement automatique. CORE reste l’autorité du texte définitif et des confirmations. Réponse trop longue : refus ou aperçu/choix explicite, pas de découpe cachée.
2. Qualifier la vraie API native de Llama/Whisper : annulation, fin effective, déchargement sous verrou et absence de travail encore actif. Le wrapper LLM actuel n’expose pas ce cycle ; ne pas appeler release pendant une inférence. Si un arrêt fiable n’est pas disponible, attendre la fin réelle, garder l’état bloqué et refuser le relais ; timeout coroutine ne libère pas le bail.
3. Développer l’arbitre CORE recommandé après validation, avec un seul travail lourd/résidence non maîtrisée à la fois, génération de bail, mort/reconnexion/expiration et preuve de nettoyage. VOICE ne doit pas accepter une simple déclaration que le LLM est libéré. Pas de modification du dépôt autonome LLM sans nouvelle autorisation.
4. Préparer le client de transport explicitement choisi, d’abord sur fixtures isolées. Relever les certificats publics réels et vérifier identité distante/capacité/contrat avant de transmettre un texte. Refuser inconnue/divergente, pas de clé privée exportée/re-signature des anciennes apps. Aucun Intent implicite ni texte personnel dans logs.
5. Qualification coordonnée avec la fiche VOICE : client CORE d’abord contre port simulé ; endpoint VOICE ensuite en fixture isolée ; **activation d’un vrai IPC seulement après accord** et tests de confiance/baux. Terminer par un même scénario public E2E aux deux SHA connus, puis essai propriétaire volontaire. Pas de profil B déplacé pour débloquer un test.

## Acceptation et rollback

Tests API24/25 (LocalTts historique conservé, bridge indisponible), API26+ (senseurs/admission à qualifier), client absent/incompatible, signataire interdit, consentement/session expirée, réponse périmée, timeout et double annulation, client/service morts, deux lourds, libération réelle, focus et fallback. Préserver migrations SQLite/33 cas et heure/7 cas existants ; qualifier natif et émulateur séparément. Repli seulement explicite après VOICE certainement arrêtée, moteur TTS local réellement prêt/offline. Pas de lecture doublée après résultat ambigu.

Rollback = désactiver le pilote, garder sessions/données/fonctions/clé et voix anciennes ; aucune désinstallation imposée. Aucune hausse de min24, aucun ajout réseau/micro hors fonction historique. PR brouillon distincte, tests et rapport ; aucun merge ni installation sans accord propriétaire.
