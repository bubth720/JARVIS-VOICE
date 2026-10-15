# 005I — architecture

Trois dimensions indépendantes : coffre 005H pour l'identité et ses tenseurs immuables ; persona conversationnelle sans tenseur, réservée au futur CORE ; intention d'expression explicite sans reconnaissance d'émotion.

`expressionRuntime` dépend de `identityRuntime` et `pocketRuntime`, sans modifier leurs interfaces. `ExpressionPolicy` produit un plan pur, déterministe, lié à l'UUID identité/profil, au SHA du fichier, au SHA canonique des 18 états, au moteur, au pack, à la graine et au sampler choisis. Aucune intention ne peut sélectionner un autre profil. Toute opération en cours refuse une nouvelle demande, même prioritaire : Arrêt explicite nécessaire. Les gardes matérielles restent imposées par l'application.

005I possède un nouvel identifiant Android et un coffre séparé. Aucune migration destructive, aucun accès au sandbox 005H. Le transfert volontaire est exclusivement local et chiffré JVIENC01. Le profil B réel n'est pas connu du Cloud.
