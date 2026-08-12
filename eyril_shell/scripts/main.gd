extends Control

const RAW_DIR := "res://assets/raw/"
const SAVE_PATH := "user://progress.json"
const TOTAL := 10

var bg: TextureRect
var dimmer: ColorRect
var ui: Control
var start_hotspot: Button
var current := 0
var player_name := ""
var selected_hero := "boy"
var choice_s010 := ""
var log_panel: PanelContainer
var log_text: TextEdit
var next_button: Button
var back_button: Button

func _ready() -> void:
    set_process_unhandled_input(true)
    _build_base()
    _load_save()
    _show_splash()
    GameLog.write("UI_READY", {"build":"0.1.3"})

func _notification(what: int) -> void:
    if what == NOTIFICATION_WM_CLOSE_REQUEST:
        GameLog.write("APP_CLOSE_REQUEST", {"screen": current})
        get_tree().quit()

func _build_base() -> void:
    bg = TextureRect.new()
    bg.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
    bg.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
    bg.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_COVERED
    add_child(bg)
    dimmer = ColorRect.new()
    dimmer.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
    dimmer.color = Color(0,0,0,0)
    dimmer.mouse_filter = Control.MOUSE_FILTER_IGNORE
    add_child(dimmer)
    ui = Control.new()
    ui.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
    add_child(ui)

func _clear_ui() -> void:
    for child in ui.get_children():
        child.queue_free()
    start_hotspot = null
    next_button = null
    back_button = null

func _load_raw_jpg(id: String) -> Texture2D:
    var path := RAW_DIR + id + ".dat"
    if not FileAccess.file_exists(path):
        GameLog.write("ASSET_MISSING", {"path":path})
        return null
    var bytes := FileAccess.get_file_as_bytes(path)
    var image := Image.new()
    var err := image.load_jpg_from_buffer(bytes)
    if err != OK:
        GameLog.write("ASSET_DECODE_ERROR", {"path":path,"error":err,"bytes":bytes.size()})
        return null
    GameLog.write("ASSET_LOADED", {"path":path,"w":image.get_width(),"h":image.get_height(),"bytes":bytes.size()})
    return ImageTexture.create_from_image(image)

func _set_background(id: String) -> void:
    var tex := _load_raw_jpg(id)
    if tex:
        bg.texture = tex

func _show_splash() -> void:
    current = 0
    _clear_ui()
    _set_background("main_splash")
    dimmer.color = Color(0,0,0,0)
    GameLog.write("SCREEN_OPEN", {"id":"SPLASH"})
    start_hotspot = Button.new()
    start_hotspot.position = Vector2(78, 690)
    start_hotspot.size = Vector2(384, 105)
    start_hotspot.flat = true
    start_hotspot.modulate = Color(1,1,1,0.01)
    start_hotspot.focus_mode = Control.FOCUS_NONE
    start_hotspot.pressed.connect(_on_start_pressed)
    ui.add_child(start_hotspot)
    var log_btn := _make_fantasy_button("ЛОГ", Vector2(420, 18), Vector2(102, 48), 16)
    log_btn.pressed.connect(_open_log)
    ui.add_child(log_btn)
    if _has_save():
        var cont := _make_fantasy_button("ПРОДОЛЖИТЬ", Vector2(155, 812), Vector2(230, 58), 19)
        cont.pressed.connect(_on_continue_pressed)
        ui.add_child(cont)

func _on_start_pressed() -> void:
    GameLog.write("TAP", {"control":"painted_start_hotspot"})
    _transition_to(1)

func _on_continue_pressed() -> void:
    GameLog.write("TAP", {"control":"continue"})
    _load_save()
    _transition_to(clampi(current, 1, TOTAL))

func _transition_to(index: int) -> void:
    var tween := create_tween()
    tween.tween_property(dimmer, "color", Color(0,0,0,0.65), 0.14)
    tween.tween_callback(func(): _show_story(index))
    tween.tween_property(dimmer, "color", Color(0,0,0,0), 0.22)

func _show_story(index: int) -> void:
    current = clampi(index, 1, TOTAL)
    _clear_ui()
    _set_background("S%03d" % current)
    GameLog.write("SCREEN_OPEN", {"id":"S%03d" % current,"hero":selected_hero,"name":player_name})
    _save()
    back_button = _make_fantasy_button("НАЗАД", Vector2(18, 22), Vector2(116, 50), 16)
    back_button.pressed.connect(_on_back)
    ui.add_child(back_button)
    var log_btn := _make_fantasy_button("ЛОГ", Vector2(420, 22), Vector2(102, 50), 16)
    log_btn.pressed.connect(_open_log)
    ui.add_child(log_btn)
    if current == 2:
        _build_name_input()
    elif current == 3:
        _build_hero_choice()
    elif current == 10:
        _build_final_choice()
    else:
        next_button = _make_fantasy_button("ДАЛЕЕ", Vector2(135, 855), Vector2(270, 70), 24)
        next_button.pressed.connect(_on_next)
        ui.add_child(next_button)

