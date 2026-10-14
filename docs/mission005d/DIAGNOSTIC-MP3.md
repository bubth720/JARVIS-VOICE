# Mission 005D — diagnostic MP3

Cause démontrable dans `ReferenceAudio.resample24k` : `i * rate / 24000.0` multipliait deux Int **avant** la division Double. À 44100 Hz, le premier débordement arrive à l'échantillon de sortie 48696 (~2,029 s) ; à 48000 Hz, à 44740 (~1,864 s). La position devient négative, aucun coefficient du noyau ne porte sur le signal, poids = 0, valeur = 0/0 = NaN. Les extraits 5 et 10 secondes sont affectés.

L'import faisait déjà le même décodage/rééchantillonnage 5 s, mais ignorait le FloatArray retourné. Aucune vérification de finitude de la sortie n'était faite. La conversion et la préécoute passaient ensuite par `quality`, qui refusait les NaN. Ce refus était masqué par un catch générique ; le décodeur MP3 pouvait avoir parfaitement fonctionné. La reproduction sur le MP3 personnel n'a pas été effectuée : il reste uniquement sur le téléphone.

Correction : multiplication en Double, contrôle de poids et de sortie finie, validation de la sortie à l'import. Les propriétés du signal et le filtre sinc 64 coefficients sont conservés, sans normalisation ni silence ajouté.

## Étapes et confidentialité

MP01 paramètres ; MP02 référence/MediaExtractor ; MP03 ouverture MediaCodec ; MP04 alimentation ; MP05 buffers PCM ; MP06 timestamps, durée et EOS ; MP07 stéréo→mono ; MP08 rééchantillonnage ; MP09 analyse ; MP10 WAV ; MP11 remplacement atomique ; MP12 AudioTrack.

Les diagnostics ne contiennent que code, classe d'exception, nombres d'échantillons et temps. L'exception structurée ne conserve pas de cause ni de message Android qui pourraient contenir un chemin. Pas de Logcat applicatif, URI originale, nom original, contenu audio ou chemin sensible. Les étapes du décodeur sont indépendantes des étapes WAV/lecture.

Buffer PCM : limites vérifiées avant lecture, `clear` puis `limit`/`position` sur duplicate, alignement frame obligatoire, PCM16/float seulement. Un EOS vide est libéré sans tenter de lire un buffer absent. Décodeur initialisé depuis le début pour préserver le réservoir MP3. PTS en microsecondes, paramètres en millisecondes, arithmétique Long. Format de sortie contrôlé. Nombre conservé plafonné à la durée demandée pour empêcher un dépassement de quelques échantillons par arrondi des PTS. La tolérance de fin reste 100 ms (plusieurs frames MP3 de ~26 ms à 44,1 kHz), sans remplissage artificiel ; un manque supérieur est refusé. Timeout 120 s et annulation à chaque cycle.

WAV : PCM16 mono 24000 Hz, en-tête RIFF de 44 octets et taille contrôlée. Écriture privée temporaire puis rename sur le même système de fichiers ; annulation contrôlée avant remplacement et pendant conversion PCM16. Échec/annulation nettoie le temporaire et conserve l'ancien extrait. L'import contrôle aussi l'annulation après inspection, avant remplacement du manifeste. La préécoute utilise le chemin AudioTrack différé déjà présent.

Protection thermique pendant conversion, mesure PSS/temps ; pas de changement de moteur, de threads, de pack Alba, de lecture différée ou du secours manuel.
