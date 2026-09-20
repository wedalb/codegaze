extends Node3D

const Tracking = preload("res://tracking.gd")
var xr: OpenXRInterface
var origin: XROrigin3D
var camera: XRCamera3D
var eye: XRController3D
var screen: MeshInstance3D
var material: StandardMaterial3D
var screen_size := Vector2(1.8, 1.0125)
var frame_id := 0
var connected := false
var recording := false
var session_id := ""
var stop_requested := false
var delivery_errors := 0
var eye_supported := false
var recentered := false
var native_xr := false
var session_focused := false
var sequence := 0
var client_id := "native-" + str(Time.get_unix_time_from_system()) + "-" + str(randi())
var queue: Array = []
var frame_request: HTTPRequest
var sample_request: HTTPRequest
var status_request: HTTPRequest
var command_request: HTTPRequest
var sample_busy := false
var frame_busy := false
var status_busy := false
var command_busy := false
var poll_clock := 0.0
var status_clock := 0.0
var sample_clock := 0.0
var send_clock := 0.0
var server_field: LineEdit
var key_field: LineEdit
var status_label: Label
var source_label: Label
var head_only: CheckBox
var record_button: Button
var server_url := "http://127.0.0.1:8742"
var pairing_key := ""

func _ready() -> void:
	origin = XROrigin3D.new()
	add_child(origin)
	camera = XRCamera3D.new()
	origin.add_child(camera)
	camera.current = true
	eye = XRController3D.new()
	eye.tracker = &"/user/eyes_ext"
	eye.pose = &"eye_pose"
	origin.add_child(eye)
	screen = MeshInstance3D.new()
	var mesh := QuadMesh.new()
	mesh.size = screen_size
	screen.mesh = mesh
	material = StandardMaterial3D.new()
	material.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	material.albedo_color = Color.WHITE
	material.texture_filter = BaseMaterial3D.TEXTURE_FILTER_LINEAR
	screen.material_override = material
	add_child(screen)
	screen.position = Vector3(0, 0, -1.6)
	frame_request = make_request(_frame_received)
	sample_request = make_request(_samples_received)
	status_request = make_request(_status_received)
	command_request = make_request(_command_received)
	build_ui()
	xr = XRServer.find_interface("OpenXR") as OpenXRInterface
	if xr:
		xr.session_focussed.connect(func(): session_focused = true)
		xr.session_visible.connect(func(): session_focused = false)
		xr.session_stopping.connect(func(): session_focused = false)
		xr.session_loss_pending.connect(func(): session_focused = false)
		xr.pose_recentered.connect(func(): recentered = false)
	if xr and (xr.is_initialized() or xr.initialize()):
		native_xr = true
		get_viewport().use_xr = true
		eye_supported = xr.is_eye_gaze_interaction_supported()
		status_label.text = "OpenXR ready. Connect to IntelliJ to display your editor."
	else:
		status_label.text = "No OpenXR runtime. Desktop preview only; no tracking will be recorded."
	for arg in OS.get_cmdline_user_args():
		if arg.begins_with("--server="):
			server_field.text = arg.trim_prefix("--server=")
		if arg.begins_with("--key="):
			key_field.text = arg.trim_prefix("--key=")
	if not key_field.text.is_empty():
		connect_server()

func make_request(callback: Callable) -> HTTPRequest:
	var request := HTTPRequest.new()
	request.timeout = 5
	add_child(request)
	request.request_completed.connect(callback)
	return request

