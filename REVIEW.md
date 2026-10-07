# 全自动深度审查 · REVIEW（第 4 轮 · 2026-10-01 极端视角；批1–11已验证 v4.64.16/411；分支 fix/auto-review-r1-r2 待汇入）

> 断网窗口 03:00–03:20：该时段只做本地只读扫描与本地编译，不做任何网络操作（fetch/push/gh/release），不断轮、不退出。
> 方法标注：通读全文 / 模式搜索。每条结论带 文件:行号 + 证据原文。

## 模块清单（通读+glob，约 448 kt + 197 js + 55 py + website 6 文件）
- shared/commonMain 约200 kt：data(~50)/tool(13)/notification(7)/navigation(1)/ui(~120：schedule12/today2/agenda2/theme12/glass9/components14/settings40+/schoolselection10)
- shared/androidMain 23 kt，androidApp 约60–70 kt（widget23/service25/根3），desktopApp 8 kt，tools 约55 py，website 5 html+1 js，adapter-worker 1 js；`.adapter_private/` 不存在
- 核心屏：`ui/schedule/WeeklyScheduleScreen.kt:198` / `ui/today/TodayScheduleScreen.kt:196` / `ui/agenda/AgendaScreen.kt:225` / `ui/schedule/components/CourseDetailBottomSheet.kt:136` / VM×3 / 路由 `App.kt:419-422`

## 静态模式 14 条执行结论（模式搜索+人工研判）
| # | 目标 | 结论 | 证据代表 |
|---|---|---|---|
| 1 | `!!` | ❌ 27处（shared24+androidApp3），6处重点复核均为不可达误报但坏味道成立，已修3/余下4 | `MorningAlarmPlan.kt:68` / `ScheduleGrid.kt:119-139`×6 / `DynamicIslandService.kt:355` |
| 2 | 空catch | ✅ Kotlin真空块0；`catch→null/fallback`约20+属容错惯用法 | `TodayScheduleViewModel.kt:145` / JS `AHZYYGZ.js:79`（第三方，不碰） |
| 3 | TODO | ❌ 真标记仅1：`BackupRepository.kt:490` 样式迁移待实现 | 同左 |
| 4 | 硬编码颜色 | ❌ 散落约10处真问题（`AdvancedColorPicker.kt:223` 彩虹Brush、`AppBasicComponents.kt:425` 等）；集中palette定义豁免 | 见R1-004 |
| 5 | 硬编码文本 | ❌ UI裸中文残留主在VM层：`TextImportViewModel.kt:102,114,121,139` 用户可见（P0） | 见R1-026 |
| 6 | 魔法数字 | ❌ 10条（`delay(520)`/`30_000L`/`44.dp`/`20.sp` 等），命名正例 `TIME_COLUMN_WIDTH` | 见R1-028 |
| 7 | 睡眠延迟 | ✅ 生产delay多为正当定时/动画；`Thread.sleep`仅桌面探针；`WidgetUpdateHelper.kt:204` 待复核 | — |
| 8 | 打印残留 | ❌ 发布版需清6处：`ApiDateImporter.kt:78` + `GlassEffectScope.kt:200,210` + `GlassPlatform.android.kt:72,92,124` | 见R1-006 |
| 9 | 注释僵尸块 | ✅ 0块 | — |
| 10 | index作key | ✅ 0；抽查key全稳定（`TodayScheduleScreen.kt:886` 表ID+课程ID） | — |
| 11 | 未使用引用 | ✅ 无实锤（import块大是God文件前兆，`App.kt:3-98` 96 import） | — |
| 12 | 重复字面量≥3 | ❌ `"08:00"`×8（P1）、`widthIn(max=640.dp)`×6（P2）等5组 | 见R1-027 |
| 13 | Thread/GlobalScope/runBlocking | ✅ 无GlobalScope/裸Thread；`runBlocking` 1必需（WebView拦截）+2小阻塞 | `ThemeModeSync.android.kt:27` |
| 14 | `as`强转 | ❌ 1小：`GlassPlatform.android.kt:99 as ActivityManager` 建议as?；其余as?全带守卫✅ | — |

