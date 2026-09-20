extends SceneTree

func _initialize() -> void:
	call_deferred("run_test")

func run_test() -> void:
	var scene := load("res://main.tscn") as PackedScene
	var app: Node = scene.instantiate()
	root.add_child(app)
	for i in range(60):
		if int(app.get("frame_id")) > 0:
			break
		await create_timer(0.1).timeout
	if int(app.get("frame_id")) == 0:
		push_error("Native HTTP integration: no decoded frame received")
		quit(1)
		return
	var material: StandardMaterial3D = app.get("material")
	if material.albedo_texture == null or material.albedo_texture.get_width() != 1200:
		push_error("Native HTTP integration: missing/invalid editor texture")
		quit(1)
		return
	app.call("toggle_recording")
	for i in range(40):
		if bool(app.get("recording")):
			break
		await create_timer(0.1).timeout
	if not bool(app.get("recording")):
		push_error("Native HTTP integration: recording did not start")
		quit(1)
		return
	await create_timer(0.15).timeout
	if not (app.get("queue") as Array).is_empty():
		push_error("Native HTTP integration: fabricated tracking without a headset")
		quit(1)
		return
	app.call("toggle_recording")
	for i in range(40):
		if not bool(app.get("recording")):
			break
		await create_timer(0.1).timeout
	if bool(app.get("recording")):
		push_error("Native HTTP integration: recording did not stop")
		quit(1)
		return
	print("PASS: native authenticated connection, JPEG decoding, recording controls and no fabricated tracking")
	app.queue_free()
	await process_frame
	quit(0)