func build_ui() -> void:
	var layer := CanvasLayer.new()
	add_child(layer)
	var panel := PanelContainer.new()
	panel.position = Vector2(16, 16)
	panel.custom_minimum_size = Vector2(580, 0)
	layer.add_child(panel)
	var box := VBoxContainer.new()
	box.add_theme_constant_override("separation", 10)
	panel.add_child(box)
	var title := Label.new()
	title.text = "CodeGaze 0.1  |  OpenXR viewer"
	title.add_theme_font_size_override("font_size", 24)
	box.add_child(title)
	server_field = LineEdit.new()
	server_field.text = server_url
	server_field.placeholder_text = "Server URL from the IntelliJ tool window"
	box.add_child(server_field)
	key_field = LineEdit.new()
	key_field.secret = true
	key_field.placeholder_text = "Paste pairing key from IntelliJ → CodeGaze"
	box.add_child(key_field)
	var buttons := HBoxContainer.new()
	box.add_child(buttons)
	var connect_button := Button.new()
	connect_button.text = "Connect"
	connect_button.pressed.connect(connect_server)
	buttons.add_child(connect_button)
	record_button = Button.new()
	record_button.text = "Start recording (F9)"
	record_button.pressed.connect(toggle_recording)
	buttons.add_child(record_button)
	var center_button := Button.new()
	center_button.text = "Recenter screen (R)"
	center_button.pressed.connect(recenter)
	buttons.add_child(center_button)
	head_only = CheckBox.new()
	head_only.text = "Force head direction (otherwise prefer valid eye gaze)"
	box.add_child(head_only)
	source_label = Label.new()
	source_label.text = "Tracking: unavailable"
	box.add_child(source_label)
	status_label = Label.new()
	status_label.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	box.add_child(status_label)
	var help := Label.new()
	help.text = "Keep IntelliJ visible. Focus it to type using your normal keyboard.\nUse the browser recorder to download sessions. R / F9 work when this window has focus."
	box.add_child(help)

func connect_server() -> void:
	server_url = server_field.text.strip_edges().trim_suffix("/")
	pairing_key = key_field.text.strip_edges()
	if not (server_url.begins_with("http://127.0.0.1:") or server_url.begins_with("http://localhost:") or server_url.begins_with("https://")):
		status_label.text = "Use a loopback HTTP address or an HTTPS proxy."
		return
	if pairing_key.is_empty():
		status_label.text = "Enter the pairing key from IntelliJ."
		return
	connected = true
	poll_clock = 1.0
	status_clock = 2.0

func headers() -> PackedStringArray:
	return PackedStringArray(["Authorization: Bearer " + pairing_key, "Content-Type: application/json"])

func _process(delta: float) -> void:
	if not connected:
		return
	poll_clock += delta
	status_clock += delta
	sample_clock += delta
	send_clock += delta
	if poll_clock >= 0.22 and not frame_busy:
		poll_clock = 0.0
		frame_busy = frame_request.request(server_url + "/api/frame", headers()) == OK
	if status_clock >= 1.0 and not status_busy:
		status_clock = 0.0
		status_busy = status_request.request(server_url + "/api/status", headers()) == OK
	if sample_clock >= 1.0 / 30.0:
		sample_clock = 0.0
		observe()
	if send_clock >= 0.1 and not sample_busy and not queue.is_empty():
		send_clock = 0.0
		var batch := queue.duplicate()
		queue.clear()
		sample_busy = sample_request.request(server_url + "/api/samples", headers(), HTTPClient.METHOD_POST, JSON.stringify({"sessionId": session_id, "samples": batch})) == OK
		if not sample_busy:
			status_label.text = "Could not submit samples; inspect the session before using it."

	if stop_requested and not sample_busy and queue.is_empty() and not command_busy:
		command_busy = command_request.request(server_url + "/api/session/stop", headers(), HTTPClient.METHOD_POST, "{}") == OK

