# Word Player

Plays mp4/mkv, loads .srt/.vtt subtitles, tap the video to play/pause, tap a subtitle word
to see its Persian meaning (offline, from app/src/main/assets/fa_en.db).
"I know" -> word turns green. "I don't know" -> word turns orange and goes to Review.

Build an UNSIGNED apk:
  * GitHub: push this folder to a new repo, open Actions > "Build unsigned APK" > download artifact.
  * Or Android Studio: open folder, Build > Build APK(s) (release variant), or run `gradle assembleRelease`.
Output: app/build/outputs/apk/release/app-release-unsigned.apk

Sign it:
  apksigner sign --ks my.jks --out WordPlayer.apk app-release-unsigned.apk
