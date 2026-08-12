extends Node

const LOG_PATH := "user://eyril.log"
const MAX_BYTES := 512 * 1024
var session_id := ""

func _ready() -> void:
    session_id = "%s-%s" % [Time.get_datetime_string_from_system().replace(":", "-"), str(randi() % 100000)]
    write("APP_START", {
        "session": session_id,
        "engine": Engine.get_version_info().get("string", "unknown"),
        "os": OS.get_name(),
        "model": OS.get_model_name(),
        "locale": OS.get_locale(),
        "screen": "%sx%s" % [DisplayServer.screen_get_size().x, DisplayServer.screen_get_size().y]
    })

func write(event: String, data: Dictionary = {}) -> void:
    var line := "%s | %s | %s\n" % [Time.get_datetime_string_from_system(), event, JSON.stringify(data)]
    var old := ""
    if FileAccess.file_exists(LOG_PATH):
        old = FileAccess.get_file_as_string(LOG_PATH)
        if old.to_utf8_buffer().size() > MAX_BYTES:
            old = old.right(old.length() / 2)
    var f := FileAccess.open(LOG_PATH, FileAccess.WRITE)
    if f:
        f.store_string(old + line)
        f.close()
    print("EYRIL_LOG ", line.strip_edges())

func text() -> String:
    if FileAccess.file_exists(LOG_PATH):
        return FileAccess.get_file_as_string(LOG_PATH)
    return "Лог пока пуст."

func copy_to_clipboard() -> void:
    DisplayServer.clipboard_set(text())
    write("LOG_COPIED_FOR_MAX")
