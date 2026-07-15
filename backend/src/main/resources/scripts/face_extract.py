#!/usr/bin/env python
# -*- coding: utf-8 -*-
import sys, json, base64

# Force redirect stdout to stderr for insightface noise
_sys_stdout = sys.stdout
sys.stdout = sys.stderr

try:
    import cv2
    import numpy as np
    from insightface.app import FaceAnalysis
    READY = True
except ImportError:
    READY = False

app = None

def get_app():
    global app
    if app is None:
        app = FaceAnalysis(name='buffalo_l', providers=['CPUExecutionProvider'])
        app.prepare(ctx_id=0)
    return app

def extract_embedding(image_b64):
    if not READY:
        return {"error": "insightface not installed"}
    img_bytes = base64.b64decode(image_b64)
    img_np = cv2.imdecode(np.frombuffer(img_bytes, np.uint8), cv2.IMREAD_COLOR)
    if img_np is None:
        return {"error": "Failed to decode image"}
    faces = get_app().get(img_np)
    if len(faces) == 0:
        return {"error": "No face detected", "embedding": None}
    largest = max(faces, key=lambda f: (f.bbox[2]-f.bbox[0])*(f.bbox[3]-f.bbox[1]))
    return {"embedding": largest.embedding.tolist()}

def compare_faces(image1_b64, image2_b64):
    r1, r2 = extract_embedding(image1_b64), extract_embedding(image2_b64)
    if "error" in r1 or "error" in r2:
        return {"error": "Face detection failed"}
    v1, v2 = np.array(r1["embedding"]), np.array(r2["embedding"])
    n1, n2 = np.linalg.norm(v1), np.linalg.norm(v2)
    sim = float(np.dot(v1, v2)/(n1*n2)) if n1>0 and n2>0 else 0.0
    return {"similarity": sim}

def main():
    try:
        data = json.loads(sys.stdin.read())
    except json.JSONDecodeError:
        sys.stdout = _sys_stdout
        print(json.dumps({"error": "Invalid JSON"}))
        return
    action = data.get("action", "extract")
    image = data.get("image", "")
    if action == "extract":
        result = extract_embedding(image)
    elif action == "compare":
        result = compare_faces(image, data.get("image2", ""))
    else:
        result = {"error": "Unknown action"}
    sys.stdout = _sys_stdout
    print(json.dumps(result))

if __name__ == "__main__":
    main()
