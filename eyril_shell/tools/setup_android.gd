extends SceneTree
func _init() -> void:
    var sdk := OS.get_environment("ANDROID_HOME")
    if sdk.is_empty(): sdk = OS.get_environment("ANDROID_SDK_ROOT")
    if sdk.is_empty():
        printerr("ANDROID_HOME/ANDROID_SDK_ROOT missing")
        quit(2)
        return
    var js := OS.get_environment("JAVA_HOME")
    var settings := EditorInterface.get_editor_settings()
    settings.set_setting("export/android/android_sdk_path", sdk)
    if not js.is_empty(): settings.set_setting("export/android/java_sdk_path", js)
    settings.set_setting("export/android/debug_keystore", ProjectSettings.globalize_path("res://debug.keystore"))
    settings.set_setting("export/android/debug_keystore_user", "androiddebugkey")
    settings.set_setting("export/android/debug_keystore_pass", "android")
    settings.save()
    print("Configured Android SDK: ", sdk)
    print("Configured Java SDK: ", js)
    print("Configured debug keystore: ", ProjectSettings.globalize_path("res://debug.keystore"))
    quit(0)
