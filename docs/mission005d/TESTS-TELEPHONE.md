# Validation Xiaomi Android 15 — à réaliser par le propriétaire

Ces essais ne sont pas annoncés comme exécutés dans le cloud. Ne transmettre aucun MP3, WAV, profil, URI ou chemin privé.

1. Installer Pocket Probe 0.4.0-mission005d et conserver le pack Alba connu fonctionnel.
2. Importer le MP3 privé existant de 12 s, sans le réenregistrer. Extraire début 0/durée 5, puis début 0/durée 10. Vérifier durée et niveaux, écouter chaque extrait : pas de coupure ni saturation nouvelle.
3. Tester un extrait décalé valide, Arrêt pendant conversion et écoute, puis relancer. Une conversion refusée/annulée doit conserver l'ancien WAV. Noter seulement le code MPxx et les métriques sans données personnelles.
4. Fermer complètement et rouvrir. Refaire conversion/écoute en mode avion, Wi-Fi coupé. Supprimer la référence ; après redémarrage elle doit rester absente.
5. Alba : mode économique 1 thread, lecture progressive désactivée ; phrase habituelle puis nouvelle phrase française. Vérifier phrase complète, zéro coupure et compteur underruns 0. Arrêt pendant attente : aucun son ultérieur ; Arrêt pendant lecture puis récupération.
6. Comparer 5 lectures à chaud aux résultats utilisateur précédents. Relever génération, RTF, PSS max, thermique, underruns et latence. Refroidir si état thermique ≥3. Le secours Android reste un choix explicite.
7. La création/sélection d'un profil personnel reste désactivée tant que le rapport encodeur ne valide pas toute la chaîne. Un WAV n'est pas une voix clonée.
