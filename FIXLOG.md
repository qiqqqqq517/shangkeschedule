# 修复日志 · FIXLOG（只追加不删除）

## 2026-10-01 · 第1轮 · R1-001a/b/c（!! 去除 3处，已验证 v4.64.6/401）
- R1-001a `shared/.../notification/plan/MorningAlarmPlan.kt:68`：`.filter{date} .minByOrNull{parseTime!!}` → `.filter{date}.mapNotNull{c->parseTime?.let{c to it}}.minByOrNull{second}?.first`。理由：comparator内!!坏味道；行为不变（parse失败此前崩溃→现跳过该天，71行已return@mapNotNull null）。验证：三端全过（sharedJvm/desktop/androidDebug），版本已迭代v4.64.6/401。
- R1-001b `shared/.../ui/settings/conversion/CourseTableConversionDialogs.kt:60`：`?: find{15}!!` → `?: find{15} ?: first()`。理由：去!!；行为不变（15恒存在，first()永不可达）。
- R1-001c `shared/.../ui/glass/GlassDecorations.kt:92`：`path!!.rewind()/addRoundRect/clipPath` → `val p = path ?: Path()` 复用。理由：绘制热路径不变式显性化；行为不变（调用点188/458/468恒传非null）。返工两次：`roundRect.bounds`/`.rect` 在本版Compose均无此属性，改与调用点同构的 `?: Path()`（零新API）后 `compileKotlinJvm` 通过。
- 下批预告：ScheduleGrid×6局部val化、DynamicIsland复用已解析值、println 6处、`"08:00"`/`640.dp`提常量、日志隐私脱敏。

## 2026-10-01 · 第1轮 · R1-026（TextImport 4处用户可见裸中文入资源，已验证 v4.64.7/402）
- `TextImportViewModel.kt:102` 旧版xls提示 → 新增 `tivm_error_old_xls`（简/英/繁三语）+ `getString`；`:114` 编码提示 → 新增 `tivm_error_not_utf8_guidance`（保留完整指引文案，不降级为短版）；`:121` 解析失败 → 复用 `tivm_import_failed_fmt` 带参（同文件170/173行已有此用法）；`:139` 空文件 → 复用 `tivm_error_empty_file`，空检查移入 `viewModelScope.launch` 首行（调用方 `JsonFileImportScreen.kt:204` onError仅送Toast，同步改异步无影响）。
- 英文/繁文案为新增翻译（EN/TW此前对这三条是中文裸串，属i18n修复）。验证：三端全过，版本已迭代v4.64.7/402。

## 2026-10-01 · 第1轮 · R1-001d/R1-017b（DynamicIsland去!!+删SAMPLE隐私日志，已验证 v4.64.8/403）
- R1-001d `androidApp/.../service/DynamicIslandService.kt:349-356,381-386`：两处 `firstOrNull{parse} + !!重解析` → `mapNotNull{Triple/pair} + firstOrNull{谓词} + 解构`，零 `!!` 且每课程少一次重复解析。语义等价（谓词条件逐字搬运，确定性纯函数）。验证：`desktopApp:compileKotlin` + `androidApp:assembleDebug` 全过。
- R1-017b `shared/.../androidMain/.../WebViewRequestInterceptor.kt:556-560`：删 `SAMPLE:` 原文前200字符日志行及 `sample` 变量，保留长度+hex诊断行。零行为变化。验证：同上（androidMain随assembleDebug编译）。

## 2026-10-01 · 第1轮 · R1-006/R1-017c（日志卫生：6处println→AppLog+桥接上报去stack，已验证 v4.64.10/405）
- R1-006 `GlassEffectScope.kt:200,210`、`GlassPlatform.android.kt:72,92,124`、`ApiDateImporter.kt:78`：`println` → `AppLog.w`（门面仅w/e两档；210/124把异常对象进 `throwable` 参，留痕更完整；两玻璃文件补 `tool.AppLog` 导入，ApiDateImporter沿用已导入）。输出通道 stdout→Logcat，文案逐字不变。
- R1-017c `WebBridgeHandler.kt:516-519`：上报日志去 `stack` 段（kind+message保留，message已是用户可见文案）。验证：三端一次全过。

## 2026-10-01 · 第1轮 · R1-001e（ScheduleGrid快照局部，已验证 v4.64.9/404）
- R1-001e `shared/.../ui/schedule/components/ScheduleGrid.kt:114-145`：外层改判快照局部 `expandedSnapshot`（6处 `state.expandedItem!!`→直读），moveIntent分支改判 `moveIntentSnapshot`（2处 `!!`→smart-cast直读）。排除过 `?: return`（会跳过整网格绘制）。语义等价（同帧主线程快照一致，仅拖拽提示计算用）。验证：三端全过。

