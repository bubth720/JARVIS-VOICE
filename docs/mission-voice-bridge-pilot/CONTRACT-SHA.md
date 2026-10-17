# Empreintes et coordination CORE/VOICE

Source005K gelée : `005k.1-proposed`, SHA256 **9c018ecfee1b62eb0c3cea36b580edb9edcbda43f3772135b01191018d5eacad**. `docs/mission005k/CONTRACT-PIN.json` et tous ses fichiers inchangés ; les schémas UNIFIED ne sont pas approuvés rétroactivement.

Nouveau complément **fixture d'authentification uniquement** : `voice.bridge.fixture/1`, SHA256 **cfbdff471ed0403113803a0f4c86ec75c57f723cc828f5af3c13fb3ef8042cf2**. Canonisation : pour CONTRACT.md et IVoicePilotFixture.aidl triés par nom, nomUTF8,NUL,octets exacts,NUL. Détails/fichiers individuels sous `voiceBridgePilot/contract/PIN.json`. BuildConfig de l'endpoint/client recalculé depuis les mêmes octets ; AIDL client généré depuis la source endpoint unique.

Branche sœur : `bubth720/JAVIS-Local-v2:feat/core-voice-pilot`, base115dd3fe9b8e82b295b076dc045601a04d21a06f. C1 commit05c087b ; C2 natif bloqué et C3 non commencée. Les copies dans `docs/mission-core-voice-pilot/fixture-contract/` sont documentaires et identiques ; aucun nouveau service/client transport intégré au CORE historique.

Cette coordination est un alignement technique des deux branches développées dans la même session, pas une revue indépendante ou une preuve de deux processus connectés. Le complément V1 ne définit pas encore le contrat de profil et d'autorité native V2 ; aucune référence client ne fabrique une preuve globale.

```sh
python3 scripts/bridge/verify_pilot_scope.py --core-root ../JAVIS-Local-v2
# Dans le dépôt CORE :
python3 tools/check-core-voice-pilot.py --voice-root ../JARVIS-VOICE
```

Les SHA de livraison définitifs sont fournis avec la publication et l'historique Git. Comparer les empreintes exactes avant tout futur raccordement V2/V3. Toute extension nécessite nouvelle version/hash et tests croisés.