func observe() -> void:
	if not native_xr or frame_id == 0:
		return
	var head_tracker := XRServer.get_tracker("head") as XRPositionalTracker
	var head_pose: XRPose = head_tracker.get_pose("default") if head_tracker else null
	var head_valid := head_pose != null and head_pose.has_tracking_data and head_pose.tracking_confidence != XRPose.XR_TRACKING_CONFIDENCE_NONE
	var eye_tracker := XRServer.get_tracker("/user/eyes_ext") as XRPositionalTracker
	var eye_pose: XRPose = eye_tracker.get_pose("eye_pose") if eye_tracker else null
	var eye_valid := eye_pose != null and eye_pose.has_tracking_data and eye_pose.tracking_confidence != XRPose.XR_TRACKING_CONFIDENCE_NONE
	var choice: Dictionary = Tracking.source_for(eye_supported, eye_valid, head_valid, head_only.button_pressed)
	if not session_focused:
		choice.valid = false
	var tracked: Node3D = eye if choice.source == "eye" else camera
	var ray_origin := tracked.global_position
	var ray_direction := -tracked.global_basis.z.normalized()
	if head_valid and not recentered:
		recenter()
	var uv: Variant = Tracking.screen_hit(ray_origin, ray_direction, screen.global_transform, screen_size) if choice.valid else null
	source_label.text = "Tracking: " + str(choice.source) + (" — valid" if choice.valid else " — lost")
	if recording and not stop_requested:
		queue.append({"clientId": client_id, "sequence": sequence, "frameId": frame_id,
			"clientMonoMs": Time.get_ticks_usec() / 1000.0, "clientEpochMs": Time.get_unix_time_from_system() * 1000.0,
			"source": choice.source, "fallbackReason": choice.fallbackReason, "valid": choice.valid,
			"u": uv.x if uv != null else null, "v": uv.y if uv != null else null,
			"origin": [ray_origin.x, ray_origin.y, ray_origin.z], "direction": [ray_direction.x, ray_direction.y, ray_direction.z],
			"sensorTime": null, "sensorTimeBasis": "unavailable; clientMonoMs is software sampling time"})
		sequence += 1
		if queue.size() > 240:
			queue.clear()
			recording = false
			status_label.text = "Sample queue overflow. Stop recording and inspect your session."

func recenter() -> void:
	screen.global_transform = camera.global_transform
	screen.global_position = camera.global_position - camera.global_basis.z * 1.6
	recentered = true

func _unhandled_key_input(event: InputEvent) -> void:
	if event is InputEventKey and event.pressed and not event.echo:
		if event.keycode == KEY_R:
			recenter()
		elif event.keycode == KEY_F9:
			toggle_recording()

func toggle_recording() -> void:
	if not connected or command_busy:
		return
	# Let in-flight samples finish before stopping. The dashboard can stop/export as well.
	if recording:
		stop_requested = true
		status_label.text = "Finishing queued samples…"
		return
	var endpoint := "/api/session/stop" if recording else "/api/session/start"
	command_busy = command_request.request(server_url + endpoint, headers(), HTTPClient.METHOD_POST, JSON.stringify({"participant": "anonymous"})) == OK

func parse_response(code: int, body: PackedByteArray) -> Dictionary:
	var value: Variant = JSON.parse_string(body.get_string_from_utf8())
	if code != 200 or not value is Dictionary:
		status_label.text = "Connection error " + str(code) + ": " + (str(value.get("error", "Check sharing and pairing key.")) if value is Dictionary else "Check sharing and pairing key.")
		return {}
	return value

func _frame_received(_result: int, code: int, _headers: PackedStringArray, body: PackedByteArray) -> void:
	frame_busy = false
	var data := parse_response(code, body)
	if data.is_empty() or int(data.id) == frame_id:
		return
	var image := Image.new()
	if image.load_jpg_from_buffer(Marshalls.base64_to_raw(data.image)) != OK:
		status_label.text = "The editor image could not be decoded."
		return
	material.albedo_texture = ImageTexture.create_from_image(image)
	screen_size = Vector2(1.8, 1.8 * float(data.height) / float(data.width))
	(screen.mesh as QuadMesh).size = screen_size
	frame_id = int(data.id)

func _samples_received(_result: int, code: int, _headers: PackedStringArray, body: PackedByteArray) -> void:
	sample_busy = false
	if code != 200:
		delivery_errors += 1
	parse_response(code, body)

func apply_status(data: Dictionary) -> void:
	if data.is_empty():
		return
	var next_session := str(data.sessionId) if data.sessionId != null else ""
	if next_session != session_id:
		queue.clear()
		session_id = next_session
		delivery_errors = 0
	recording = data.recording
	record_button.text = "Stop recording (F9)" if recording else "Start recording (F9)"
	status_label.text = ("Recording · " if recording else "Connected · ") + str(data.samples) + " samples. Export through the browser recorder." + (" DELIVERY ERRORS: " + str(delivery_errors) if delivery_errors > 0 else "")

func _status_received(_result: int, code: int, _headers: PackedStringArray, body: PackedByteArray) -> void:
	status_busy = false
	apply_status(parse_response(code, body))

func _command_received(_result: int, code: int, _headers: PackedStringArray, body: PackedByteArray) -> void:
	command_busy = false
	stop_requested = false
	apply_status(parse_response(code, body))
