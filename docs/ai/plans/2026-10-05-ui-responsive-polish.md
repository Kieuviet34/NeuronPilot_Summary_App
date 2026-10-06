# Responsive UI polish plan — Meeting Notes tablet

Plan-only audit of the current UI_Hoang worktree. No source or resource files were changed while preparing this plan.

## 1. Core problem, assumptions, and risks

**Core problem:** make the six existing Kotlin Activity/XML screens readable, state-correct, and free of clipping at 1024×600 landscape when the app window is either about 1024×600dp (mdpi) or 683×400dp (hdpi), without changing the recording or AI pipeline.

Assumptions:

- Keep Kotlin Activities, XML Views, the current landscape/full-screen kiosk presentation, current navigation, and the existing Room/pipeline contracts.
- Treat the current uncommitted UI work as the baseline; refine those files rather than reverting or recreating them.
- Use offline, app-bundled fonts and existing AndroidX/Material Components. No new runtime service, network call, or UI framework.
- Make the default/fallback resources work at 683×400dp. At this size the smallest side is about 400dp, so a sw600dp resource does not apply even though the landscape width is 683dp.
- No screenshots are checked in. Findings below are from current XML/Kotlin and dimensional arithmetic; final clipping confirmation requires previews/device validation.

Unknowns: the G720’s actual density and usable window bounds/system bars; its configured font scale; and real-world title, transcript, and action-item lengths.

Risks and boundaries:

- The worktree already has extensive unstaged/untracked UI, data, audio, AI, build, and manifest changes, including I2SAudioRecorder.kt, MeetingAiPipeline.kt, and NeuroPilotLlmBridge.kt. Do not reset or overwrite that work. This plan’s implementation scope is presentation resources and com.bhs.meetingnotes UI only; never touch native/JNI files or the protected MediaTek package.
- The current custom drawables and WaveformView use a mixed palette. Day/night colors must be checked across both XML drawables and the custom view.
- A change to Activity lifecycle/navigation can affect an active recording or pipeline. Keep existing service, recorder, and pipeline behavior intact; validate those screen transitions manually.

## 2. Current-state audit

