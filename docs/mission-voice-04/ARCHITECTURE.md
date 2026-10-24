# VOICE-04 — architecture expérimentale isolée

Base préparée `4f47eb3`, base fonctionnelle VOICE-02 `3ffb280`. Aucun changement de MAIN, CORE, UNIFIED ou Binder. L'application `voice04Studio` a l'identifiant `com.jarvis.voice.studio.voice04` ; elle ne peut lire le stockage de 005J ni le profil B. Aucun import ou déplacement automatique. Les archives JVIENC01 et profils JVFP1 utilisent les services existants, sans migration.

Le module réutilise les sources UI historiques en lecture seule, avec des fichiers d'adaptation VOICE-04 séparés (contrôleur, activité, diagnostics/lecteur). `voiceStudio` conserve sa configuration, son identité Android et ses sources. Les composants communs de création/restauration d'archives, consentement et admission restent ceux de 005J/VOICE-02. L'APK laboratoire n'est jamais une mise à jour de 005J.

`ObservedQualityEngine` est une copie instrumentée isolée du moteur FP32 de référence, sans changement du sampler, des quatre sessions ni des 18 états. La seule extension du runtime historique est la visibilité publique de `CancellationToken.withOptions`, avec le même corps : le moteur expérimental partage ainsi la terminaison ONNX effective sans second poller ni faux arrêt natif. Aucune résidence permanente. Le contrôle de parité doit comparer ses PCM au `QualityEngine` intact.

`SpeedTrace` n'accepte que des événements, durées et compteurs ; aucune chaîne libre de texte utilisateur, URI, UUID, hash de profil ou enregistrement. Monotone relatif, `null` pour l'absence. FIRST_WRITE et PRESENTATION_ESTIMATED ne sont jamais une preuve d'audibilité humaine. CLOSE_RETURNED n'est pas une preuve de mémoire JNI libérée. Un échec sans retour de fermeture met le parcours expérimental en quarantaine jusqu'au redémarrage du processus.

Admission lourde Studio existante (~2 Gio disponibles), surveillance PSS/thermique toutes les 250 ms, interruption mémoire/thermique, CPU ONNX un thread, une opération lourde à la fois. Les compteurs batterie/énergie sont contextuels, parfois absents/quantifiés. Pas de permission réseau, compte, téléchargement de poids ou télémétrie.