## 12 维度结论（轮1广度）
- A逻辑空安全：❌ R1-001（已修3，余ScheduleGrid×6/DynamicIsland×3下批）
- B并发周期：❌ R1-030 裸Scope1（P1待批）；其余launch/register成对✅
- C边界输入：✅空态齐；❌ R1-031周次差一（P2待验证）、R1-035 maxLines无上限（P3待批）、CourseDao TOCTOU（P2待批）
- D状态反馈：❌ R1-011三屏缺Error态（P1待批）；❌ R1-012防重4处（P1下轮）；❌ R1-013文案10条（P2下批）
- E适配可达：❌ 硬编码色（P2记录）、可达性3处（P2下轮）
- F动效：✅ 集中收归MotionTokens、reduceMotion 35命中；❌ 打断2风险点（P2待验证）
- G体验：✅ 10路径9通；❌ 搜索入口疑缺（P3待验证）
- H一致：❌ 圆角/按钮高/胶囊边距4组不统一——按修复纪律禁顺手改视觉基线，仅记录待批
- I数据：✅ 落盘/迁移/事务原子化好；❌ R1-016 POST>1M静默丢（P1下批）；❌ R1-003 TODO迁移（P2待批）
- J性能：✅ 懒加载/key/IO分布好；⏭️ 启动单次runBlocking与图片钳制无证据不改（探针先行）
- K质量：❌ 超长函数5（P2待批）、VM直写文件2（P2待批）、哨兵吞错（P2下批）；⏭️ 死代码3（记录）
- L安全：✅ 无硬编码密钥；❌ R1-017日志隐私2处（P1下批）；⏭️ R1-018多余权限待验证、R1-019导出明文（设计如此，待批）

## 问题总账（编号|维度|严重度|文件:行号|状态）
| 编号 | 维度 | P | 文件:行号 | 状态 |
|---|---|---|---|---|
| R1-001 | A | P2 | 27处`!!` | 批1/3/4已验证（v4.64.9/404；余4处为守卫后惯用/逻辑蕴含，降P3记录：MoreOptionsScreen:235、WeeklyScheduleScreen:351、WebViewRequestInterceptor:527、App:351） |
| R1-003 | I | P2 | `BackupRepository.kt:490` TODO样式迁移 | 待批（需设计） |
| R1-004 | E | P2 | `AdvancedColorPicker.kt:223` 等散落颜色10处 | 遗留（下轮） |
| R1-006 | K | P2 | 6处println | 批5已验证（v4.64.10/405；收归AppLog.w） |
| R1-010 | A | P3 | `GlassPlatform.android.kt:99` | 批7已验证（v4.64.12/407） |
| R1-011 | D | P1 | `WeeklyScheduleScreen.kt`/`TodayScheduleScreen.kt`/`AgendaScreen.kt` 缺Error态 | 待批（架构） |
| R1-012 | D | P1 | 防重4处 | 批10已验证2处（v4.64.15/410；Agenda提交锁+SchoolItem节流）；Today幂等降P3记录、Weekly单发误报 |
| R1-013 | D | P2 | 文案10条 | 待批（#1省略号三语不统一不改；#8感叹号7处非孤例；#4术语/#5冒号等附候选方向，见FIXLOG批7备注） |
| R1-016 | I | P1 | `WebViewRequestInterceptor.kt:147-149` POST超限静默丢 | 遗留（下批） |
| R1-017 | L | P1 | 日志隐私3处 | 批3/批5已验证（v4.64.10/405；SAMPLE行删、stack段去；残WebBridgeHandler message段，用户可见文案，保留） |
| R1-026 | D | P1 | `TextImportViewModel.kt:102,114,121,139` 裸中文用户可见 | 批2已验证（v4.64.7/402） |
| R1-027 | K | P1/P2 | 重复字面量 | 批6/批9已验证（v4.64.14/409；08:00×6+640.dp×11；模板654/`"placeholder"`/`"courseTableId"`/`"FilesDir"`记录不改，理由在案） |
| R1-030 | B | P1 | `ResourceInitializerManager.kt:34` 裸Scope | 待批（DI改动） |
| R1-031 | C | P2 | `AppSettingsRepository.kt:449-451` 周次差一 | ✅误报（446-447双对齐→diffDays恒为7倍数，除法精确；学期前week≤0由474过滤+Weekly501分支承接） |
| R1-033 | C | P2 | `CourseDao.kt:68` ABORT+先查后写TOCTOU | 待批 |
| R1-034 | G | P3 | 课程名搜索无入口（学校搜索存在 `SchoolSelectionListScreen.kt:125`） | 待批（feature，非缺陷） |
| R4-001 | I | P2 | 零时长/倒挂课照进提醒·模式·灵动岛排程 | 批11已验证（v4.64.16/411；effectiveCourses 加结束>开始过滤） |
| R4-002 | I | P3 | ICS 导出周下界缺失（week≤0 落盘过去日期） | 批11已验证（v4.64.16/411；改双边截断） |
| R6-READ | K | P3/P4 | 新人可读性 20 项（名实8/缺注释5/易误用4/God拆分4/魔法语义3） | 记录（重命名与拆分属架构面，不自动改） |
| R1-023/022/024/025/028/029等 | K/J/F/C | P2/P3 | 超长函数/VM直写文件/JS空catch/哨兵/魔法数字/动画打断 | 记录在案，下轮处理 |