| Screen | Defects and evidence in the current files |
|---|---|
| **1. Meeting list** | activity_meeting_list.xml:14 fixes the left rail at 240dp. In the hdpi profile that leaves roughly 442dp for the main panel. Its header uses a 72dp row (275) containing title, date filter, and a fixed 280dp search field (335), so the row cannot fit at that width. item_meeting_card.xml:6 gives every card a 230dp minimum height, limiting how many complete cards appear in the 400dp-tall profile. MeetingNotesActivity.kt:237–252 observes Room flows without a visible loading/error state. applyFilters() (369–440) has one generic non-trash empty message (433–435) for no data, no search match, and no date/language match; offer a clear-filter state for the latter cases. Most labels/icons are inline in XML/Kotlin; screen icons such as the clickable actions have no contentDescription. |
| **2. Multi-segment recording** | activity_meeting_record.xml:14 reserves 64dp for the header; the timer ring is 180dp (145–149), while control captions are 12sp (252, 291, 328, 365, 402) and summary labels are 11sp (490, 518, 551, 575). item_record_segment.xml:57 gives its clickable replay control only 40dp height. The timer itself is fed from the recorder tick callback (MeetingRecordActivity.kt:349–365); the three manually painted control states live in updateControlButtonsState() (474–568), so acceptance must cover recording, paused, saved, and resumed states. onDestroy() stops the service and recorder (678–684); preserve that existing audio behavior and resolve any lifecycle expectation separately from visual work. |
| **3. Segment selection** | The body is a 1.4:1 split (activity_meeting_select_segments.xml:78–155). The right panel stacks statistics, three estimates, and a tip in a plain LinearLayout (147–414), not a scroll container; that content exceeds the available inner height in the 400dp profile and can cut off the tip/last rows. The RecyclerView has no sibling empty-state view (137–143). updateStatisticsAndEstimates() treats 0 selected == 0 total as “all selected” and labels the toggle “Bỏ chọn tất cả” (MeetingSelectSegmentsActivity.kt:189–196). Bulk deselection also writes a synthetic break note into blank item.note values (135–146), then persists it with selection changes (264–271); show the exclusion label from selection state and preserve user notes. The select-all TextView has wrap_content height and only 5dp vertical padding (layout:123–133); item_select_segment.xml uses 36dp checkbox and 44dp preview targets. |
| **4. AI processing** | activity_meeting_processing.xml uses a 64dp header and 2-column timeline/preview area (77–98, 433–550); its step/status copy includes 11–13sp text (for example 140, 151, 169, 462–475). The scroll areas prevent long transcript clipping, but the compact viewport leaves little readable preview height. MeetingProcessingActivity.kt:68–74 puts an invalid-ID message into the transcript preview rather than a screen-level error. startPipeline() is called from onCreate() for every valid creation (61–74, 129–149); a recreated Activity can start the same work again. A null result has no error branch (138–148), and failure has no recovery/navigation action. item_pipeline_step.xml has an 11sp badge (56–59), but repository search finds no use of this layout; do not refactor this unused file unless it is wired in later. |
| **5. Meeting result** | The content already has independent ScrollViews for summary/actions/transcript (activity_meeting_result.xml:235–351), but the 74dp header and two 54dp actions (14, 58–85) compete for width after font scaling. selectTab() changes only text colors and visibility (MeetingResultActivity.kt:123–136), not the selected/accessibility state. loadMeetingData() renders only when the row exists (138–167) and leaves the screen without a loading/error/missing-meeting state otherwise. Empty actions have a message (212–224); summary/transcript have only inline empty copy, not a common state treatment. |
| **6. Settings** | activity_meeting_settings.xml uses a 240dp rail plus 20dp outer padding (59–69). The five 56dp menu rows and their 10dp gaps need 320dp before the hardware information box (71–209, 217–240), but the 400dp profile leaves about 296dp below the header after padding; the non-scrollable rail can clip its final row/info box. The content itself is scrollable (252–256). Several labels are 12–14sp (51, 228–239); MeetingSettingsActivity.kt builds the alias dialog with pixel padding and inline sizes/copy (222–237). Glossary stats begin as “Đang tải dữ liệu từ điển...” (layout:410–416), but loadGlossaryStats() has no error state (Activity:207–215), so a failed local read can look like endless loading. The glossary TextInputEditText is in part_settings_content.xml:280. adjustResize is currently set at application level (AndroidManifest.xml:21), with no Settings-specific override (67); verify the keyboard behavior before changing it. Android permits this attribute on the application or an activity element ([manifest reference](https://developer.android.com/guide/topics/manifest/activity-element)). |
| **Shared presentation** | values/dimens.xml defines caption/body/title as 12/14/16sp; smaller 11–13sp labels remain in screen/item XML. values/colors.xml has overlapping legacy/new token names, styles.xml uses Theme.MaterialComponents.Light.NoActionBar, and there is no values-night or res/font directory. WaveformView.kt:54–65 hardcodes a four-color gradient. contentDescription exists on a few settings/list items, but is absent from most screen back/action icons. Multiple visible strings and dp/sp values remain inline. values-sw600dp currently overrides only screen_padding. |

## 3. Design system and responsive strategy

### Typography

Use the bundled **Noto Sans** family for Vietnamese diacritics, with the Android system sans-serif as fallback. Bundle local TTF/font-family resources; include the upstream SIL Open Font License 1.1 text with the assets. Google Fonts metadata identifies Noto Sans as OFL and lists the Vietnamese subset: [Noto Sans metadata](https://github.com/google/fonts/blob/main/ofl/notosans/METADATA.pb). Runtime font loading must stay entirely local.

| Role | Size | Weight / use |
|---|---:|---|
| Timer/display | 40sp | Semibold/bold; tabular-looking digits; timer may shrink only to 36sp in the compact token set |
| Screen title | 24sp | Semibold |
| Section/card title | 18sp | Semibold |
| Body | 16sp | Regular, 24sp line height |
| Metadata/caption | 14sp | Regular/medium, 20sp line height; do not use 11–12sp for user-facing text |
| Button | 16sp | Medium/semibold |

Keep all text in sp and allow system font scaling. At larger scales content must scroll or wrap; do not autoshrink below the role minimum to make a fixed panel appear to fit.

### Tokens and ownership

- **Spacing:** 4, 8, 12, 16, 24, 32dp. **Touch:** 48dp minimum hit area; primary actions 56dp where they fit.
- **Radius:** control 8dp, card 12dp, large panel 16dp, pill 100dp. **Elevation:** flat 0dp, card 2dp, raised action 4dp.
- Store type/spacing/touch/radius/elevation tokens in values/dimens.xml; keep base values compact. Expand suitable tokens in the existing values-sw600dp/dimens.xml and add values-h480dp/dimens.xml for taller windows. Apply radius tokens only to existing screen/card background drawables.
- Define semantic light tokens in values/colors.xml and matching night variants in new values-night/colors.xml. Suggested pairs: background #F5F7FA / #111827; surface #FFFFFF / #1F2937; text #111827 / #F3F4F6; secondary text #475569 / #CBD5E1; primary #1D4ED8 / #93C5FD; divider #CBD5E1 / #475569; success #166534 / #86EFAC; warning #92400E / #FCD34D; error #991B1B / #FCA5A5. Map existing color names to semantic tokens during the change so referenced layouts keep resolving. Check normal-text contrast at 4.5:1 or better in both modes. Pair status colors with text/icon, never color alone.
- Use values/styles.xml with the existing Material Components DayNight NoActionBar theme; do not migrate to Compose/Material3 or add a dependency. Theme the WaveformView gradient from color resources/theme values rather than literals.
- Put the Noto Sans family XML/TTF under res/font and its OFL text under docs/licenses. Keep visible copy in the existing values/strings*.xml files: strings_home.xml, strings_recording.xml, strings_segments.xml, strings_processing.xml, strings_result.xml, strings_settings.xml, and strings.xml.

### Responsive rules

Android’s View guidance defines sw in dp and provides layout-sw600dp variants; the app window may be smaller than the physical panel after system UI/insets: [responsive/adaptive layouts with Views](https://developer.android.com/develop/ui/views/layout/responsive-adaptive-design-with-views).

- Treat default res/layout as the compact fallback for the hdpi case: about 683dp wide, 400dp high, smallest-width about 400dp. Do not depend on sw600dp resources for this case.
- Keep/extend values-sw600dp for the mdpi-sized window (about 1024×600dp, sw about 600dp). Add layout-sw600dp alternatives only for the list/settings navigation shells that need an expanded labeled rail; their default layouts use an approximately 72dp icon rail, while the sw600dp variants use an approximately 200dp labeled rail.
- Use h480dp dimension overrides for extra vertical breathing room. Base dimensions and all four non-shell screens still need to work at h400dp. Keep two-pane layouts weighted and their long-content panes independently scrollable.
- Preserve landscape/full-screen behavior. The edge-to-edge skill’s code examples require Compose, which this app does not use; apply only its relevant rule in Views: have one inset owner, keep system bars from obscuring critical controls, and verify the IME. Test the glossary input with the keyboard open; retain the current application-level adjustResize if it works, and add an Activity-level override only if device validation shows it is needed. Do not bump compile/target SDK as part of this UI plan.

## 4. Target layouts and exact screen files

Wireframes are approximate arrangements on the 1024×600px landscape canvas. At hdpi, use the compact fallback noted above.

### 1. Meeting list

~~~text
┌──────── rail ───────┬────────────────── Meeting Notes ─────── Search ── ⚙ ┐
│ All / VI / EN /     │ Date filter chips                                      │
│ Trash               ├───────────────────────────────────────────────────────┤
│                     │ Meeting card: title + state/language + date/duration   │
│ [+ New meeting]     │ 2-line summary + transcript/action counts               │
│                     │ Meeting card …                         [ + ]            │
└─────────────────────┴───────────────────────────────────────────────────────┘
~~~

Use a 72dp icon rail in the compact fallback and a labeled rail at sw600dp. Let search consume remaining width, put date filters on a wrap/scrolling chip row if needed, and keep the list as the only vertically scrolling region. Give no-data, no-filter-match, loading, and Room-error states distinct copy/actions.

Files: edit layout/activity_meeting_list.xml, layout/item_meeting_card.xml, MeetingNotesActivity.kt, adapter/MeetingAdapter.kt, values/strings_home.xml; create layout-sw600dp/activity_meeting_list.xml.

### 2. Recording

~~~text
┌──────────────────────────── header: back · meeting · language · finish ────┐
│ Current segment · REC / PAUSED            │ Saved segments (scroll)        │
│             40sp timer                    │ 01 name · duration · size       │
│ Waveform, state text                      │ 02 name · duration · size       │
│ Pause/resume   Stop   New   Mark           │ Duration · size summary          │
│                                            │ [Finish and select segments]    │
└────────────────────────────────────────────┴────────────────────────────────┘
~~~

Keep the timer and state prominent; use a smaller timer ring in the compact token set. Arrange five controls as 48dp-or-larger hit targets (wrap labels to two rows rather than shrinking them). Keep saved items scrollable and retain the bottom action. Timer must advance on record/resume, freeze on pause, and reset only when a new segment starts.

Files: edit layout/activity_meeting_record.xml, layout/item_record_segment.xml, MeetingRecordActivity.kt, adapter/RecordSegmentAdapter.kt, util/WaveformView.kt, values/strings_recording.xml. Preserve I2SAudioRecorder and AudioRecordingService semantics.

### 3. Segment selection

~~~text
┌──────────────────────── header: back · meeting · selected count · [Process] ┐
│ Selected segments (scroll, about 55%) │ Selected totals (scroll, about 45%) │
│ [✓] segment 01 · preview               │ duration / size / skipped count     │
│ [ ] segment 02 · preview               │ estimate by pipeline step            │
│ …                                       │ tip                                  │
└────────────────────────────────────────┴─────────────────────────────────────┘
~~~

Make the right panel independently scrollable at h400dp. Provide a true no-segments state; when there are zero rows, disable/hide select-all and process. Derive “break/excluded” labels from isSelected; never overwrite a user note to show selection status.

Files: edit layout/activity_meeting_select_segments.xml, layout/item_select_segment.xml, MeetingSelectSegmentsActivity.kt, adapter/SelectSegmentAdapter.kt, values/strings_segments.xml.

### 4. Processing

~~~text
┌──────────────────────────── header: meeting · language · progress ──────────┐
│ 4-step timeline (scroll, ~42%) │ Raw transcript preview (scroll)            │
│ step/status/progress             ├───────────────────────────────────────────┤
│                                  │ Corrected transcript preview (scroll)     │
├──────────────────────────────────┴───────────────────────────────────────────┤
│ Current step · percent · elapsed/remaining estimate                          │
└──────────────────────────────────────────────────────────────────────────────┘
~~~

Retain the current pipeline callback/content contract. Render invalid meeting ID, missing meeting, null/failure result as visible error states with a safe return action. Ensure Activity recreation does not start duplicate work; do not alter MeetingAiPipeline behavior. Keep preview text scrollable and status copy at readable sizes.

Files: edit layout/activity_meeting_processing.xml, MeetingProcessingActivity.kt, values/strings_processing.xml. item_pipeline_step.xml is currently unused; leave it untouched unless it becomes the actual row layout.

### 5. Result

~~~text
┌──────────────────────── header: back · meeting title · TTS · export ────────┐
│ duration · words · language · models                                        │
├──────────────────────────── tabs ──────────────────────────────────────────┤
│ summary/actions/transcript (scroll, ~62%) │ Action items (scroll, ~38%)     │
│                                          │ title · owner · deadline          │
│                                          │ …                                  │
└──────────────────────────────────────────┴──────────────────────────────────┘
~~~

At compact width, allow header actions to wrap into a second row rather than overlap the title. Keep three tabs and data unchanged; expose selected tab state to accessibility. Show loading, empty, and missing-meeting states without leaving sample/static content visible.

Files: edit layout/activity_meeting_result.xml, MeetingResultActivity.kt, layout/item_action_detail.xml, values/strings_result.xml, values/strings.xml.

### 6. Settings

~~~text
┌──────────────────────────── header: back · Settings ────────────────────────┐
│ rail (72dp compact / 200dp wide) │ Scrollable settings sections             │
│ ASR · LLM · Hardware · Storage   │ ASR language cards                       │
│ Export                           │ LLM / glossary / audio / storage / export  │
│                                  │ Keyboard keeps focused glossary field      │
└──────────────────────────────────┴──────────────────────────────────────────┘
~~~

Use a compact icon rail at sw400dp; move the nonessential hardware facts into a scrollable section instead of leaving them in the fixed rail. Keep the content column vertically scrollable and keep all sliders/switches/inputs usable at 48dp touch size. Selected rail item needs a selected accessibility state. Add glossary loading/error feedback and activity-level IME resize.

Files: edit layout/activity_meeting_settings.xml, layout/part_settings_content.xml, MeetingSettingsActivity.kt, values/strings_settings.xml, AndroidManifest.xml; create layout-sw600dp/activity_meeting_settings.xml.

## 5. Ordered coding tasks and acceptance gates

Run each task on top of the current worktree, never by reverting its existing UI diff. Each task should compile before the next begins.

1. **Shared type/color/theme foundation (medium risk).** Files: values/dimens.xml, values/colors.xml, values/styles.xml, values-sw600dp/dimens.xml; add values-night/colors.xml, values-h480dp/dimens.xml, res/font/NotoSans family/TTF assets, and docs/licenses/OFL-Noto-Sans.txt. Acceptance: semantic light/dark tokens resolve, Noto Sans is local/offline, type/spacing tokens replace touched literals, no new dependency. Verify: ./gradlew assembleDebug, ./gradlew lintDebug; manually switch day/night and font scale 1.0/1.5.
2. **List/settings compact and expanded shells (high clipping risk).** Files: activity_meeting_list.xml, activity_meeting_settings.xml, new layout-sw600dp/activity_meeting_list.xml and layout-sw600dp/activity_meeting_settings.xml; consume the h480dp tokens from Task 1. Acceptance: compact fallback fits 683×400dp; wide variants fit 1024×600dp; search/header does not overflow; settings rail entries remain reachable. Verify both layout previews, ./gradlew assembleDebug and ./gradlew lintDebug.
3. **Selection screen and state correctness (high behavior risk).** Files: activity_meeting_select_segments.xml, item_select_segment.xml, MeetingSelectSegmentsActivity.kt, SelectSegmentAdapter.kt, strings_segments.xml, focused tests under app/src/test or app/src/androidTest. Acceptance: empty/all/partial selection, selected count/estimate/CTA, preview state, and preserved user notes agree; right pane scrolls at h400dp. Verify focused cases, ./gradlew testDebugUnitTest, assembleDebug, and manual check/clear/process/no-segment paths.
4. **Recording screen controls (high safety risk).** Files: activity_meeting_record.xml, item_record_segment.xml, MeetingRecordActivity.kt, RecordSegmentAdapter.kt, WaveformView.kt, strings_recording.xml. Acceptance: no tiny captions or sub-48dp hit areas; timer/state labels match record/pause/resume/save; dark gradient is visible; no recorder/service semantics change. Verify ./gradlew assembleDebug, ./gradlew testDebugUnitTest, plus manual timed record → pause → resume → save → next segment → finish. Do not change I2SAudioRecorder.kt or AudioRecordingService.kt.
5. **Processing state/error surface (high re-entry risk).** Files: activity_meeting_processing.xml, MeetingProcessingActivity.kt, strings_processing.xml, focused UI-state tests if an existing harness supports them. Acceptance: valid run starts once per processing session; invalid/missing ID and null/failure result show actionable UI; progress/previews remain scrollable. Verify ./gradlew assembleDebug, ./gradlew testDebugUnitTest, manual missing-ID/success/failure/recreation; no edits to MeetingAiPipeline.kt.
6. **Result screen and tabs (medium behavior risk).** Files: activity_meeting_result.xml, MeetingResultActivity.kt, item_action_detail.xml, strings_result.xml, strings.xml. Acceptance: tabs expose selected state; long transcript/actions scroll; missing meeting, empty summary, empty actions, and empty transcript are understandable; header actions do not overlap at compact width/font scale. Verify ./gradlew assembleDebug, ./gradlew testDebugUnitTest, manual all tabs and TTS/export entry states.
7. **Cross-screen copy, touch, accessibility, and IME sweep (medium risk).** Files: the six Activity layouts/items above, existing strings*.xml, MeetingNotesActivity.kt, MeetingRecordActivity.kt, MeetingSelectSegmentsActivity.kt, MeetingProcessingActivity.kt, MeetingResultActivity.kt, MeetingSettingsActivity.kt, WaveformView.kt; AndroidManifest.xml only if IME validation requires an Activity-level override. Acceptance: visible copy is in strings resources; interactive icons have localized contentDescription; icon-only controls have 48dp hit areas; light/dark semantic colors are consistent; Settings IME does not cover the active field; all six screens tolerate 1.0, 1.5, and 2.0 font scales by wrapping/scrolling. Verify ./gradlew assembleDebug, ./gradlew lintDebug, ./gradlew testDebugUnitTest, then Accessibility Scanner/manual TalkBack pass.
8. **Two-density device/layout sign-off (release gate).** Files: no new source files; fix only failures found in the owned UI files above. Acceptance: no clipped controls/text or unreachable actions at both profiles and dark/light/font-scale checks. Verify layout preview at 1024×600px @160dpi and @240dpi; on device/emulator record adb shell wm size, adb shell wm density, and system font scale; use aapt2 dump resources app/build/outputs/apk/debug/app-debug.apk to confirm packaged sw/h variants; inspect runtime selection with Layout Inspector; run ./gradlew assembleDebug, ./gradlew lintDebug, ./gradlew testDebugUnitTest. Reset any emulator wm overrides after the check.

## 5b. Review rulings (Claude, 2026-10-05) — override sections 3 and 5 where they conflict

1. **Font:** mặc định dùng font hệ thống `sans-serif` / `sans-serif-medium` (Roboto, hỗ trợ đủ dấu tiếng Việt, 0 byte thêm). KHÔNG bundle Noto Sans trong Task 1: máy dev từng không tải được tài nguyên qua SSL (xem ledger cũ) và font phải nằm trong repo để build offline. Chỉ thêm `res/font` nếu TTF đã có sẵn trong repo; nếu không, bỏ qua và ghi follow-up.
2. **Hai bộ cỡ chữ** (không dùng một bộ 16sp cho cả hai mật độ):
   | Vai trò | Compact (default, ~683x400dp) | `sw600dp` (~1024x600dp) |
   |---|---:|---:|
   | Timer | 36sp | 40sp |
   | Title màn hình | 20sp | 24sp |
   | Section/card title | 16sp | 18sp |
   | Body | 14sp | 16sp |
   | Caption | 12sp (chỉ phụ chú; không dùng cho nội dung quan trọng) | 14sp |
   | Button | 14sp | 16sp |
   Đặt trong `values/dimens.xml` (compact) và `values-sw600dp/dimens.xml` (rộng); layout chỉ tham chiếu `@dimen/text_*`.
3. **Task 0 (dọn dẹp, làm trước):** thư mục `UI_new/`, `docs/superpowers/`, `test*.wav`, `docs/references/imported_ai_application_xml/` mô tả dự án Java khác / app khác → đã nén ở `D:\DMHoang\Project_GitHub\_NeuronPilot_archive_2026-10-05.zip`; xoá sau khi chủ dự án xác nhận. Không xoá layout/drawable nào trong `app/` (dự án không bật ViewBinding, đếm tham chiếu không đáng tin; layout cũ của demo aibox bị cấm đụng).
4. **Thứ tự code:** 1 → 2 → 7a (strings/contentDescription từng màn) → 3 → 4 → 5 → 6 → 8. Mỗi task phải `assembleDebug` pass trước khi sang task kế. Không sửa `I2SAudioRecorder.kt`, `AudioRecordingService.kt`, `MeetingAiPipeline.kt`, `NeuroPilotLlmBridge.kt`, `cpp/`, `jniLibs/`.
5. **Xác minh thật:** không có thiết bị/emulator → chỉ khẳng định "build + lint pass"; clipping ở 1024x600 phải do chủ dự án chụp ảnh trên G720 và ghi `wm size`/`wm density`.

## 6. Owner questions

No blocking question before implementation. Record the actual G720 wm size/density, usable system-bar area, and font scale during device validation; keep the current fullscreen landscape behavior as the default assumption.
