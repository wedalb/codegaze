class_name GazeTracking
extends RefCounted

static func source_for(eye_supported: bool, eye_valid: bool, head_valid: bool, head_only: bool) -> Dictionary:
	if eye_supported and eye_valid and not head_only:
		return {"source": "eye", "valid": true, "fallbackReason": null}
	var reason := "head_forced" if head_only else ("eye_tracking_lost" if eye_supported else "eye_tracking_unavailable")
	return {"source": "head", "valid": head_valid, "fallbackReason": reason}

static func screen_hit(ray_origin: Vector3, ray_direction: Vector3, panel: Transform3D, size: Vector2) -> Variant:
	var local_origin := panel.affine_inverse() * ray_origin
	var local_direction := panel.basis.inverse() * ray_direction
	if local_direction.z >= -0.000001:
		return null
	var distance := -local_origin.z / local_direction.z
	if distance <= 0.0:
		return null
	var hit := local_origin + local_direction * distance
	var uv := Vector2(hit.x / size.x + 0.5, 0.5 - hit.y / size.y)
	if uv.x < 0.0 or uv.x >= 1.0 or uv.y < 0.0 or uv.y >= 1.0:
		return null
	return uv