## 第 7 轮 · 回归复扫（2026-10-01 02:50，本地零网络）
- `!!`：残留均为测试断言 + 已定案4处（WebViewRequestInterceptor:527 逻辑蕴含等 P3），11 批改动点零新增 ✅。
- commonMain `println`：0（批5清零确认）✅。
- 严格 TODO/FIXME/HACK：仅 BackupRepository:490（R1-003 待批）✅。
- 结论：修复无回退、无新模式命中；断网窗口（03:00–03:20）内只做本地只读与本地编译，不碰网络。
- 用户指令（02:50）：03:20 后继续。已设本地定时器跨过窗口，03:21 自动恢复：第 8 轮回归复扫 + fetch 核对分支状态 + 探活推送通道。

## 第 9 轮 · 通知链/小组件/工具链深挖（2026-10-01 04:00+，3 路并行只读）
- 5 个高危项逐一复核证据后**当场修**（批12）：manifest 缺 `<queries>`（targetSdk 37 包可见性让早八时钟探测恒 null，形成"探测失败→不尝试→不授予可见性"自锁，永久走应用内降级）；AlarmScheduler 只按 START action 取消，END 闹钟 PendingIntent 残留（filterEquals 只比 action）；DynamicIslandManager.sync() 全仓仅 1 处自举调用（KDoc 承诺的 SyncManager 接线缺失→开机/改设置后窗口永不下单）；WidgetListCapacity 闭式解漏 (N-1)×5dp 分隔线；push_via_wincred 打印令牌前 4 字符。
- 5 个子代理误报/降级留证：tools/ 被 gitignore 是 **AGENTS.md 明文红线**（AI 会话产物不入库），非缺陷；Listener 注册/注销无悬空；composeResources 三语 key 齐备。
- 其余 P1/P2 处置结果：通知设置 6 开关不触发重排 → 批13已修（7 字段签名比较）；无 TIMEZONE_CHANGED 监听 → 批15已修（新增 TimeChangeReceiver）；RINGER_MODE_NORMAL 覆盖用户铃声偏好 → 批14已修（记录并还原）；灵动岛绕 effectiveCourses → 批16已修（收敛到 ReminderEngine.effectiveCourses）。
- 剩余转待批/下轮：通知 ID 哈希碰撞 ~10%（改 ID 生成会牵动已投递通知的 dismiss 对应关系）、privacy.html 缺 SET_ALARM/WAKE_LOCK（法务文本，不擅改）、tools/ 三个 `git show HEAD` 覆写脚本（gitignore 内，改动不随仓走，需重写流程）、Worker 限流 clear()（部署侧）。

## 第 10 轮 · 通知链修复回归复核（2026-10-01 05:00）
- 批13-16 共 4 项修复逐条反查证据链：SyncManager 0.3 链路用 `NotificationSettingsSignature` 7 字段（不取全量 settings），无关开关不触发 ✅；TimeChangeReceiver 三个 action 全在 HANDLED_ACTIONS 白名单，运行时注册 RECEIVER_NOT_EXPORTED ✅；toggleSilent 的 prefs 记录仅在「当前非静音」时写入，连续重排不覆盖 ✅；computeState 保留默认参数 emptySet() 供单测/旧调用点兼容 ✅。
- 单测锁：WidgetListCapacityTest 新增 4 例（逐档交叉验证、249dp 溢出边界、dividerDp=0 退化为旧闭式解、负值按 0 处理），androidApp 64 例全绿。
- 复扫：本批改动点零 `!!` 引入、零 `println`、无 TODO 残留。

