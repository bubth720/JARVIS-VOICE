# Origine et licences — Pocket Probe

Le code Android stable et JARVIS CORE ne sont pas modifiés. Les modèles, états vocaux et APK générés restent hors du dépôt Git ; seuls leurs manifestes, sommes de contrôle et outils de préparation sont suivis.

| Composant | Référence | Licence / portée |
| --- | --- | --- |
| Pocket TTS officiel | Kyutai, code v3.3.0 `3dbee45d343d7dddd0d105468d17f8dcba14db3e` | Code MIT ; auteurs : Manu Orsini, Simon Rouard, Gabriel De Marmiesse, Václav Volhejn, Neil Zeghidour, Alexandre Défossez. |
| Poids français publics | `kyutai/pocket-tts-without-voice-cloning`, `languages/french/model.safetensors`, révision `8843db76457a91db32077edf8dfcd1c0e3e755fd` | CC BY 4.0. Modèle français 6 couches, sans encodeur de clonage. |
| Tokenizer français | Même dépôt et révision, `languages/french/tokenizer.json` | CC BY 4.0 ; Unigram, 4000 pièces, Metaspace et fallback UTF-8. SHA-256 `6211acffe9cb95172f71f0051f18e26ea305b2b32ea00b331ee976551c70887f`. |
| Export ONNX INT8 | `Alias-Alias/pocket-3-3-0-tts-onnx`, révision `71a4d6d11a76e26b98709fa163477c77d16bdbdb`, dossier `fr` | Export communautaire dérivé des poids officiels CC BY 4.0 ; frontières d'états de streaming et quantification dynamique MatMul. Ni un export ONNX officiel Kyutai ni un pack sherpa-onnx. |
| État vocal Alba | État officiel français `languages/french/embeddings/alba.safetensors`, révision `4e1e0a3e611c51c0b4ed8174fc10f32a54644303`, encapsulé dans l'export | Origine : Alba MacKenna, `kyutai/tts-voices/alba-mackenna/casual.wav`, CC BY 4.0. Seul cet état public est conservé dans le pack distribué. |
| ONNX Runtime Android/JVM | Microsoft, version 1.27.0 | MIT ; bibliothèques précompilées Android ARM64. |
| Kotlin, Gradle, Android Gradle Plugin | Versions fixées dans les fichiers Gradle | Kotlin/Gradle/AGP : Apache-2.0 ; avis des distributions applicables. |

Attribution : **Pocket TTS par Kyutai ; voix interprétée par Alba MacKenna ; export ONNX par Alias-Alias ; adaptation Kotlin/Android et pack limité à Alba pour JARVIS Pocket Probe.** Modifications du pack upstream : suppression de sept états prédéfinis et réduction de leur liste dans le manifeste. Les graphes ONNX et le tokenizer sont inchangés. Aucun entraînement ni clonage personnel.

Les autres états de l'export original ne sont pas distribués, notamment les voix issues d'Expresso/EARS soumises à des conditions non commerciales. Les enregistrements d'origine ne sont pas fournis dans notre pack. Une licence du modèle ne remplace pas les droits du locuteur ou ceux de l'enregistrement.

Liens : [Kyutai](https://github.com/kyutai-labs/pocket-tts/tree/3dbee45d343d7dddd0d105468d17f8dcba14db3e), [poids officiels publics](https://huggingface.co/kyutai/pocket-tts-without-voice-cloning), [export figé](https://huggingface.co/Alias-Alias/pocket-3-3-0-tts-onnx/tree/71a4d6d11a76e26b98709fa163477c77d16bdbdb/fr), [voix et licences](https://huggingface.co/kyutai/tts-voices), [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/), [licence Microsoft](https://github.com/microsoft/onnxruntime/blob/v1.27.0/LICENSE).

Les conditions d'utilisation Kyutai excluent le clonage sans consentement explicite et légal et les utilisations trompeuses ou illicites. Le futur encodeur nécessitera un pack complet autorisé et une nouvelle validation. Le pack de cette mission ne peut pas encoder un MP3.
