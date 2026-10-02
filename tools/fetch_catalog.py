"""Copy the curated public-domain exercises and their photos into the app."""

import json
import urllib.request
from pathlib import Path

ROOT = Path(r"C:\Users\dvirs\Projects\workout")
SOURCE = Path(
    r"C:\Users\dvirs\.grok\sessions\C%3A%5CUsers%5Cdvirs\01a0eeb6-43c7-73f2-a3b9-71b991d174f3\web_fetch\1.json"
)
ASSETS = ROOT / "app" / "src" / "main" / "assets"
IDS = [
    "Bodyweight_Squat",
    "Dumbbell_Squat",
    "Plie_Dumbbell_Squat",
    "Butt_Lift_Bridge",
    "Single_Leg_Glute_Bridge",
    "Stiff-Legged_Dumbbell_Deadlift",
    "Superman",
    "Dumbbell_Rear_Lunge",
    "Dumbbell_Lunges",
    "Split_Squat_with_Dumbbells",
    "Dumbbell_Step_Ups",
    "Incline_Push-Up",
    "Pushups",
    "Push-Ups_With_Feet_Elevated",
    "Dumbbell_Floor_Press",
    "Dumbbell_Bench_Press",
    "One-Arm_Dumbbell_Row",
    "Bent_Over_Two-Dumbbell_Row",
    "Bent_Over_Two-Dumbbell_Row_With_Palms_In",
    "Reverse_Flyes",
    "Standing_Dumbbell_Press",
    "Dumbbell_Shoulder_Press",
    "Standing_Palms-In_Dumbbell_Press",
    "See-Saw_Press_Alternating_Side_Press",
    "Dead_Bug",
    "Crunches",
    "Plank",
    "Side_Bridge",
    "Reverse_Crunch",
    "Dumbbell_Bicep_Curl",
    "Hammer_Curls",
    "Seated_Dumbbell_Curl",
    "Concentration_Curls",
    "Standing_Dumbbell_Calf_Raise",
    "Side_Lateral_Raise",
]

BASE = "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/"


def main() -> None:
    exercises = json.loads(SOURCE.read_text(encoding="utf-8"))
    by_id = {item["id"]: item for item in exercises}
    catalog = []
    for exercise_id in IDS:
        item = by_id[exercise_id]
        images = item["images"][:2]
        local_images = []
        for image in images:
            target = ASSETS / "exercises" / image
            target.parent.mkdir(parents=True, exist_ok=True)
            if not target.exists() or target.stat().st_size == 0:
                url = BASE + image.replace(" ", "%20")
                print("get", url)
                urllib.request.urlretrieve(url, target)
            local_images.append("exercises/" + image.replace("\\", "/"))
        catalog.append(
            {
                "id": exercise_id,
                "name": item["name"],
                "instructions": item["instructions"],
                "primaryMuscles": item["primaryMuscles"],
                "images": local_images,
            }
        )
    out = ASSETS / "catalog.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(catalog, indent=2), encoding="utf-8")
    print("wrote", out, "exercises", len(catalog))


if __name__ == "__main__":
    main()