## 第 11 轮 · 小组件一致性收口（2026-10-01 05:10）
- 批17-18 把四规格间的三处实质不一致收口：DoubleDays 空态改走 `todayEmptyTip`（此前恒「无课程」，与另两规格的「今日课程已结束」互相矛盾）+ 新增 `text_no_courses_tomorrow`（四语齐备）；ListVertical 星期改 `DateTimeFormatter`（原数组只覆盖 zh/en 四档）；ListVertical 头部补 `headerSizeSp`（原固定 14sp，S 档层级反转）。
- Compact 死代码清理：`tv_status_msg` 在 `widget_today_compact_native.xml` 中不存在，写入恒 no-op；删除并在 `showStatus` 签名注释写明 `msg` 仅全屏形态生效，防后人再踩。
- 剩余 P3 记录不动（`resolveCourseColor` 纯转发冗余但被单测引用，删会连带改测试；`WidgetCoursePalette` 使兜底分支成测试可达的防御代码，保留合理）。

## 第 12 轮 · 通知剩余项复核与修复（2026-10-01 10:45，v4.64.21）
- 子代理逐条读代码取证，7 项定案：4 项当场修（通知 ID 命名空间、精确闹钟提示收敛、三策略异常隔离、LOCKED_BOOT_COMPLETED 死配置）；1 项确认无需修（`skippedDates` 重复过滤语义无害，仅建议加注释）；2 项待批（DND 只认 PRIORITY 的完整「记录还原」版、早八降级续链）。
- **纠正本文件一处误判**：原写「改 ID 生成会牵动已投递通知的 dismiss 对应关系」——不成立。dismiss 的 requestCode/extra/identifier 与通知 ID 在同一次 `build()` 内派生（`CourseReminderNotifier.kt:58,89-101`），登记簿键是 `occurrenceKey` 原文（`PostedNotificationRegistry.kt:32`）。该项据此从「必须待批」降级为可做。
- **反向误修风险留证**：`LOCKED_BOOT_COMPLETED` 的正确处理是**删 action**，严禁补 `android:directBootAware="true"` —— 本应用 Room/DataStore 全为 credential-protected，解锁前读不到库，补上反而制造崩溃或静默假成功。
- 碰撞率实测（本地复算 FNV-1a，与 Kotlin 实现逐位一致）：10 万槽 7 天窗口 70 节约 2.17%（30 窗口累计约 48%）；100 万槽约 0.22%。REVIEW 原估「~10%」偏保守，同日碰撞才是真实受损面。
- 复核结论亦修正上一子代理的误报：精确闹钟缺权限时并非「200 条通知刷屏」（同 ID 覆盖 + `setOnlyAlertOnce(true)`，用户只听到一次），真实代价是 261 次 `resolveActivity` + build + binder 的 IPC 浪费。

## 第 13 轮 · 数据层与工具链复核（2026-10-01 10:50，v4.64.22）
- 4 项落地：`ResourceInitializerManager` 裸 Scope 补 `SupervisorJob`（原默认 `Job()` 非 supervisor，任一子步骤抛出即整作用域取消 + 默认 handler → 崩溃）；`WidgetUpdateHelper.listRowHeightPx` 改 `by lazy`（仅 ListVertical 消费，无该规格时是纯浪费，**无探针即可证伪**）；`tools/rebuild_zip.py` 改废弃守卫 + 同步修 `docs/adapter-sop.md` 四处；`tools/build_schools.py` 路径改 `__file__` 派生。
- **工具链 P1（本轮最大收获）**：`rebuild_zip.py` 产出的「逐文件 zip」App 端**解不开**（要求 zip 内唯一条目 `repo.skr` + `SKR1` 魔数，`OfflineRepoArchive.kt:60-66` 硬校验），而异常被 `initializeOfflineRepo` 的 `runCatching` 吞掉 → 内置适配资源**静默失效**。真实入口是 Gradle `:shared:packSchoolsZip`（已排序 + 固定时间戳 + level 9），且被预构建流程依赖、每次构建自动重生成。已入库 zip 无需重建。
- 2 项定案无需修（记录在案）：`teacher` 字段虽不被小组件读取，但被课前提醒通知消费（`CourseReminderNotifier.kt:83-121`），从 diff 排除会让「只改教师名」不再落库 → 通知长期显示旧教师名；`CourseDao` 的 check-then-write 已在同一 `withWriteTransaction` 内，无 TOCTOU。
- 1 项降级为「仅补注释」：`WidgetRepository.replaceSnapshotIfChanged` 的读在事务外，但全仓唯一写者且整体已被 `syncMutex` 串行 → 无并发缺口；且 Room 3.0.1 下把 `Flow.first()` 塞进事务属未验证区域，收益为零风险不零。
- **反向误修风险留证**：`WAKE_LOCK` 是 `androidx.work` 合并引入、App 自身未声明 —— 补进隐私页「App 申请的权限」表属失真（应表述为「依赖库引入」），故只补 `SET_ALARM` 一行，且与版本号同步一并交用户确认。

