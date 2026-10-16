# 005J — architecture UI

Base contrôlée : 005I f917ef15ce74a045564ddabdc407ccde4b595ba6. Aucune édition des trois bibliothèques vocales ou des applications historiques.

Choix : Android Views/XML + Material Components 1.12.0, thème Material 3 DayNight. Pas de Compose ni WebView ; dépendance UI standard uniquement, sans compte/SDK de suivi. XML pour la coque et les thèmes, composants natifs homogènes pour les formulaires dynamiques. Coût et dépendances vérifiés à la construction. Min26, target35, écrans sans animations lourdes.

Navigation : Parler / Mes voix / Créer dans une barre basse ; Réglages avancés dans la barre haute. Chaque écran a son propre contenu défilant borné, pas une activité technique à 50 boutons. Statut et Arrêter restent au-dessus du contenu. Cartes arrondies, bleu nuit en mode sombre, bleu/vert doux, textes contrastés, cibles >=48 dp, libellés visibles et TalkBack. Le contenu principal sera borné en largeur sur tablette.

`studioRuntime` contient états, navigation, étapes, validation de durée, paramètres et confirmation de profil. `voiceStudio` contient les vues et la coordination Android ; les moteurs existants sont appelés par une seule file, sans modification. État privé hors sauvegarde : écran, texte, progression et métadonnées du brouillon. Aucun mot de passe ni consentement de checkbox n'est repris automatiquement. Retour arrière ne supprime rien. Les fichiers/identités du coffre restent la source de vérité après interruption.

Maquette structurelle :
- Parler : carte de voix, état modèle, phrase, Parler, réécoute valide, options repliées (pauses/contexte).
- Mes voix : liste d'identités puis profils, choix explicite et menu de gestion confirmé.
- Assistant : une étape parmi sept ; retour/continuer et progression.
- Avancé : graine/LSD/température, A/B, diagnostics, licences et gestion fine des références.

Phase A construit les états vides et la navigation ; aucun bouton ne simule une synthèse ou une création réussie.

## Coordination et reprise implémentées

`StudioActivity` coordonne uniquement navigation, sélecteurs SAF et dialogues. `SpeakScreen`, `VoicesScreen`, `WizardScreen`, `AdvancedScreen` composent les vues. `StudioController`, détenu par l'Application, possède la file unique et le lecteur ; une rotation réattache l'écran et conserve le travail, quitter l'application annule. Les écouteurs ont un propriétaire pour éviter qu'une ancienne Activity détache la suivante.

`StudioLibrary` préserve la sélection active pendant la préparation d'une nouvelle identité. Ajouter deux audios crée deux références de cette même identité. La confirmation du profil lie UUID/empreinte/pack et ne confirme jamais automatiquement un profil restauré. Une restauration préserve la voix précédente jusqu’au choix explicite ; une suppression concernée invalide la confirmation. Les consentements sont explicitement redemandés, jamais persistés comme cases cochées. Le brouillon est sauvegardé hors Android Bundle, mots de passe exclus ; champs de texte privés désactivent autofill et demandent l'absence d'apprentissage IME. Dialogues privés et Activity utilisent FLAG_SECURE.

Les helpers MP3, AudioTrack, mesures et protections sont repris de 005I avec seul changement de package ; aucune copie de son Activity monolithique. Les services de modèles/archives/encodeur sont les mêmes bibliothèques inchangées.

Caches privés : principal, A/B d'expression, A/B de profils ; chacun <=20 s, au plus 10 identités (moins de 92 Mio de PCM total, hors petites métadonnées). Réécoute sans modèle, vérification des contextes et suppression isolée. Pas de bascule entre deux profils sans action explicite ; la comparaison de profils ne change pas la voix principale.

## Validation et frontières de test

`StudioRendering` prépare exactement la politique 005I puis appelle ExpressionRenderer. Quatre générations complètes sur Alba publique et le pack FP32 privé existant comparent le PCM flottant avant conversion PCM16. Les poids et fichiers audio ne sont pas inclus dans les rapports.

Le backend officiel et les documents ContentResolver sont injectables : l’APK de test seule contient un backend synthétique et un lecteur de documents fixtures. L’APK livrée ne contient aucune voix ni modèle de démonstration, aucune branche de succès factice. Les tests de parcours utilisent le vrai coffre/codec/archive/contrôleur/AudioTrack mais ne constituent pas une validation de l’encodeur ONNX Android réel ni des autorisations SAF physiques Xiaomi. Le test natif JVM complémentaire utilise le pack réel ; l’essai sur téléphone reste distinct.

Les captures automatiques montrent uniquement une bibliothèque vide synthétique. FLAG_SECURE est conservé dans l’application livrée, sa levée temporaire étant confinée au code d’instrumentation pour ces captures. CI installe aussi 005I avec un marqueur synthétique privé et vérifie coexistence et conservation ; aucune donnée personnelle n’est requise.
