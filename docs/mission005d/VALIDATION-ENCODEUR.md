# Encodeur officiel — preuves et limites

**Actualisation du 14 octobre 2026 après republication :** le CDN est accessible,
le checkpoint complet a été téléchargé et son SHA256 mesuré est conforme. Les
neuf parités de l'encodeur+BOS, les 18 états FP32, FlowLM, Flow et le décodeur
français passent. La comparaison supplémentaire de générations indépendantes
dépasse la tolérance stricte sur deux phrases ; les mêmes entrées intermédiaires
passent sur les trois phrases. Le candidat Android reste désactivé dans l'UI.
Voir [RESULTATS-REPRISE.md](RESULTATS-REPRISE.md) et ses rapports JSON.

Les sections ci-dessous conservent les preuves **antérieures à cette reprise** ;
le refus du CDN et l'absence d'export qu'elles décrivent sont historiques.

## Accès sécurisé

Le secret réseau HF_TOKEN est présent et fonctionne par substitution du proxy. `whoami-v2` authentifié : HTTP 200, sans publication de l'identité. Métadonnées à la révision demandée : HTTP 200, dépôt gated auto, licence CC-BY-4.0. Le téléchargement léger authentifié README.md avec huggingface_hub 2.2.0 a réussi sans persistance du jeton. Le secret reste limité à huggingface.co.

Le checkpoint répond HTTP 302 vers **us.aws.cdn.hf.co**. La requête sur cette URL signée, sans Authorization, est refusée par le proxy : CONNECT 403. Il s'agit d'un domaine de stockage non autorisé, pas d'une authentification HF refusée, de conditions non acceptées ou d'une révision introuvable. La politique active de cette session ne contient encore que huggingface.co au moment de cette mesure. Le propriétaire a indiqué ajouter le domaine de stockage ; cela doit devenir effectif avant de reprendre. Aucune URL signée ni valeur de secret n'est conservée dans le rapport.

Preuves : `HF-ACCESS.json`, `OFFICIAL-CHECKPOINT-METADATA.json`. Empreinte **annoncée par les métadonnées officielles**, pas mesurée sur des poids téléchargés :

- Révision : d0ebc5deb50bc6b225f686782f124dd37e2dca0a.
- Fichier : languages/french/model.safetensors.
- Taille : 219029196 octets.
- SHA256 : dd5fe98f5577ecfd56db9af8057cd05fe20cbc91560042aec8a56cfb01c34995.

## Travail effectué

`check_official_access.py` produit des diagnostics filtrés sans token, identité ou URL de stockage. `download_official_checkpoint.py` exige l'hôte HTTPS exact, télécharge sans en-tête HF sur le CDN, vérifie taille et SHA256 puis remplace le fichier privé. Quatre tests HTTP synthétiques exécutés : absence d'Authorization au stockage, refus d'hôte inconnu, refus de poids corrompus/conservation du précédent, arrêt avant stockage si HF refuse l'authentification.

L'exporteur exige désormais `--checkpoint` exact et interdit le repli sur les poids sans clonage. Configuration française 3.3.0, six couches, BOS activé, FP32 CPU, un thread. Neuf cas de parité prévus : silence/sinus/bruit, longueurs 120000, 240017, 360000, formes attendues [1,64,1024], [1,127,1024], [1,189,1024], BOS une seule fois ; atol/rtol 2e-4, maxAbs/L2 relatif et versions dans le rapport. Le comparateur appelle la méthode officielle sur l'entrée non pré-paddée, afin de vérifier aussi le padding. Aucun contournement beartype ne reste nécessaire.

Le garde-fou a réellement refusé l'export faute de checkpoint. La conversion ONNX et les neuf comparaisons **n'ont pas été exécutées** : poids non téléchargés. L'installation locale de pocket-tts 3.3.0, torch 2.6.0+cpu, ONNX et ONNX Runtime 1.27.0 est terminée.

## Compatibilité observée et encore requise

Le tokenizer officiel français (révision 8843db76457a91db32077edf8dfcd1c0e3e755fd) a été téléchargé et haché. SHA256 6211acffe9cb95172f71f0051f18e26ea305b2b32ea00b331ee976551c70887f, identique octet pour octet au pack Alba épinglé. Rapport : `TOKENIZER-CHECK.json`. Cela ne valide pas le BOS, les poids, la projection vocale, les 18 caches, offsets/pads, la quantification ni le générateur complet.

La source Python du wheel officiel confirme projection Mimi 512→FlowLM 1024, padding de frames 1920 échantillons à 24 kHz, BOS avant le conditionnement, six couches françaises. Les poids restent indispensables pour établir la parité réelle. Le candidat tiers précédemment incompatible reste exclu.

Aucun mélange de pack n'a été réalisé. Le pack Alba connu fonctionnel est conservé. Aucun profil personnel, cache cloné ou génération de référence publique n'est déclaré valide. Le clonage et la sélection du profil restent désactivés ; aucune APK de clonage ni pack dérivé protégé n'est publié.

## Reprise autorisée dès que le domaine est effectif

Exécuter dans Codex, avec sorties sous work/ et jamais dans GitHub Actions public :

```sh
python scripts/pocket/check_official_access.py --report work/hf-access.json
python scripts/pocket/download_official_checkpoint.py --destination work/private-model/model.safetensors --report work/private-model/provenance.json
python scripts/pocket/export_matched_encoder.py --checkpoint work/private-model/model.safetensors --output-dir work/private-export
```

Puis prouver la parité des 18 états et la cohérence générateur/FlowLM/décodeur/tokenizer/INT8, sur référence publique autorisée et phrases françaises nouvelles. Si le pack actuel diverge, exporter un pack cohérent séparé et préserver Alba. L'intégration Android versionnée/atomique et les essais perceptifs ne commencent qu'après ces preuves. La ressemblance personnelle restera évaluée uniquement par l'utilisateur sur son téléphone.