## 第 14 轮 · 导入导出链 + 数据库层复核（2026-10-01 11:25，v4.64.23）
- 四个 P1 全部落地：`importTimeSlots` 空列表清空作息（适配脚本算出 0 个时段即触发）、跨课表全局主键冲突（`Course.id` 不分域，`preserveId=true` 复用原 id）、单双周只在 ◇ 路径过滤（CSV/HTML/纯文本/Excel-非◇ 四条路径静默入库为全周）、widget 快照永不自愈（内容比对遇「空库==空快照」恒跳过）。
- widget 自愈修法值得留证：用**版本戳**而非「库是否为空」判断——后者会把「合法的无课状态」也当成需要重建，15 分钟一次无谓全量重写。版本戳只在真正落库时 +1，库被清空后归 0，入参携带上次递增值 ⇒ 必然重建。
- **复核确认为误报、未改**：`WebDAV restoreAllCourseTablesCbor` 是真事务先清后写，且有三层防御（空备份完整性校验 / 内存快照 / 失败回滚）；`BackupRepository:353` 的嵌套 `withWriteTransaction` 经反编译确认以 `SAVEPOINT` join 外层（字节码常量含 `RELEASE/ROLLBACK TRANSACTION TO SAVEPOINT`），注释属实。
- 转待批（行为/架构面）：ICS 导入不聚合（1 门 16 周裂成 16 门，闭环断裂）、ICS `UID` 每次随机（重新导出再导入产生重复事件）、建表与导入分属两事务（失败留孤儿空表）、`CourseTableDao.insert` 用 REPLACE 可 CASCADE 删光整表（DAO 层无护栏）、WebDAV 下载把截断/认证失败统一报「文件已损坏」、解析器错误未 i18n。
- 待清理：仓库根 4 个 `tmp_*.db` 是 `adb pull` 的真机库副本，**含用户真实课程数据**（courses 表 15 行），已被 `.gitignore` 忽略但会被全盘备份/IDE 索引扫到；建议删除（保留结构而非整库）。未擅自删除，等用户确认。

## 第 8 轮 · 窗口后恢复（2026-10-01 03:21+）
- 03:21 定时器触发：初次探活 schannel 握手失败（已知故障特征），60 秒后重探成功，origin/main=7e6806e 无变动。
- fetch 间歇性失败（peer 收数据失败/握手失败交替，灾后抖动）；`origin/fix/auto-review-r1-r2...HEAD`=0 0，3 提交推送态完整；工作区零 tracked 改动（仅 REVIEW/FIXLOG 未入库 + 2 个他会话文件）。
- 第 8 轮复扫：残留 `!!` 全为已定案项（UniversalScheduleParser 图不变式/Weekly pager/Weekly:351 守卫/:905/Appearance×2），commonMain `println` 保持 0，无新增 ✅。
- 无可推送内容（无需重推）；循环保持：待批清单等用户方向，其余每轮回归复扫继续。

## 本轮新发现明细（含证据原文）
- R1-001a `MorningAlarmPlan.kt:68` `.minByOrNull { ReminderEngine.parseTime(it.startTime)!! }` → 已修为 mapNotNull+minBy（修复于第1轮，通读复核：上游虽已filter但 comparator 内!!仍为坏味道）
- R1-001b `CourseTableConversionDialogs.kt:60` `?: localizedOptions.find { it.value == 15 }!!` → 已修加 `?: localizedOptions.first()`（同函数49-57构造恒含15，不可达但去!!）
- R1-001c `GlassDecorations.kt:92` `path!!.rewind()` → 已修 `val p = path ?: return clipRect(...)`（绘制热路径不变式显性化）
- 其余见上表；6处!!重点复核全量结论：现有路径均不可达崩溃（deterministic纯函数/同帧快照/构造恒含），无P0（通读前后50行+定义追溯）

