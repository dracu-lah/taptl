# ML Kit looks up its model managers by class at runtime (RemoteModelManager → TranslateRemoteModel).
# R8 merges/strips those classes and the lookup returns null, so keep ML Kit's public API.
-keep class com.google.mlkit.** { *; }