func _build_name_input() -> void:
    var panel := PanelContainer.new()
    panel.position = Vector2(58, 705)
    panel.size = Vector2(424, 190)
    panel.add_theme_stylebox_override("panel", _panel_style())
    ui.add_child(panel)
    var vb := VBoxContainer.new()
    vb.add_theme_constant_override("separation", 10)
    panel.add_child(vb)
    var title := Label.new()
    title.text = "КАК ТЕБЯ ЗОВУТ?"
    title.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
    title.add_theme_font_size_override("font_size", 22)
    vb.add_child(title)
    var line := LineEdit.new()
    line.placeholder_text = "Имя героя"
    line.text = player_name
    line.add_theme_font_size_override("font_size", 21)
    vb.add_child(line)
    var ok := _make_fantasy_button("ПРОДОЛЖИТЬ", Vector2.ZERO, Vector2(0,62), 20)
    ok.size_flags_horizontal = Control.SIZE_EXPAND_FILL
    ok.pressed.connect(func():
        player_name = line.text.strip_edges()
        if player_name.is_empty(): player_name = "Герой"
        GameLog.write("NAME_SET", {"name":player_name})
        _save()
        _transition_to(3)
    )
    vb.add_child(ok)

func _build_hero_choice() -> void:
    var boy := _make_fantasy_button("МАЛЬЧИК", Vector2(35, 815), Vector2(220, 76), 21)
    var girl := _make_fantasy_button("ДЕВОЧКА", Vector2(285, 815), Vector2(220, 76), 21)
    boy.pressed.connect(func(): _select_hero("boy"))
    girl.pressed.connect(func(): _select_hero("girl"))
    ui.add_child(boy)
    ui.add_child(girl)

func _select_hero(hero: String) -> void:
    selected_hero = hero
    GameLog.write("HERO_SELECTED", {"hero":hero})
    _save()
    _transition_to(4)

func _build_final_choice() -> void:
    var panel := PanelContainer.new()
    panel.position = Vector2(46, 595)
    panel.size = Vector2(448, 330)
    panel.add_theme_stylebox_override("panel", _panel_style())
    ui.add_child(panel)
    var vb := VBoxContainer.new()
    vb.add_theme_constant_override("separation", 7)
    panel.add_child(vb)
    var title := Label.new()
    title.text = "ПЕРВАЯ ПРАВКА"
    title.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
    title.add_theme_font_size_override("font_size", 22)
    vb.add_child(title)
    for label in ["SAVE", "EXCLUDE", "ADD", "TRANSFER", "WITNESS"]:
        var b := _make_fantasy_button(label, Vector2.ZERO, Vector2(0,47), 17)
        b.size_flags_horizontal = Control.SIZE_EXPAND_FILL
        b.pressed.connect(func(l=label): _select_final(l))
        vb.add_child(b)

func _select_final(label: String) -> void:
    choice_s010 = label
    GameLog.write("FINAL_CHOICE", {"choice":label})
    _save()
    _show_result_message("Выбор записан: " + label)

func _show_result_message(text_value: String) -> void:
    var p := PanelContainer.new()
    p.position = Vector2(65, 380)
    p.size = Vector2(410, 150)
    p.add_theme_stylebox_override("panel", _panel_style())
    var l := Label.new()
    l.text = text_value + "\n\nДля теста глава завершена."
    l.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
    l.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
    l.add_theme_font_size_override("font_size", 19)
    p.add_child(l)
    ui.add_child(p)

func _on_next() -> void:
    GameLog.write("TAP", {"control":"next","screen":current})
    if current < TOTAL: _transition_to(current + 1)

func _on_back() -> void:
    GameLog.write("TAP", {"control":"back","screen":current})
    if current <= 1:
        _show_splash()
    else:
        _transition_to(current - 1)