## 遗留项与卡点
- 批1验证完成（`:shared:compileKotlinJvm` + `:desktopApp:compileKotlin` + `:androidApp:assembleDebug` 全过，版本已迭代 v4.64.6/401，工作日志已追加；未提交，待用户确认后显式add推送）
- 03:00–03:20 断网窗口将至：窗口内不做fetch/push/gh，只做本地修复+编译；到点自动切本地模式，无需停轮
- 待批（破坏性/架构/视觉基线）：R1-011/R1-015/R1-022/R1-023/R1-030/R1-033/R1-003/R1-019 —— 继续扫其余部分不停摆

## 轮次报告
- 扫描模块数：7顶层（shared/commonMain·androidMain/androidApp/desktopApp/tools/website/adapter-worker）
- 检查项：14模式×全仓 + 12维度×7模块；✅约40% / ❌约45% / ⏭️约15%（明细见上）
- 新发现：35项（P0 0/P1 7/P2 19/P3 9，含误报已剔除；`!!`6重点复核后降级）
- 修复：3（R1-001a/b/c，待验证）；遗留32；下一轮：第2轮修复者视角（复核本轮3修复点前后50行 + R1-012/R1-026/R1-027/R1-006/R1-017）

## 第 2 轮 · 修复者视角复核（2026-10-01 02:10+，批1–7全量 diff 重读 + 模式复扫）
- 复核方式：`git diff -- shared androidApp/src` 逐 hunk 重读（15 文件）+ `!!`/`println`/SAMPLE 模式复扫。
- 复扫结论：修复点零残留（MorningAlarmPlan/DynamicIsland/ScheduleGrid 零 `!!`；glass+ApiDateImporter 零 `println`；SAMPLE 零命中），无新增模式命中 ✅。
- 新发现 3 项：
  - R2-001（P3 记录）：DynamicIsland 改后对全天课程 eager 解析（原 firstOrNull 短路），每日 ≤10 条纯字符串解析，可忽略，已留 REVIEW。
  - R2-002（P3 已修/批8）：批6 插入的 tool 导入落在 data 块中间（AddEditCourseViewModel.kt:7），已移至 navigation 之后，shared 门验证中。
  - R2-003（P3 记录）：`ReportErrorPayload.stack` 去日志后无其他读取点（仅 JS 桥接填充 + JSON 解析），保留字段（解析需要），不删。
- 本轮状态：批1–7（R1-001a/b/c/d/e、R1-026、R1-006、R1-010、R1-017b/c、R1-027a）全部三端验证归档；下一轮：第3轮用户视角（10 条操作流实机走查，R1-011/R1-012 需真机/模拟器推演；R1-031 已代码层闭环为误报）。

