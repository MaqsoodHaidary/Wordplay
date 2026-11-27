# MKM English

Watch mp4/mkv with .srt/.vtt subtitles, tap any subtitle word for its Persian meaning (offline,
from app/src/main/assets/fa_en.db). "I know" -> green. "I don't know" -> orange + Review list.
Home screen shows words learned, words to review, and words left in the current video.

Build an UNSIGNED apk:
  * GitHub: push this folder to a repo, Actions > "Build unsigned APK" > download the artifact.
  * Or Android Studio / `gradle assembleRelease`.
Output: app/build/outputs/apk/release/app-release-unsigned.apk

Sign it (use the SAME key as before to update the old app and keep saved words):
  apksigner sign --ks my.jks --out MKMEnglish.apk app-release-unsigned.apk