## 2026-10-01 · 第1轮 · R1-027a（回退"08:00"×6收归常量，已验证 v4.64.11/406）
- `tool/TimeTextUtils.kt` 新增 `DEFAULT_CUSTOM_START_TIME`；替换 `WeeklyScheduleViewModel.kt:789`、`CourseDetailBottomSheet.kt:776`、`AddEditCourseScreen.kt:367`、`AddEditCourseViewModel.kt:42,179,307`（3文件补导入，Weekly沿用已有导入）。值逐字不变。排除 `CourseTableRepository.kt:654`（时刻模板固定数据，非回退语义，改则误导）。验证：三端一次全过，版本已迭代v4.64.11/406。

## 2026-10-01 · 第1轮 · R1-010（ActivityManager硬转as?，已验证 v4.64.12/407）
- `GlassPlatform.android.kt:100`：`as ActivityManager` → `as? … ?: return@runCatching false`。等价（原失败走runCatching→false，现显式return false，均在runCatching内）。验证：`assembleDebug` 通过。
- 文案R1-013核验（只读）：`…`:5 vs `...`:3（省略号以单字符为多数约定）；`！`共7处（245非孤例，改属风格 judgment，转待批）；112教师：vs171老师: 双不一致但无多数方向，转待批（候选：统一教师：/统一老师:）；305/306半角冒号待查全角 prevalence 后定。

## 2026-10-01 · 第2轮 · R2-002/R1-027b（import排序修正+640.dp×11收归常量，已验证 v4.64.14/409）
- R2-002 `AddEditCourseViewModel.kt`：tool 导入移至 navigation 之后（字母序）。验证：三端全过，版本已迭代v4.64.13/408。
- R1-027b `ui/theme/AppStyle.kt` 新增顶层 `SettingsPageMaxWidth = 640.dp`（附不进 AppSpacingTokens 的理由：与主题无关）；替换 8 文件 11 处（TimeSlotManagement:318、Settings:180/187/742、Semester:140、Backup:212、ManageCourseTables:251、Profile:213/401/414、FileImportHub:80；7文件补导入）。复扫 `640.dp` 仅剩定义+注释。验证：三端一次全过，版本已迭代v4.64.14/409。

## 2026-10-01 · 第3轮 · R1-012a/b（防重：Agenda提交锁+SchoolItem节流，已验证 v4.64.15/410）
- R1-012a `AgendaScreen.kt:1665-1687`：创建按钮加 `submitted` 锁（点击即锁 + `enabled=!submitted`；sheet 条件合成，重开自动复位）。根因：关闭前双击穿透调两次 addEvent 落两条。
- R1-012b `SchoolSelectionListScreen.kt:435-448`：`SchoolItem` 加 500ms 节流（item 作用域 lastClickMs；返工一次：kotlinx.datetime.Clock 无 System，改 kotlin.time.Clock 与 AppSettingsRepository 同源）。根因：快速双击推两页 AdapterSelection。
- 同批定案不修：Today toggle（`setDone` 绝对值写入，幂等，P3记录）、Weekly 手势（onDragEnd 单发 + `isNoPositionChange` 守卫，误报）。验证：三端全过，版本已迭代v4.64.15/410。

## 2026-10-01 · 第4轮 · R4-001/R4-002（引擎零时长守卫+ICS周下界，已验证 v4.64.16/411）
- R4-001 `ReminderEngine.kt:57-67`：`effectiveCourses` 追加结束>开始过滤（无结束时间保持旧行为；4 调用方全为调度路径，无展示消费）。根因：零时长/倒挂课照进提醒/模式/灵动岛排程（早闹钟侧已有守卫）。验证：编译+hostTest+三端全过。
- R4-002 `IcsExportTool.kt:89`：`weekIndex > total` → `!in 1..total`（与 Widget/Today 双边对齐；原 week≤0 落盘过去日期）。验证：同上。
- R4-003 format/parse 往返 ✅闭环（未动手写单测）：`DATE_FORMATTER`（AppSettingsRepository.kt:58-64）用 `year/char/monthNumber/char/day` 即文档级 ISO 重构式，month/day 默认 ZERO 补零，输出恒 `yyyy-MM-dd`，与读侧 `LocalDate.parse` ISO 兼容；跨年不断裂（epoch 连续）。
- 第5轮结论：Today 30+裸sp、Weekly reduceMotion 0分支等 9 项全部转待批（视觉基线/需真机，见 REVIEW 待批清单补遗）。