func _make_fantasy_button(text_value: String, pos: Vector2, sz: Vector2, font_size: int) -> Button:
    var b := Button.new()
    b.text = text_value
    b.position = pos
    b.size = sz
    b.focus_mode = Control.FOCUS_NONE
    b.add_theme_font_size_override("font_size", font_size)
    b.add_theme_color_override("font_color", Color("eaf5ff"))
    b.add_theme_color_override("font_hover_color", Color.WHITE)
    b.add_theme_color_override("font_pressed_color", Color("fff4c7"))
    b.add_theme_stylebox_override("normal", _button_style(Color("102f99"), Color("e7b451"), 3))
    b.add_theme_stylebox_override("hover", _button_style(Color("174bd1"), Color("ffd76a"), 4))
    b.add_theme_stylebox_override("pressed", _button_style(Color("0a226d"), Color("fff0a0"), 4))
    return b

func _button_style(fill: Color, border: Color, width: int) -> StyleBoxFlat:
    var s := StyleBoxFlat.new()
    s.bg_color = fill
    s.border_color = border
    s.set_border_width_all(width)
    s.corner_radius_top_left = 18
    s.corner_radius_top_right = 18
    s.corner_radius_bottom_left = 18
    s.corner_radius_bottom_right = 18
    s.shadow_color = Color(0.12,0.35,1.0,0.55)
    s.shadow_size = 10
    s.content_margin_left = 14
    s.content_margin_right = 14
    return s

func _panel_style() -> StyleBoxFlat:
    var s := StyleBoxFlat.new()
    s.bg_color = Color(0.015,0.035,0.12,0.93)
    s.border_color = Color("d6a64b")
    s.set_border_width_all(2)
    s.corner_radius_top_left = 18
    s.corner_radius_top_right = 18
    s.corner_radius_bottom_left = 18
    s.corner_radius_bottom_right = 18
    s.content_margin_left = 14
    s.content_margin_right = 14
    s.content_margin_top = 12
    s.content_margin_bottom = 12
    return s

func _open_log() -> void:
    GameLog.write("TAP", {"control":"open_log","screen":current})
    if is_instance_valid(log_panel): return
    log_panel = PanelContainer.new()
    log_panel.position = Vector2(18, 95)
    log_panel.size = Vector2(504, 730)
    log_panel.add_theme_stylebox_override("panel", _panel_style())
    ui.add_child(log_panel)
    var vb := VBoxContainer.new()
    vb.add_theme_constant_override("separation", 8)
    log_panel.add_child(vb)
    var title := Label.new()
    title.text = "ТЕХНИЧЕСКИЙ ЛОГ EYRIL"
    title.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
    title.add_theme_font_size_override("font_size", 18)
    vb.add_child(title)
    log_text = TextEdit.new()
    log_text.text = GameLog.text()
    log_text.editable = false
    log_text.size_flags_vertical = Control.SIZE_EXPAND_FILL
    log_text.add_theme_font_size_override("font_size", 12)
    vb.add_child(log_text)
    var copy := _make_fantasy_button("КОПИРОВАТЬ ДЛЯ MAX", Vector2.ZERO, Vector2(0,55), 16)
    copy.size_flags_horizontal = Control.SIZE_EXPAND_FILL
    copy.pressed.connect(func():
        GameLog.copy_to_clipboard()
        log_text.text = GameLog.text()
        copy.text = "СКОПИРОВАНО"
    )
    vb.add_child(copy)
    var close := _make_fantasy_button("ЗАКРЫТЬ", Vector2.ZERO, Vector2(0,50), 16)
    close.size_flags_horizontal = Control.SIZE_EXPAND_FILL
    close.pressed.connect(func():
        GameLog.write("LOG_CLOSED")
        log_panel.queue_free()
        log_panel = null
    )
    vb.add_child(close)

func _has_save() -> bool:
    return FileAccess.file_exists(SAVE_PATH)

func _save() -> void:
    var data := {"screen":current,"name":player_name,"hero":selected_hero,"choice_s010":choice_s010}
    var f := FileAccess.open(SAVE_PATH, FileAccess.WRITE)
    if f:
        f.store_string(JSON.stringify(data))
        f.close()
        GameLog.write("SAVE_OK", data)
    else:
        GameLog.write("SAVE_FAIL")

func _load_save() -> void:
    if not FileAccess.file_exists(SAVE_PATH): return
    var raw := FileAccess.get_file_as_string(SAVE_PATH)
    var data = JSON.parse_string(raw)
    if typeof(data) == TYPE_DICTIONARY:
        current = int(data.get("screen", 0))
        player_name = str(data.get("name", ""))
        selected_hero = str(data.get("hero", "boy"))
        choice_s010 = str(data.get("choice_s010", ""))
        GameLog.write("SAVE_LOADED", data)
    else:
        GameLog.write("SAVE_CORRUPT", {"raw":raw.left(200)})
