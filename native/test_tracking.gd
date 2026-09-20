extends SceneTree
const Tracking = preload("res://tracking.gd")
func _initialize() -> void:
	var panel := Transform3D(Basis.IDENTITY, Vector3(0, 0, -2))
	assert(Tracking.screen_hit(Vector3.ZERO, Vector3(0,0,-1), panel, Vector2(2,1)) == Vector2(0.5,0.5))
	assert(Tracking.screen_hit(Vector3.ZERO, Vector3(0,0,1), panel, Vector2(2,1)) == null)
	assert(Tracking.screen_hit(Vector3.ZERO, Vector3(8,0,-2), panel, Vector2(2,1)) == null)
	assert(Tracking.source_for(true,true,true,false).source == "eye")
	assert(Tracking.source_for(false,false,true,false).source == "head")
	assert(Tracking.source_for(true,false,true,false).fallbackReason == "eye_tracking_lost")
	assert(Tracking.source_for(true,true,true,true).source == "head")
	assert(not Tracking.source_for(false,false,false,false).valid)
	var map := load("res://openxr_action_map.tres") as OpenXRActionMap
	assert(map != null and map.get_action_set_count() == 1)
	assert(map.find_interaction_profile("/interaction_profiles/ext/eye_gaze_interaction") != null)
	print("PASS: native geometry, tracking fallback and eye action map")
	quit(0)