## 待批清单（破坏性/架构/视觉基线/UX  judgment，需用户定方向后才动手）
- R1-011 三屏缺Error态（P1）：Weekly/Today/Agenda 的 UiState 无 Error 建模（选校屏四态为标杆）。方案：sealed 加 Error + AppErrorState 分支；影响三屏+三VM，不自动改。
- R1-012 防重4处（P1）：Agenda:279 addEvent 双击穿透、SchoolSelectionList:435 行点击无节流、Today:310 toggle 连发、Weekly:749 手势连续触发。方案：VM 侧 in-flight 守卫 + 行点击 throttle（300ms）；待定节流时长与是否全局封装。
- R1-013 文案（P2）：#4 统一`教师：`或`老师：`（候选二选一）；#5 `ID: /创建于: ` 半角冒号→全角（仅简中，英/繁不动）；#2 量词 `%d项/%d个日程/%s节` 统一；其余见首轮明细。用户可见措辞，不擅改。
- R1-015 组件一致性（P2）：卡片圆角/按钮高/胶囊边距三套口径。按修复纪律禁顺手改视觉基线，待设计定 token。
- R1-016 POST超限静默丢（P1）：`WebViewRequestInterceptor.kt:147-149` >1M 丢弃无提示。方案：回落时打 AppLog.e + 导入页 toast（需定文案），或提限额；行为变更，待批。
- R1-003 样式迁移TODO（P2）：`BackupRepository.kt:490` 旧样式包无版本钳制。方案：补版本号+迁移/拒绝逻辑；数据兼容，待批。
- R1-019 导出明文（P2）：ICS/备份体明文（预期行为）。方案：维持 + 导出前二次确认文案（如已有则关闭）。
- R1-022 VM直写文件（P2）：两 VM 直接 fileSystem 写头像/壁纸。方案：下沉 Storage 网关；分层重构，待批。
- R1-023 超长函数（P2）：Weekly200行+/AgendaCreateSheet288行等。方案：按段拆小 Composable；大改，待批。
- R1-030 裸Scope（P1）：`ResourceInitializerManager.kt:34`。方案：注入应用级 scope；DI 改动，待批。
- R1-033 CourseDao TOCTOU（P2）：ABORT+先查后写。方案：改 IGNORE/REPLACE 或事务化；并发语义，待批。
- R1-004/R1-014 散落颜色与可达性（P2）：`AdvancedColorPicker` 彩虹刷等是否进 token、22dp 把手是否扩 48dp、圆点是否加形状冗余；视觉/布局变更，待批。
- R1-028/R1-029 魔法数字与动画打断（P2/P3）：轮询周期命名、`GlassInteractions`/`LiquidGlassTabs` 打断取消；前者可分批，后者需动效验证，待批。
- R1-034 课程搜索（P3 feature）、R1-035 maxLines 无上限（P3）：待批。
- R1-031 已闭环为误报（见上表）。

## 第 3 轮 · 用户视角走查（进行中，分支 fix/auto-review-r1-r2）
- R1-012 深查定案：4 点逐一通读调用链——(a)Agenda创建双击穿透已修（批10）；(b)选校双击推两页已修（批10）；(c)Today toggle `setDone` 绝对值写入幂等，不修（P3）；(d)Weekly 手势 onDragEnd 单发 + `isNoPositionChange` 守卫，不修（误报）。
- 状态新鲜度抽查：Weekly:203/359、Today:201/202 均为 `collectAsStateWithLifecycle`，VM 经 Flow 组合 Room/设置流，落盘即重发，无手动刷新缺口 ✅（通读+模式搜索）。
- 回归加固：`:shared:jvmTest`（NO-SOURCE，无 JVM 用例） + `:shared:testAndroidHostTest` **BUILD SUCCESSFUL**（16s，10 executed；改动区 ReminderEngine/MorningAlarmPlan/资源全量进 host 运行；仅 2 条既有 `No cast needed` warning，与本次无关）。
- 批11后复跑 hostTest 全过（R4-001 守卫进 effectiveCourses 后，引擎既有用例仍绿）。

## 第 6 轮 · 新人视角（2026-10-01 02:45，只读零改动）
- 名实不符 8：`Destination.Schedule`实挂 Agenda（App.kt:422）、`thisMonday`实为可配首日（Weekly:236）、24H 下 `startSection` 存小时浮点（WeeklyVM:64）、`isForeignTable`/`isCrush` 实为情侣表（WeeklyVM:72，isCrush 迁移遗留恒 false）、`floatingCourse` 实为调课 staging（WeeklyVM:92）、`todayXPalette(peach)` 实为奇偶条纹（Today:478）、`isGridHolding` 实为拖拽中（Weekly:252）、`pageGlassBackdrop=null` 实为诊断开关（Weekly:378/Today:232）。P3/P4，记录。
- 缺注释关键路径 5：Weekly:232-246 页码订阅链、Weekly:299-313 双口径 duration、Today:392-406 overdue 回推、Agenda:743-754 滑动不改日期、Agenda:1906-1933 分组/状态阈值。P3/P4，记录。
- 易误用 4：AddEditCourseChannel 先发后跳（漏发卡空表单零写入）、TextImportFormat.fromName 静默回 null、`NavigationBackHandler` 吞根返回条件、`hazeSource` 平级约束。P3，记录。
- God 文件 4：App.kt/Weekly/Agenda/Today 拆分方向各一句（不动手）。P4，记录。
- 魔法语义 3：Agenda GROUP/STATUS 裸 Int、NextCard 今日明日复用、firstDayOfWeek Int 无约束 + GRID 预留。P3/P4，记录。
