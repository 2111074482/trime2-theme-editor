# Trime2主题键盘实时预览编辑器安卓应用一次性开发计划书

> 文档类型:一次性开发实施计划
>
> 目标应用:原生 Android Trime2 主题键盘实时预览编辑器
>
> 产品布局参考:[layout-3-canvas.html](design-prototypes/layout-3-canvas.html)
>
> 参数依据:[主题Lua参数文档.md](主题Lua参数文档.md)
>
> 功能依据:[Trime2主题编辑器功能汇总.md](Trime2主题编辑器功能汇总.md)
>
> 计划原则:先固定产品边界、数据模型和预览机制,再一次性完成主流程、编辑能力、导入导出和验收,不在开发中途更换整体布局或另起一套编辑模型。
>
> 最终交付原则:功能必须先一次性开发完毕并完成全部验收,上传源码、创建公开仓库、触发云端构建、生成 APK 和上传构建产物只能作为最后一个环节执行。功能未完成、验收未完成或存在未关闭的必做功能缺口时,禁止上传、推送、创建公开仓库、触发 GitHub Actions 或生成交付 APK。

## 1. 项目目标

开发一款原生 Android 应用,让用户在手机或平板上直接创建、编辑、实时预览和导出 Trime2 Lua 主题。

应用必须解决四个核心问题:

1. 用户能够通过可视化界面增加、删除、复制、移动和调整键盘按键。
2. 用户修改样式、图片、字体、事件后,预览能够立即反映结果。
3. 生成的主题目录和 Lua 文件能够被 Trime2 直接读取。
4. 对当前 Trime2 可以解析但不能触发、或者没有可靠消费逻辑的字段显示明确警告。

本项目不是普通的颜色配置器,也不是只展示键盘截图的原型。最终应用必须具备结构化主题编辑、原生预览、资源管理、验证和导出能力。

## 2. 固定产品形态

### 2.1 产品名称

暂定名称:`Trime2 Studio`。

中文副标题:`Trime2 主题键盘实时预览编辑器`。

### 2.2 目标用户

- 想在手机上制作 Trime2 主题的普通用户。
- 需要快速调整按键、颜色和图片的主题作者。
- 需要直接编辑 Lua 并检查兼容性的高级用户。
- 需要在不同屏幕方向和尺寸下检查键盘布局的开发者。

### 2.3 设备范围

- Android 手机竖屏。
- Android 手机横屏。
- Android 平板横屏和竖屏。
- 固定兼容当前工程:`minSdk 21`、`compileSdk 35`、`targetSdk 35`、Java/JVM 11;首轮开发不得擅自升级 SDK、AGP、Kotlin、Gradle、NDK 或 CMake。

### 2.4 设计方向

以 `layout-3-canvas.html` 为唯一布局参考,转换为原生 Android 工作台:

| HTML 原型区域 | 原生 Android 对应区域 |
|---|---|
| 全屏画布 | `CanvasWorkspaceView` 自定义画布容器 |
| 顶部浮动工具条 | `StudioTopBar` |
| 左侧组件工具箱 | `ComponentToolRail` |
| 中央键盘预览 | `ThemePreviewSurface` |
| 选中对象快捷条 | `SelectionActionBar` |
| 左侧状态/结构卡片 | `PreviewStatePanel`、`StructureInfoPanel` |
| 右侧属性面板 | `InspectorDrawer` |
| 底部缩放和保存状态 | `WorkspaceStatusBar` |

手机端不强行保留桌面三栏。手机端改为:顶部工具栏、中央画布、底部工具栏、右侧属性抽屉;平板和横屏使用三栏工作台。

## 3. 一次性开发边界

### 3.1 本次必须完成

- 新建主题。
- 打开主题目录、ZIP 或单个 Lua 入口。
- 保存本地项目。
- 增加、删除、复制、移动按键。
- `rows` 行式布局完整编辑。
- `flex_box`、`keys`、`key_maps` 的基础编辑和代码保留。
- 按键文字、提示、尺寸、样式和事件编辑。
- 长按、滑动、状态替代事件编辑。
- 按键背景图、键盘背景图、标签同名图片。
- 字体、音效和振动配置。
- 候选栏、工具栏、符号面板、剪贴板和组合窗基础样式。
- 竖屏、横屏和自定义尺寸实时预览。
- 预览状态模拟。
- 撤销、重做和修改历史。
- Lua 生成、语法检查、结构检查和资源检查。
- 导出主题目录和 ZIP。
- 兼容性警告。
- Android Storage Access Framework 文件访问。

### 3.2 本次不承诺真实生效

以下字段可以保留、显示和导出,但编辑器必须显示兼容性警告:

- `double_click`。
- `triple_click`。
- `combo` 的触发行为。
- `swipe` 滑动点按映射。
- `layout` 当前后续布局效果。
- `symbol.flex_basis`。
- `composition.border`。
- `composition.spacing`。
- `composition.round_corner`。
- `composition.elevation`。
- `candidate.expanded.pressed.ripple_color`。

### 3.3 本次不做

- 修改 Trime2 输入法核心 Java 逻辑。
- 在编辑器中重新实现完整 Rime 引擎。
- 自动执行用户未知 Lua 回调或脚本。
- 云端账号、在线主题市场和在线协作。
- 自动发布到应用商店。
- 直接绕过 Android 存储权限写入其他应用私有目录。

## 4. 技术路线

### 4.1 原生技术栈和仓库基线

当前仓库基线已经确认:

- Gradle Kotlin DSL。
- Gradle Wrapper `9.2.0`。
- Android Gradle Plugin `8.11.0`。
- Kotlin `2.2.0`、KSP `2.2.0-2.0.2`。
- Java/Kotlin JVM Target 11。
- `minSdk 21`、`compileSdk 35`、`targetSdk 35`、Build Tools `35.0.0`。
- 当前模块为 `:app` 和 `:codegen`;主应用命名空间 `com.osfans.trime`、applicationId `com.nirenr.trime`。
- 主源码约 590 个 Java 文件和少量 Kotlin 文件,不能按纯 Kotlin 新工程处理。
- 已启用 ViewBinding,并已有 AppCompat、Activity KTX、ConstraintLayout、Navigation、RecyclerView、ViewPager2、WorkManager、Coroutines 和 Flexbox。
- 已内置 AndroLua、`org.luaj` Java 源码、Lua 编辑器、主题 Java View、librime JNI 和 CMake/NDK 构建。

首版采用:

- 在现有 `:app` 内新增 `com.osfans.trime.editor` 功能包和独立 `ThemeEditorActivity`。
- 使用 Android 原生 View、自定义 View、ViewBinding、ViewModel、StateFlow、RecyclerView 和现有组件。
- 不引入 Compose,不使用 WebView 作为编辑器或预览核心。
- `layout-3-canvas.html` 仅作为布局参考。
- 优先使用当前版本目录已存在的依赖,新增依赖必须说明必要性、许可证、APK 体积和 `minSdk` 影响。
- 首版最近项目和编辑器设置优先使用文件元数据与 SharedPreferences;Room 依赖当前被注释,没有明确必要性前不启用。
- Android Storage Access Framework 处理用户目录授权;FileProvider 仅用于受控分享缓存目录。

### 4.2 主题解析和接入策略

首版直接复用同一 `:app` 中的 LuaJ、AndroLua、`ThemeManager`、`Style`、`KeyStyle`、`Key`、`Event`、KeyboardView 和 Composition 逻辑,避免复制另一套解析器或立即重构输入法核心。

纯数据模型、字段注册表、验证器、写出器和命令历史不得依赖 `TrimeService` 或具体 Android View;预览适配层允许依赖现有 Java View。行为稳定并有往返测试后,才能评估抽取 `theme-core` Android Library。首版不得先做大规模模块迁移。

解析器必须分成三层:

1. Lua 源码解析层:Lua 文件加载、语法错误和运行时环境。
2. 结构化模型层:主题、样式、键盘、按键和资源引用。
3. Android 预览层:将模型转换成原生 Preview View。

编辑器 UI 不得直接通过字符串替换修改 Lua 文件。

### 4.3 包结构建议

```text
app/src/main/java/com/osfans/trime/editor/
├── App.kt
├── MainActivity.kt
├── navigation/
├── project/
│   ├── ThemeProject.kt
│   ├── ProjectRepository.kt
│   ├── ProjectSnapshot.kt
│   └── ProjectFileAccess.kt
├── model/
│   ├── ThemeModel.kt
│   ├── StyleModel.kt
│   ├── KeyboardModel.kt
│   ├── RowModel.kt
│   ├── FlexContainerModel.kt
│   ├── KeyModel.kt
│   ├── EventModel.kt
│   └── ResourceModel.kt
├── parser/
│   ├── LuaThemeParser.kt
│   ├── LuaThemeWriter.kt
│   ├── LuaValidator.kt
│   └── CompatibilityAnalyzer.kt
├── preview/
│   ├── ThemePreviewController.kt
│   ├── ThemePreviewSurface.kt
│   ├── DeviceProfile.kt
│   ├── PreviewState.kt
│   └── PreviewInteractionController.kt
├── editor/
│   ├── CanvasWorkspaceView.kt
│   ├── ComponentToolRail.kt
│   ├── InspectorDrawer.kt
│   ├── SelectionActionBar.kt
│   ├── StructureInfoPanel.kt
│   └── WorkspaceStatusBar.kt
├── resource/
│   ├── ImageRepository.kt
│   ├── FontRepository.kt
│   ├── SoundRepository.kt
│   └── ResourceValidator.kt
├── history/
│   ├── EditCommand.kt
│   ├── UndoRedoManager.kt
│   └── HistoryEntry.kt
└── validation/
    ├── Diagnostic.kt
    ├── ThemeValidator.kt
    └── OverflowAnalyzer.kt
```

包名、目录名和类名可根据实际仓库调整,但模块职责必须保持隔离。

## 5. 核心数据模型

### 5.1 项目模型

```kotlin
data class ThemeProject(
    val projectId: String,
    val rootUri: Uri?,
    val theme: ThemeModel,
    val styles: Map<String, StyleModel>,
    val keyboards: Map<String, KeyboardModel>,
    val resources: ResourceIndex,
    val diagnostics: List<Diagnostic>,
    val previewState: PreviewState,
    val rawNodes: List<RawLuaNode>,
    val sourceVersion: TrimeCompatibilityVersion,
    val isDirty: Boolean,
)
```

项目模型必须保存:

- 主题入口。
- 样式集合。
- 键盘集合。
- 图片、字体、音效和脚本资源。
- 当前选择的键盘。
- 当前选择的样式。
- 当前选中节点。
- 验证结果。
- 修改状态。

### 5.2 主题模型

对应 `main.lua`:

- `name`。
- `author`。
- `style`。
- `keyboard`。
- `get_keyboard` 的可保留 Lua 源码或规则模型。
- `action_labels`。
- `preset_keys`。
- `onWindowShown()`。
- `onWindowHidden()`。
- `onStartInput(editorInfo, restarting)`。
- `onFinishInput()`。
- `onConfigurationChanged(configuration)`。
- `onDestroy()`。
- `onSpeechResults(text)`。
- 生命周期和语音回调的保留源码及风险标记。

### 5.3 样式模型

通用样式字段:

- `text_color`。
- `text_size`。
- `background`。
- `corner_radius`。
- `stroke_width`。
- `stroke_color`。
- `elevation`。
- `shadow_color`。
- `font`。
- `gravity`。
- `height`。
- `show`。
- `padding`。
- `margins`。
- `scale_x`、`scale_y`。
- `translation_x`、`translation_y`、`translation_z`。
- `offset_x`、`offset_y`。
- `pressed`、`hint`、`long_click`、`preview` 子样式。

样式模型必须保留继承关系,不能把所有继承字段静态复制后丢失原始结构。

样式模型还必须按组件区分节点,不能用一个 `KeyStyle` 对象代替所有组件:

- `KeyboardStyleModel`。
- `KeyStyleModel`。
- `PopupStyleModel`。
- `CandidateStyleModel` 与 `CandidateExpandedStyleModel`。
- `ToolbarStyleModel`。
- `SymbolStyleModel`。
- `ClipboardStyleModel`。
- `PreeditStyleModel`。
- `CompositionStyleModel` 与 `CompositionWindowModel`。

每个组件模型必须保存原始字段、继承来源、最终解析值和兼容性诊断。

### 5.4 键盘模型

支持:

- 键盘元数据。
- `style`。
- `lock`。
- `ascii_mode`。
- `key_width`。
- `key_height`。
- `rows`。
- `flex_box`。
- `keys`。
- `key_maps`。
- `layout` 原始数据和兼容性诊断。
- `layoutMode` 当前实际生效模式。
- `sourceLayoutNodes` 被隐藏或未生效的其他布局节点。
- `migrationHistory` 布局迁移记录。
- `stableNodeId` 和父容器路径。

布局优先级必须与 Trime2 一致:

```text
rows > flex_box > keys > key_maps
```

### 5.5 按键模型

```kotlin
data class KeyModel(
    val id: String,
    val click: EventModel?,
    val longClick: EventModel?,
    val swipeLeft: EventModel?,
    val swipeRight: EventModel?,
    val swipeUp: EventModel?,
    val swipeDown: EventModel?,
    val combo: EventModel?,
    val label: String?,
    val hint: String?,
    val description: String?,
    val style: String?,
    val width: Double?,
    val height: Double?,
    val x: Double?,
    val y: Double?,
    val popup: PopupModel?,
    val composing: EventModel?,
    val hasMenu: EventModel?,
    val paging: EventModel?,
    val ascii: AsciiReplacement?,
    val sendBindings: Boolean?,
    val hintLong: String?,
    val hintLeft: String?,
    val hintRight: String?,
    val hintUp: String?,
    val hintDown: String?,
    val swipeRepeatable: Boolean?,
    val sourceForm: KeySourceForm,
    val rawFields: Map<String, RawLuaValue>,
)
```

data class PopupModel(
    val items: List<PopupItem>,
    val columnCount: Int?,
)

sealed interface PopupItem

data class PopupTextItem(val value: String) : PopupItem
data class PopupKeyItem(val key: KeyModel) : PopupItem

sealed interface AsciiReplacement

data class AsciiEventReplacement(val event: EventModel) : AsciiReplacement
data class AsciiKeyReplacement(val key: KeyModel) : AsciiReplacement

### 5.6 事件模型

支持:

- `send`。
- `text`。
- `commit`。
- `command`。
- `option`。
- `select`。
- `toggle`。
- `states`。
- `label`。
- `preview`。
- `description`。
- `shift_lock`。
- `sticky`。
- `repeatable`。
- `functional`。
- `index`。

需要保留事件来源:预设键引用、字符串事件、直接事件表。导出时尽可能保持用户原来的表达形式。

### 5.7 组件和资源模型

必须建立独立模型,避免把候选栏、工具栏、符号、剪贴板、预编辑和组合窗字段塞入按键模型:

- `CandidateModel`、`CandidateExpandedModel`、`CandidateFilterBarModel`。
- `ToolbarModel`、`ToolbarKeyModel`、`SchemaSwitchModel`。
- `SymbolPanelModel`、`SymbolTabBarModel`、`SymbolToolbarModel`。
- `ClipboardModel`、`ClipboardItemModel`、`ClipboardTabBarModel`、`ClipboardToolbarModel`。
- `PreeditModel`。
- `CompositionModel`、`CompositionWindowItemModel`。
- `ResourceIndex`、`ResourceReference`、`ResourceLookupResult`。
- `StyleResolution`、`FallbackTrace`。

资源引用必须保存来源文件、相对路径、实际命中路径、查找顺序、文件类型、文件尺寸、校验值和所有引用者。

### 5.8 数值、颜色和空值模型

模型必须区分 Lua integer、Lua float、dp、sp、百分比、ARGB 颜色、`nil`、空字符串、空表、空数组、未设置并继承以及显式设置为默认值。写出器不得把这些状态混写成同一种结果。

### 5.9 类型契约和公共接口

以下类型必须在 `theme-core` 中定义最小契约,不得由各页面自行创建同名临时结构:

```kotlin
sealed interface RawLuaValue

data class RawLuaNode(
    val file: String,
    val line: Int?,
    val path: String,
    val source: String,
    val value: RawLuaValue,
)

enum class KeySourceForm { STRING_EVENT, INLINE_TABLE, PRESET_REFERENCE, SCRIPT, DYNAMIC, RAW }

data class Diagnostic(
    val severity: Severity,
    val code: String,
    val file: String?,
    val line: Int?,
    val luaPath: String?,
    val message: String,
    val suggestion: String?,
    val affectsPreview: Boolean,
)

data class ResourceReference(
    val ownerPath: String,
    val rawValue: String,
    val resolvedPath: String?,
    val lookupScope: LookupScope,
    val exists: Boolean,
)
```

公共接口必须至少包括:

- `ThemeParser.parse(ProjectSource): ParseResult`。
- `ThemeWriter.write(ThemeProject, WriteMode): WriteResult`。
- `ThemeValidator.validate(ThemeProject): List<Diagnostic>`。
- `CompatibilityAnalyzer.analyze(ThemeProject): List<Diagnostic>`。
- `ResourceResolver.resolve(rawValue, kind, stylePath, themeRoot): ResourceLookupResult`。
- `PreviewRuntime.render(ThemeProject, PreviewContext): PreviewResult`。
- `ProjectRepository.load/save/createSnapshot/restoreSnapshot`。
- `UndoRedoManager.execute/undo/redo/canUndo/canRedo`。

所有接口必须返回可诊断结果,不能用 `null` 代表“文件不存在、字段未设置、解析失败和权限失败”四种不同状态。

### 5.10 集中默认值和回退规则

`ThemeDefaults` 必须集中保存当前 Trime2 版本的默认值,至少包括:

- `popup.column_count = 5`。
- `popup.key.width = 10`、`popup.key.height = 15`。
- `candidate.height = 48`。
- `candidate.expanded.filter_bar.show = true`。
- `candidate.expanded.filter_bar.gravity = left`。
- `candidate.expanded.tool_bar.gravity = right`。
- `candidate.expanded.tool_bar.keys = hide/page_up/page_down/char_filter`。
- `symbol.tool_bar.keys = hide/page_up/page_down/BackSpace`。
- `clipboard.tool_bar.keys = hide/page_up/page_down/undo`。
- `toolbar.schema_switches = false`。
- `toolbar.hide.text = ▽`。
- `composition.position = fixed`。
- `composition.movable = false`。
- `composition.min_length = 0`、`max_length = 5`。
- `composition.sticky_lines = 0`、`max_entries = 5`、`cloud_max_entries = 0`。
- `composition.all_phrases = false`、`use_cursor = true`。
- `composition.line_spacing = 1`、`line_spacing_multiplier = 1.0`。
- `keyboard.style = keyboard`。
- `rows` 缺省宽高、单键宽高和 `key_width/key_height` 的计算规则。

回退链必须集中声明并可查询:

```text
样式字段 -> 子样式 -> 组件样式 -> keyboard/style -> 内置默认值
图片 -> 当前样式目录 -> 主题 images/ -> 共享 images/
字体 -> 当前样式目录 -> 主题 fonts/ -> 共享 fonts/ -> 系统字体
音效 -> 当前样式目录 -> 主题 sounds/
composition.show -> preedit.show -> 默认值
symbol/clipboard tab_bar/tool_bar -> candidate 对应样式 -> 内置默认值
```

每次解析结果必须保存实际命中的来源,并允许诊断面板查看回退链。

### 5.11 预览和结果类型契约

以下类型必须定义为共享核心类型:

```kotlin
enum class Severity { INFO, WARNING, ERROR, FATAL }
enum class LookupScope { STYLE, THEME, SHARED, SYSTEM }
enum class ResourceKind { IMAGE, FONT, SOUND, SCRIPT }
enum class WriteMode { PRESERVE, HYBRID, STRUCTURED }
enum class Orientation { PORTRAIT, LANDSCAPE }
enum class InputMode { CHINESE, ENGLISH, ASCII, NUMBER, SYMBOL }
enum class EditorAction { NONE, SEND, GO, DONE, SEARCH, PREVIOUS, NEXT }

data class ImportedFile(
    val relativePath: String,
    val sizeBytes: Long,
    val checksum: String?,
)

data class WrittenFile(
    val relativePath: String,
    val sizeBytes: Long,
    val checksum: String,
)

data class CandidatePreviewData(
    val text: String,
    val comment: String?,
    val isSelected: Boolean,
    val isCloud: Boolean,
)

data class DeviceProfile(
    val id: String,
    val screenWidthPx: Int,
    val screenHeightPx: Int,
    val keyboardWidthPx: Int,
    val keyboardHeightPx: Int,
    val density: Float,
    val orientation: Orientation,
    val source: DeviceSource,
)

enum class DeviceSource { PRESET, CURRENT_DEVICE, CUSTOM }

data class PreviewState(
    val inputMode: InputMode,
    val composing: Boolean,
    val hasMenu: Boolean,
    val paging: Boolean,
    val pressedNodeId: String?,
    val longPressedNodeId: String?,
    val expandedCandidate: Boolean,
    val symbolPanel: Boolean,
    val clipboardPanel: Boolean,
    val toolbarVisible: Boolean,
    val compositionVisible: Boolean,
)

data class ProjectSource(
    val rootUri: Uri?,
    val entryFile: String?,
    val importedFiles: List<ImportedFile>,
)

data class ParseResult(
    val project: ThemeProject?,
    val diagnostics: List<Diagnostic>,
    val cancelled: Boolean,
)

data class WriteResult(
    val files: List<WrittenFile>,
    val diagnostics: List<Diagnostic>,
    val cancelled: Boolean,
    val permissionDenied: Boolean,
)

data class PreviewContext(
    val inputMode: InputMode,
    val schemaId: String?,
    val editorAction: EditorAction,
    val candidates: List<CandidatePreviewData>,
    val composingText: String,
    val selectedCandidate: Int?,
    val device: DeviceProfile,
    val state: PreviewState,
)
```

`PreviewState` 必须是不可变状态,至少包含输入模式、组字、候选菜单、翻页、按下、长按、展开候选、符号面板、剪贴板面板、工具栏和组合窗状态。`PreviewContext` 由 `PreviewRuntime` 注入,预览 View 不得直接读取全局输入法服务状态。

`PopupModel` 必须区分字符串弹出项和按键表弹出项;`AsciiReplacement` 必须区分字符串事件、事件表和完整按键替代。`DeviceProfile` 必须同时保存屏幕尺寸、键盘区域尺寸、密度、方向和来源(预设、当前设备或自定义)。

### 5.12 主题加载和解析顺序契约

解析器必须按 Trime2 当前顺序执行并记录每一步:

1. 定位主题根目录和 `main.lua`。
2. 执行主题入口并读取主题元数据、`preset_keys`、`action_labels` 和回调源码。
3. 主题入口定义的全局变量可以被样式 Lua 使用。
4. 加载 `styles/<style>/main.lua`。
5. 样式缺失或执行失败时回退到内置 `styles/light/main.lua` 并产生诊断。
6. 根据显式配置选择的键盘优先级和 `get_keyboard(id, alphabet)` 规则确定键盘 ID。
7. 加载 `keyboards/<id>.lua` 并按 `rows > flex_box > keys > key_maps` 选择布局。
8. 建立样式继承、资源查找和事件引用索引。
9. 生成结构化模型和 `FallbackTrace`。

主题入口与样式文件共享全局变量时,解析器必须保存来源作用域;变量值无法静态确定时保留 RawLuaNode,不能把未知值写成空值。


## 6. 页面和交互设计

### 6.1 主工作台

主工作台由以下区域组成:

- 顶部项目工具栏。
- 中央无限画布。
- 左侧组件工具箱。
- 中央键盘预览。
- 选中对象快捷操作条。
- 左下预览状态和结构信息卡。
- 右侧属性抽屉。
- 底部状态和缩放栏。

### 6.2 顶部工具栏

功能:

- 返回项目列表。
- 项目名称和保存状态。
- 当前键盘选择。
- 当前设备选择。
- 撤销。
- 重做。
- 预览模式。
- 移动端打开属性抽屉。
- 保存主题。

当前键盘选择项:

- `qwerty26`。
- `number`。
- `symbols`。
- `editor`。
- 用户项目中的其他键盘。

当前设备预设:

- 竖屏。
- 横屏。
- 自定义尺寸。

### 6.3 左侧组件工具箱

支持拖放或点击添加:

- 选择工具。
- 按键。
- 文本。
- 图标。
- 候选栏。
- 键盘行。
- 背景。

点击“按键”必须在当前有效布局中创建真实 `KeyModel`;不能只添加展示占位。

### 6.4 中央实时预览

预览必须使用原生 Android View,支持:

- 键盘行和按键布局。
- 候选栏。
- 按键普通态。
- 按下态。
- 助记和方向提示。
- 长按弹出面板。
- 预览气泡。
- 工具栏。
- 符号面板。
- 剪贴板面板。
- 组合窗。

预览对象点击后:

1. 高亮选中对象。
2. 显示选中标签。
3. 更新右侧属性面板。
4. 更新图层和结构信息。
5. 在手机端打开属性抽屉。

### 6.5 选中对象快捷条

支持:

- 复制按键。
- 向左移动。
- 向右移动。
- 应用样式。
- 删除按键。
- 在多选模式下批量操作。

### 6.6 右侧属性抽屉

分为四个 Tab:

#### 基础

- 显示内容。
- 按键行为。
- 样式引用。
- X/Y。
- 宽度/高度。
- 背景颜色。
- 文字颜色。
- 圆角。
- 透明度。
- 阴影。
- 字体。
- 对齐。
- 内外边距。

#### 事件

- 点击。
- 长按。
- 上滑。
- 下滑。
- 左滑。
- 右滑。
- 组合事件。
- 添加事件。
- 引用预设键。
- 创建内联事件表。

#### 状态

- ASCII 替代。
- 组字替代。
- 候选菜单替代。
- 翻页替代。
- `send_bindings`。
- 状态标签。

#### 资源

- 按键背景图片。
- 标签同名图片。
- 字体。
- 音效。
- 振动效果。
- 当前资源文件大小和引用路径。

### 6.7 结构信息面板

分为:

- 统计。
- 图层。
- 历史。

统计显示:

- 当前布局模式。
- 行数。
- 按键数量。
- 键盘高度。
- 资源引用数量。
- 溢出数量。
- 错误数量。
- 警告数量。

图层显示:

- 候选栏。
- 主键盘。
- 各行。
- 弹出面板。
- 工具栏。
- 组合窗。

每层支持显示/隐藏和定位。

历史显示最近修改,支持点击历史项查看状态;正式版本使用快照或命令模型恢复,不能只展示静态日志。

## 7. 实时预览实现

### 7.1 统一预览管线

```text
ThemeProject
  -> LuaThemeParser / Model
  -> ThemePreviewController
  -> Native Preview Views
  -> CanvasWorkspaceView
```

编辑器发生修改时:

1. `ViewModel` 更新结构化模型。
2. 记录 `EditCommand`。
3. 标记项目为 dirty。
4. 运行受影响字段的局部验证。
5. 预览控制器执行增量刷新。
6. 必要时重建当前键盘 View。
7. 更新统计、图层和诊断。

### 7.2 设备预览模型

```kotlin
DeviceProfile is defined in the shared preview contract above.
```

设备切换必须实际影响:

- 预览容器宽高。
- 键盘行宽。
- 键盘高度。
- `rows` 百分比计算。
- `keys` 绝对定位。
- `flex_box` 尺寸和权重。
- 背景图片裁剪区域。
- 候选栏和工具栏空间。
- 组合窗边界。

设备预设只是预览模拟,不等于实际获取系统屏幕尺寸;正式版本同时提供“跟随当前设备”和“自定义尺寸”。

### 7.3 预览状态

支持切换:

- 中文。
- ASCII。
- 组字中。
- 候选菜单。
- 翻页。
- 长按。
- 按下态。
- 符号面板。
- 剪贴板面板。
- 展开候选。
- 夜间样式。

状态可以组合,但互斥状态需要由模型定义。例如长按和按下可以同时存在,符号面板和普通键盘需要按面板切换。

### 7.4 交互预览

- 点击按键选中并显示属性。
- 长按显示 `popup`。
- 左右上下滑动显示方向事件和提示。
- 点击候选切换候选状态。
- 点击工具栏按钮切换面板。
- 拖动画布改变平移值。
- 缩放按钮和双指缩放改变画布比例。
- 适应按钮自动计算可用区域。

### 7.5 溢出分析

溢出分析器检查:

- 文字是否超过键宽。
- 图标是否超过键边界。
- 行总宽度是否超过容器。
- 行总高度是否超过键盘高度。
- 绝对布局按键是否越界。
- 弹性容器固定尺寸和 `grow` 是否冲突。
- 工具栏和候选栏是否遮挡键盘。
- 组合窗是否超出设备模拟边界。

问题显示为红色;可疑但仍可加载的情况显示为黄色。

## 8. 布局编辑能力

### 8.1 `rows`

作为第一优先布局完整支持:

- 增加行。
- 删除行。
- 复制行。
- 调整行顺序。
- 设置行高度。
- 设置行默认宽度。
- 增加按键。
- 删除按键。
- 复制按键。
- 跨行移动。
- 单键宽高。
- 自动均分。
- 多选和批量设置。

### 8.2 `flex_box`

支持:

- 横向/纵向容器。
- 容器嵌套。
- 固定宽高。
- `grow`。
- 容器样式。
- 容器内按键管理。
- 固定尺寸和权重冲突提示。

### 8.3 `keys`

支持:

- 自由拖动。
- X/Y 百分比。
- 宽高百分比。
- 网格吸附。
- 多选移动。
- 对齐和等距。
- 越界提示。

### 8.4 `key_maps`

支持:

- 符号页增删复制。
- 页名修改。
- 排序。
- 页内符号按键增删复制。
- 字符选择器批量添加。
- Tab 和工具栏预览。

## 9. Lua 解析、生成和代码页

### 9.1 解析原则

- Lua 语法错误必须定位到文件和行号。
- 可解析字段进入结构化模型。
- 未识别字段保留原始 Lua 节点。
- 不可靠字段进入兼容性报告。
- 用户注释尽量保留。
- Lua 保留字使用正确方括号形式,例如 `['end']`。

### 9.2 生成原则

- 生成稳定字段顺序。
- 字符串正确处理单引号、换行和反斜杠。
- 颜色输出为 ARGB 十六进制。
- 空表、空数组和 `nil` 不能混淆。
- 资源输出为主题相对路径。
- 删除按键必须删除真实布局节点。
- `rows > flex_box > keys > key_maps` 顺序不能改变。

### 9.3 代码页

高级用户可以切换到 Lua 代码页:

- 语法高亮。
- 行号。
- 搜索替换。
- 错误定位。
- 从代码重新解析模型。
- 从模型重新生成代码。
- 无法解析的代码保留并显示警告。

如果结构化模型和手工 Lua 发生冲突,必须给出选择:保留手工代码、覆盖为结构化模型或取消。

### 9.4 高级回调处理规则

编辑器在主题入口页显示以下 7 个回调,但默认以只读源码和风险标记形式呈现:

- `onWindowShown()`。
- `onWindowHidden()`。
- `onStartInput(editorInfo, restarting)`。
- `onFinishInput()`。
- `onConfigurationChanged(configuration)`。
- `onDestroy()`。
- `onSpeechResults(text)`。

`onSpeechResults(text)` 的静态说明必须保留:返回 `false` 取消提交,返回字符串替换识别结果,返回 `nil` 或其他值继续提交原文本。生命周期回调和语音回调不进入普通可视化预览执行链;用户只能在代码页查看、编辑、启用或禁用,并在保存前确认风险。

## 10. 资源管理和权限

### 10.1 资源目录

项目目录固定支持:

```text
fonts/
images/
sounds/
scripts/
```

### 10.2 图片

支持:

- 键盘背景图。
- 按键背景图。
- 按下态背景图。
- 候选栏背景图。
- 工具栏背景图。
- 符号/剪贴板面板背景图。
- 组合窗背景图。
- 标签同名 PNG 图标。

资源管理显示文件名、类型、尺寸、大小、引用者、冲突和删除风险。

### 10.3 字体

支持单字体和 fallback 字体数组。显示 Android 10/API 29 兼容提示,并提供中英文、数字、符号覆盖预览。

### 10.4 音效和振动

支持音效导入、引用检查;`vibration_effect` 提供简单数组编辑或保留原文。振幅限制为 `0..255`,两个数组按较短长度匹配。不实现可视化波形编辑器和试听功能。

### 10.5 Storage Access Framework

- 创建项目时使用用户选择的目录。
- 打开目录时请求持久化 URI 权限。
- 导入 ZIP 时解压到应用项目目录。
- 导出 ZIP 时使用系统保存文件选择器。
- 安装到 Trime2 时使用用户授权目录。
- 权限失败时保留内存模型,不能丢失编辑内容。

## 11. 验证系统

### 11.1 语法诊断

- `main.lua` 加载错误。
- 样式 Lua 加载错误。
- 键盘 Lua 加载错误。
- 脚本加载错误。
- 括号、字符串和表结构错误。

### 11.2 结构诊断

- 默认样式不存在。
- 默认键盘不存在。
- 布局为空。
- 多个布局字段冲突。
- 行宽/高度异常。
- 样式引用不存在。
- 预设键引用不存在。
- 按键缺少有效事件。
- 弹性容器嵌套异常。

### 11.3 资源诊断

- 图片不存在。
- 字体不存在。
- 音效不存在。
- 文件名不兼容。
- 未使用资源。
- 同名资源冲突。
- 删除资源仍有引用。

### 11.4 兼容性诊断

每条诊断包含:

- 严重级别。
- 文件路径。
- Lua 路径。
- 行号(可用时)。
- 当前行为。
- 推荐处理方式。
- 是否影响预览。
- 是否影响导出。

## 12. 撤销、重做和保存

### 12.1 命令模型

所有修改通过 `EditCommand` 执行:

- `AddKeyCommand`。
- `DeleteKeyCommand`。
- `DuplicateKeyCommand`。
- `MoveKeyCommand`。
- `UpdateKeyStyleCommand`。
- `UpdateEventCommand`。
- `UpdateResourceReferenceCommand`。
- `UpdateDeviceProfileCommand`。
- `UpdateLayoutCommand`。

### 12.2 合并策略

- 连续拖动合并为一次移动。
- 颜色拖动合并为一次颜色修改。
- 滑块拖动合并为一次属性修改。
- 文本输入在失焦或停顿后生成一次命令。
- 设备预览切换不污染主题撤销栈,只记录预览历史。

### 12.3 保存

- 自动保存内存草稿。
- 用户点击保存写入文件。
- 保存失败保留 dirty 状态。
- 保存成功清除 dirty 状态。
- 导出前自动执行验证。
- 导出失败显示具体文件和权限原因。

## 13. 导入、导出和安装

### 13.1 导入

支持:

- 主题目录。
- ZIP 包。
- 单个 `main.lua`。
- 图片。
- 字体。
- 音效。

导入报告显示文件数、资源数、缺失引用、语法错误和不支持字段。

### 13.2 导出

支持:

- 完整主题目录。
- ZIP 主题包。
- 仅 Lua。
- 仅资源。
- 当前键盘。
- 当前样式。
- 兼容性报告。

导出选项:

- 是否包含字体。
- 是否包含图片。
- 是否包含音效。
- 是否包含脚本。
- 是否移除未使用资源。
- 是否保留注释。
- 是否输出诊断报告。

### 13.3 安装到 Trime2

在用户授权后:

1. 创建备份。
2. 复制主题目录。
3. 校验 `main.lua`、默认样式和默认键盘。
4. 请求 Trime2 刷新或提示用户刷新主题。
5. 显示安装路径和验证结果。

## 14. 安全和稳定性

- 不自动执行未知主题脚本。
- Lua 回调和脚本运行在受控环境。
- 不上传用户主题、输入内容或资源。
- 文件访问仅限用户授权目录。
- 导入 ZIP 防止路径穿越。
- 限制单个资源文件大小。
- 图片解码使用尺寸保护,避免内存溢出。
- 解析失败不能导致应用崩溃。
- 预览异常时显示降级 View。
- 保存采用临时文件加原子替换。

## 15. 一次性开发顺序

### 阶段一:工程和数据基础

1. 在现有 `:app` 内建立 `com.osfans.trime.editor` 包和非导出的 `ThemeEditorActivity`。
2. 修复无 `API_KEY`、`API_ID`、`signKeyFile` 时的开发构建阻塞,运行现有测试并记录基线。
3. 从 `PrefLauncher` 增加主题编辑器入口,确认输入法 Service 和现有入口不回归。
4. 建立项目目录访问、SAF、草稿和受控分享缓存层。
5. 建立主题、样式、键盘、按键、事件、资源模型及 `ThemeFieldRegistry`。
6. 接入 Lua 解析、写出、`ThemeDefaults`、`FallbackTrace` 和诊断模型。
7. 建立 ViewModel、`SavedStateHandle`、项目仓库、revision 和 dirty 状态。

完成标准:debug APK 可构建并启动编辑器入口;能够打开一个主题目录,解析并展示项目摘要,保存后内容不丢失;输入法基础入口仍可使用。

### 阶段二:原生画布和基础预览

1. 实现 `CanvasWorkspaceView`。
2. 实现顶部工具栏。
3. 实现左侧工具箱。
4. 实现中央 `rows` 键盘预览。
5. 实现按键点击选中。
6. 实现右侧属性抽屉。
7. 实现手机端底部工具箱和属性抽屉。
8. 实现缩放、平移和适应画布。
9. 实现竖屏、横屏和自定义尺寸预设。

完成标准:可在不同设备预设下查看同一个主题,选中按键后属性与预览同步。

### 阶段三:按键和布局编辑

1. 增加按键。
2. 删除按键。
3. 复制按键。
4. 移动按键。
5. 调整宽高。
6. 增加、删除、复制和移动行。
7. 实现多选和批量样式。
8. 实现 `flex_box` 基础编辑。
9. 实现 `keys` 绝对编辑。
10. 实现 `key_maps` 分页编辑。

完成标准:修改后的布局模型、预览和生成 Lua 三者一致。

### 阶段四:样式、事件和资源

1. 基础样式。
2. 普通态和按下态。
3. 提示层和预览层。
4. 点击、长按和四方向滑动。
5. 状态替代。
6. 弹出面板。
7. 背景图片和按键图片。
8. 字体、音效和振动。
9. 候选栏、工具栏、符号、剪贴板和组合窗。
10. 资源引用和缺失检查。

完成标准:编辑器可覆盖参数文档中的所有可靠字段,不可靠字段明确标记。

### 阶段五:验证、历史和导出

1. 撤销和重做。
2. 历史面板。
3. 语法检查。
4. 结构检查。
5. 资源检查。
6. 溢出分析。
7. 兼容性报告。
8. 导出目录和 ZIP。
9. 安装和备份。
10. 导出后重新导入闭环测试。

完成标准:从导入到编辑、预览、验证、导出、重新导入的流程闭环可用。

### 阶段六:自动化质量门禁

本阶段只保留适合稳定、重复执行的自动化校验。阶段六仍属于开发和验收阶段,不是上传或发布阶段;在阶段一至阶段六全部退出门禁前,不得创建公开仓库、推送源码、触发最终 GitHub Actions、构建交付 APK 或上传 Artifact:


1. Lua 语法错误和表结构错误。
2. 主题、样式、键盘和资源引用完整性。
3. 布局优先级和布局数据转换。
4. 按键增删改后模型与 Lua 写出一致。
5. 事件、样式继承和资源路径读写一致。
6. 撤销重做命令状态一致。
7. 导出目录和 ZIP 内容结构正确。
8. 导出后重新导入模型不丢失。
9. 不可靠字段兼容性诊断完整。
10. 损坏输入文件不会导致解析进程崩溃。

无法完全自动化或自动化成本过高的测试,统一移至[Trime2主题编辑器人工测试清单.md](Trime2主题编辑器人工测试清单.md),不作为普通 CI 门禁。

每个阶段必须独立满足对应的源码检查、功能验收和自动化测试条件后才能进入下一阶段。阶段性验证不得被误认为最终交付构建;最终 APK 构建、源码上传和 GitHub Actions 只能在全部必做功能完成后统一执行。不得以已上传的半成品、已触发的云端任务或“后续再补功能”替代一次性开发完成。

## 16. 自动化测试计划

本节只定义可在本地或 CI 中稳定重复执行的测试。设备体验、视觉判断、硬件反馈、系统权限故障和真实 Trime2 对照测试不放入本节。

### 16.1 单元测试

- Lua 颜色、尺寸和字符串序列化。
- Lua 保留字写出。
- `rows` 宽高计算。
- `flex_box` `grow` 计算。
- `keys` 百分比定位。
- `key_maps` 页数据写出。
- 事件字段读写。
- 样式继承和回退链。
- 默认值和 Lua integer 约束。
- 资源路径查找顺序。
- 诊断级别判断。
- 撤销重做命令。
- 项目模型版本迁移。
- URI 和资源引用模型序列化。

### 16.2 解析和生成集成测试

- 导入主题目录后模型结构完整。
- 导入 ZIP 后目录结构完整。
- `main.lua`、样式 Lua 和键盘 Lua 解析结果一致。
- `rows > flex_box > keys > key_maps` 优先级保持一致。
- 样式继承、缺失回退和默认值保持一致。
- 按键增加、删除、复制和移动后模型与 Lua 写出一致。
- 事件表、预设键和状态替代写出后重新解析一致。
- 图片、字体、音效引用写出后重新解析一致。
- `RawLuaNode` 和 HybridMode 不丢失未知 Lua 内容。
- 兼容性诊断能够识别不可达字段。
- 导出目录和 ZIP 内容清单正确。
- 导出后重新导入结构化模型不丢失。
- 原子保存失败时原文件保持不变。
- 损坏 Lua、损坏 ZIP 和非法资源路径不会导致解析崩溃。

### 16.3 纯逻辑界面测试

只自动检查确定性状态,不承担视觉验收:

- 选中节点后选择状态和模型 ID 同步。
- 属性修改事件更新模型。
- 按键数量、行数和诊断数量统计正确。
- 撤销和重做后模型状态恢复正确。
- 图层显示状态和模型可见性同步。
- 设备预设数据转换为正确的宽高配置。
- 溢出分析器对固定测试尺寸返回正确结果。
- dirty 状态、草稿状态和保存状态转换正确。
- 旧 revision 的解析、预览和诊断结果不会覆盖新模型。
- 自动草稿与手动保存并发时提交顺序和 dirty 状态正确。
- 保存期间继续编辑时,保存成功只清除已提交 revision 对应的 dirty 状态。
- 外部文件校验值变化时进入冲突状态,不会静默覆盖。
- 重复 SAF Activity Result 和重复导出点击保持幂等。
- `SavedStateHandle` 轻量状态与落盘草稿恢复一致。

### 16.4 自动化排除项

以下内容不纳入自动化测试门禁,统一执行人工清单:

- 真实设备方向、屏幕密度和大字体体验。
- 画布、面板和键盘的视觉布局判断。
- 图片、字体、音效和振动的实际体验。
- Android 文件选择器、权限拒绝和磁盘空间不足。
- 真实 Trime2 安装、刷新和主题显示对照。
- 长按、滑动、候选、组合窗和工具栏的完整交互体验。


## 17. 自动化完成标准

自动化测试通过后,应用必须满足。以下条件全部满足前,不得进入最终上传和构建环节:

- 项目模型、解析器、写出器和诊断模块有明确测试覆盖。
- 任何参数字段都能在“可视化编辑、代码编辑、原文保留”三者之一找到归属。
- 所有可靠字段均能完成模型、写出和诊断闭环。
- 所有不可靠字段均有兼容性说明。
- 导入项目不修改原文件直到用户明确保存。
- 保存失败不会破坏原主题。
- 导出的主题重新导入编辑器后结构不丢失。
- 导出目录、ZIP 和资源引用通过结构校验。
- 解析失败、资源损坏和非法输入不会导致解析进程崩溃。
- 设备、视觉、硬件、权限和真实 Trime2 对照项目必须按[Trime2主题编辑器人工测试清单.md](Trime2主题编辑器人工测试清单.md)另行完成。


## 18. 后续开发执行规则

后续正式开发按本计划书执行。上传和最终构建是最后环节,不得提前执行:

1. 先读取现有 Android 工程结构和 Gradle 配置。
2. 不擅自升级依赖和改动 Trime2 核心逻辑。
3. 先建立模型、解析器和预览边界。
4. 再实现画布和基础 `rows` 编辑。
5. 每完成一个模块立即运行对应测试和静态验证。
6. 所有新增字段必须有模型、UI、预览、写出和诊断路径。
7. 所有不可达字段必须在 UI 中显示兼容性说明。
8. 不以 HTML 原型替代 Android 实际实现;HTML 只作为布局和交互参考。
9. 每轮开发保持改动小、可回滚、可验证。
10. 最终以导出主题在 Trime2 中实际读取为验收依据。
11. 在所有必做功能、自动化测试、人工测试、兼容性诊断和交付材料完成并复核前,只允许在工作区内开发和验证,不得创建公开仓库、推送源码、上传文件或触发最终云端构建。
12. 最终环节严格按以下顺序执行:冻结源码和文档 -> 复核功能清单零缺口 -> 完成自动化与人工验收 -> 创建公开 GitHub 仓库 -> 推送完整源码和工作流 -> 触发 GitHub Actions -> 检查构建、测试和 Artifact -> 记录最终 commit、构建号和结果。任一步失败都必须回到工作区修复后重新进入该最终环节,不得上传不完整版本。

## 19. 查漏补充:参数覆盖矩阵

以下矩阵用于防止“文档写了,但产品没有入口”或“界面有入口,但 Lua 没有写出”的问题。每个可编辑字段必须同时具备模型、编辑器入口、预览映射、Lua 写出和验证规则;只保留原文的字段必须明确标记为高级代码字段。

| 参数区域 | 模型 | 可视化编辑 | 实时预览 | Lua 写出 | 备注 |
|---|---|---|---|---|---|
| `main.lua` 元数据 | 必须 | 必须 | 部分 | 必须 | `name`、`author`、`style`、`keyboard` |
| `action_labels` | 必须 | 必须 | 必须 | 必须 | 预览不同 Editor Action |
| `preset_keys` | 必须 | 必须 | 必须 | 必须 | 支持引用和内联事件 |
| `get_keyboard` | 保留规则和源码 | 代码页编辑 | 方案状态模拟 | 必须 | 动态 Lua 逻辑不可盲目重写 |
| 生命周期/语音回调 | 原文保留 | 代码页和风险提示 | 不承诺 | 必须 | 不在普通视觉编辑器中执行 |
| 通用样式 | 必须 | 必须 | 必须 | 必须 | 颜色、尺寸、背景、字体和继承 |
| `pressed` / `hint` / `long_click` / `preview` | 必须 | 必须 | 必须 | 必须 | 按状态切换验证 |
| `rows` | 必须 | 必须 | 必须 | 必须 | 第一优先布局 |
| `flex_box` | 必须 | 必须 | 必须 | 必须 | 固定尺寸和 `grow` 诊断 |
| `keys` | 必须 | 必须 | 必须 | 必须 | 百分比绝对布局 |
| `key_maps` | 必须 | 必须 | 必须 | 必须 | 页签和符号工具栏 |
| 按键事件 | 必须 | 必须 | 必须 | 必须 | 七种有效事件 |
| `combo` / `swipe` | 必须 | 仅兼容性入口 | 不承诺 | 必须 | 不伪造触发效果 |
| `candidate` / `toolbar` | 必须 | 必须 | 必须 | 必须 | 默认值和回退链必须保留 |
| `symbol` / `clipboard` | 必须 | 必须 | 必须 | 必须 | `keys` 类型差异需单独处理 |
| `preedit` / `composition` | 必须 | 必须 | 必须 | 必须 | 位置、窗口组件和格式化字段 |
| 图片/字体/音效 | 必须 | 必须 | 必须 | 必须 | 路径查找顺序和缺失诊断 |
| 不可靠示例字段 | 原文保留 | 警告入口 | 灰度或不预览 | 原样保留 | 不从模型中静默删除 |

### 19.1 必须固定的回退和优先级

实现中不得使用编辑器自定义的默认值覆盖 Trime2 行为,至少固定以下规则:

- 键盘视图选择顺序为 `rows > flex_box > keys > key_maps`。
- 样式缺失时保留 Trime2 的继承和回退关系。
- 样式文件加载失败时提示并记录回退到 `styles/light/main.lua`。
- `popup` 缺失时回退到键盘样式,弹出按键缺失时回退到全局 `key`。
- `candidate.expanded.tool_bar.keys`、`symbol.tool_bar.keys` 和 `clipboard.tool_bar.keys` 按字符串数组处理。
- 背景图片、字体和音效的查找顺序与参数文档一致。
- 尺寸写出前校验 Lua integer 要求,不能把界面浮点值直接写成会被 Trime2 回退的值。
- `composition.max_entries`、候选高度、弹出列数等默认值从实际源码或参数文档集中读取,不得分散硬编码。

## 20. 独立 APK 与 Trime2 复用边界

### 20.1 当前仓库实施方案

首版固定在现有 `:app` 模块内实现:

```text
:app
└── com.osfans.trime.editor
    ├── project / model / parser / writer / validation
    ├── preview
    ├── workspace / inspector / resource / history
    └── ThemeEditorActivity
:codegen
```

原因:当前主题加载、LuaJ/AndroLua、KeyboardView、`LuaApplication`、Config 和资源查找均直接位于应用模块并依赖 Android 运行时;立即拆成 `theme-core` 会同时触及约 590 个 Java 文件、JNI 构建和输入法行为,不符合首版小范围改动原则。

纯模型、`ThemeFieldRegistry`、`ThemeDefaults`、写出器和验证器保持无 View/Service 依赖。后续只有在测试覆盖稳定后才允许抽取 Android Library;不能创建与现有实现重复的第二套 LuaJ 或 ThemeManager。

### 20.2 独立 APK 方案

如果必须做独立 APK:

- 复制并隔离当前 Trime2 主题解析代码。
- 为解析器增加 `TrimeCompatibilityVersion`。
- 在导出项目中写入来源版本和提交号。
- 预览使用 `PreviewRuntime` 模拟 Rime 状态,不假设当前系统已安装 Trime2。
- 不直接调用 Trime2 私有 API。
- 对复制的 Java 逻辑建立差异清单,后续 Trime2 升级时重新审计。

独立 APK 不能宣称“预览一定等于 Trime2”,除非使用同一版本的解析和 View 实现并通过设备对比测试。

### 20.3 版本兼容

项目元数据必须保存:

```text
trimeThemeFormatVersion
trimeSourceCommit
editorSchemaVersion
```

打开项目时检测来源版本;版本不匹配时显示“解析器版本可能不同”警告,并提供只读预览和复制为新项目选项。

## 21. Lua 动态内容和双向同步边界

### 21.1 结构化可编辑范围

普通可视化编辑器只修改以下结构化内容:

- 字面量字符串、数字、颜色、布尔值。
- 普通 Lua table。
- 可识别的事件字符串和事件表。
- 可识别的布局数组。
- 可识别的资源引用。

### 21.2 必须原文保留的内容

以下内容不应被自动格式化覆盖:

- `require`、模块函数和动态计算。
- 使用外部变量生成布局的 Lua。
- 自定义函数和复杂条件。
- 生命周期和语音回调函数体。
- 未识别的 Lua userdata。
- 编辑器无法安全解析的 table 表达式。

导入后将这些节点登记为 `RawLuaNode`,代码页显示来源范围;模型修改与原文修改冲突时必须让用户选择处理方式。

### 21.3 生成策略

采用三种写出模式:

1. `PreserveMode`:最大限度保留原文和注释,只替换确认安全的节点。
2. `StructuredMode`:由模型完整生成稳定格式 Lua。
3. `HybridMode`:结构化字段生成,未知区域原文拼接。

默认使用 `HybridMode`。只有新建项目或用户明确确认时才使用 `StructuredMode`。

## 22. 预览运行时边界

### 22.1 PreviewRuntime

预览运行时提供:

- 模拟 Lua 全局环境。
- 模拟主题目录和资源查找。
- 模拟 Rime 状态:中文、ASCII、组字、候选菜单、翻页和方案切换。
- 模拟 Editor Action。
- 模拟屏幕方向和密度。
- 模拟当前候选数据和注释。
- 模拟长按、滑动和按下状态。

### 22.2 不得伪造的行为

以下内容没有实际触发路径时,预览必须显示“未实现”或“当前版本不可达”:

- `combo`。
- `swipe` 滑动点按。
- 无 Java 消费证据的样式字段。
- 依赖真实 Rime schema 的行为。
- 依赖真实 EditorInfo 的生命周期行为。
- 依赖设备硬件的振动强度和音效效果。

### 22.3 预览与真机对照

提供“预览诊断”信息:

- 当前使用的 Trime2 源码提交。
- 当前 LuaJ/解析器版本。
- 模拟设备参数。
- 使用的字体、图片和音效路径。
- 与实际 Trime2 可能不同的字段。

## 23. 项目持久化、迁移和恢复

### 23.1 项目状态

项目保存分为三层:

- 原始文件层:用户导入的原始 Lua 和资源。
- 编辑模型层:当前结构化模型和 `RawLuaNode`。
- 草稿层:自动保存的临时快照和撤销信息。

草稿不得覆盖原始文件。用户点击保存时才写入目标目录。

### 23.2 原子写入

保存流程:

1. 在项目目录创建临时文件。
2. 写入 Lua 和资源变更。
3. 重新读取临时文件并解析验证。
4. 验证成功后替换目标文件。
5. 验证失败则保留原文件和临时文件。
6. 向用户显示失败文件、行号和原因。

ZIP 导出也必须先写临时 ZIP,校验后再交给系统保存。

### 23.3 数据库和迁移

项目索引、最近项目、草稿和设置可以使用 Room 或项目已有持久化方案。结构化模型必须带 `editorSchemaVersion`,每次升级提供迁移器;不能直接清空旧项目。

至少覆盖:

- 新增字段的默认值。
- 旧布局模式转换。
- 旧资源 URI 失效。
- 旧解析器生成的未知节点。
- 撤销栈版本不匹配时丢弃撤销栈但保留项目。

## 24. 性能预算和内存策略

### 24.1 交互性能

目标:

- 普通属性修改到预览刷新:目标小于 100 ms。
- 输入框连续输入使用 150 至 300 ms 防抖。
- 拖动按键或画布时不触发完整 Lua 重写。
- 设备切换完成后目标小于 300 ms。

### 24.2 资源性能

- 图片导入时读取尺寸后再决定解码采样率。
- 预览图和导出原图分离。
- 大图片生成缩略图并使用缓存。
- 字体只在需要的预览区域加载。
- 主题解析和 ZIP 解压放到后台线程。
- 所有 View 更新回到主线程。

### 24.3 列表和画布

- 资源、历史、命令列表使用惰性或回收列表。
- 键盘按键数量较大时避免每次输入都重建整棵 View 树。
- 预览重建使用 diff 或布局区域刷新。
- 撤销快照可使用结构化 patch,大项目不默认复制所有图片二进制。

## 25. Android 权限、生命周期和系统行为

### 25.1 权限

- 优先使用 SAF,不申请不必要的存储权限。
- Android 13 及以上按系统选择器处理图片和媒体访问。
- 文件 URI 不持久时提示用户重新授权。

### 25.2 生命周期

- ViewModel 使用 `SavedStateHandle` 保存轻量导航状态和草稿 ID,不得把完整主题模型或图片二进制塞入 Bundle。
- Activity 进入后台时调度增量草稿持久化,不能在 `onStop()` 主线程同步写完整项目。
- Android 不保证进程被杀前回调;恢复能力必须依赖已经落盘的草稿和 `SavedStateHandle`,不能依赖“被杀前保存”。
- 旋转屏幕不能丢失当前选择、缩放、平移和属性 Tab。
- 分屏和窗口大小变化触发设备预览重算。
- 打开资源选择器时避免重复创建编辑器状态。
- `ViewModel.onCleared()` 取消仅属于当前工作区的解析、缩略图和预览任务;已进入原子提交阶段的文件写入按保存协调器规则完成或回滚。
- SAF Activity Result 在 Activity 重建后仍按请求 ID 关联到原操作,重复结果不得重复导入或导出。

### 25.3 可访问性和国际化

- 所有图标按钮提供 string resource 形式的 content description。
- 不能只用颜色表达错误、选中和兼容性状态。
- 支持系统字体缩放下关键文字不截断。
- 中文为首要界面语言,所有用户可见字符串进入资源文件,不散落在 Kotlin/Java 代码中。
- Lua 源码数字写出固定使用 Locale.ROOT 规则。
- 键盘预览按键提供无障碍描述、事件摘要和选中状态。
- 触控操作目标不小于 48dp。
- 首版不承诺完整 TalkBack、RTL 和降低动画支持,相关限制写入应用内已知限制。
## 26. 风险清单和应对方案

| 风险 | 影响 | 应对 |
|---|---|---|
| Lua 动态代码无法结构化解析 | 修改后破坏原主题 | `RawLuaNode`、HybridMode、冲突确认 |
| 编辑器解析器与 Trime2 版本不一致 | 预览和真机不同 | 源码提交标记、版本诊断、真机对照 |
| 独立 APK 无法复用输入法上下文 | 预览状态不完整 | `PreviewRuntime` 模拟状态并明确差异 |
| 图片/字体过大 | OOM 或卡顿 | 采样、缓存、尺寸限制、后台处理 |
| SAF 权限丢失 | 无法保存或安装 | 持久化 URI、重新授权入口、草稿保留 |
| 导出覆盖用户文件 | 数据丢失 | 临时目录、备份、原子替换 |
| 系统字体缩放改变尺寸 | 布局溢出 | 设备预览、大字体测试、溢出分析 |
| Trime2 不消费某字段 | 用户误判配置生效 | 字段兼容性诊断、灰度入口 |
| 回调或脚本执行风险 | 隐私和稳定性 | 默认不执行、受控环境、权限提示 |
| 历史快照过大 | 内存增长 | patch 命令、上限、定期压缩 |
| 横屏空间不足 | 面板遮挡预览 | 响应式三栏、抽屉化属性、适应画布 |

## 27. 开发交付物

一次性开发结束必须交付:

- 可安装的 Android APK。
- 可重复构建的源码和 Gradle 配置。
- 主题模型、解析器和 Lua 写出模块。
- 原生实时预览模块。
- 资源管理和 SAF 模块。
- 单元、集成和 UI 测试。
- 示例主题项目。
- 导入、编辑、导出和重新导入测试样本。
- 兼容性诊断报告样例。
- 用户操作说明。
- 已知限制和 Trime2 版本兼容说明。
- 发布前签名和版本信息记录。
- 最终公开 GitHub 仓库、完整源码、GitHub Actions 工作流、构建日志、测试报告和 APK Artifact;这些交付物只能在全部功能一次性开发完成并通过验收后生成。

## 28. 计划书的完成定义

只有满足以下条件,才算“一次性开发完成”;在此定义满足前,任何上传、公开推送、最终构建和 APK 交付行为均视为违规:

- 计划书中的必做模块都有代码实现或明确测试覆盖,不存在以 TODO、占位界面、半成品入口或“后续补齐”代替实现的功能。
- 任何参数字段都能在“可视化编辑、代码编辑、原文保留”三者之一找到归属。
- 所有可靠字段均能完成模型、UI、预览、写出和诊断闭环。
- 所有不可靠字段均有兼容性说明。
- 导入项目不修改原文件直到用户明确保存。
- 保存失败不会破坏原主题。
- 导出的主题重新导入编辑器后结构不丢失。
- 导出的主题在目标 Trime2 版本中可以读取。
- 关键操作不依赖网络,无网络状态下仍可完成本地编辑和导出。
- 自动化测试范围内的结构、解析、生成、迁移和错误恢复检查全部通过。
- 设备、视觉、硬件、权限和真实 Trime2 对照项目按[Trime2主题编辑器人工测试清单.md](Trime2主题编辑器人工测试清单.md)完成。
- 已知限制、版本差异和未实现行为在应用内可见。
- 必做功能清单、字段覆盖报告、自动化测试、人工测试和交付材料已由开发者逐项复核并记录结果;必做功能缺口为零。
- 上述条件全部满足后,才允许执行最终公开仓库创建、完整源码推送、GitHub Actions 构建测试、APK Artifact 上传和交付记录归档;这些动作不得提前执行。

## 29. 必做功能覆盖与实现清单

本节是后续开发的防漏清单。每一项都属于必做功能;除明确标注“仅代码/兼容性保留”的项目外,不得只做字段解析而没有用户入口、预览或 Lua 写出。

### 29.1 主题入口和高级逻辑

- 主题名称 `name` 编辑、预览和写出。
- 主题作者 `author` 编辑和写出。
- 默认样式 `style` 选择、缺失检查和写出。
- 默认键盘 `keyboard` 选择、缺失检查和写出。
- 键盘动态选择 `get_keyboard(id, alphabet)` 代码页编辑、源码保留、方案状态模拟和写出。
- `action_labels.none`、`send`、`go`、`done`、`search`、`previous`、`next` 编辑和 Editor Action 预览。
- `preset_keys` 新建、重命名、复制、删除、引用查找、事件字段编辑和写出。
- `onWindowShown`、`onWindowHidden`、`onStartInput`、`onFinishInput`、`onConfigurationChanged`、`onDestroy`、`onSpeechResults` 代码页编辑、保留和风险提示。

### 29.2 样式根和键盘容器

- 样式名称和作者。
- 样式根 `background`、`height`。
- `keyboard.height`、`keyboard.background`、`keyboard.font`。
- 默认 `key` 样式和任意命名样式。
- 样式继承、回退、引用数量、未引用样式和删除保护。
- 样式文件加载失败回退到 `styles/light/main.lua` 的诊断和预览。

### 29.3 通用样式字段

以下字段必须在通用样式编辑器中可编辑,并能应用到按键、提示、候选、工具栏、符号、剪贴板和组合窗支持的样式节点:

- `text_color`、`text_size`。
- `background`。
- `corner_radius`、`stroke_width`、`stroke_color`。
- `elevation`、`shadow_color`。
- `font` 单文件和字体数组。
- `gravity` 及组合值。
- `height`、`show`。
- `padding`、`margins` 的四边值。
- `scale_x`、`scale_y`。
- `translation_x`、`translation_y`、`translation_z`。
- `offset_x`、`offset_y`。
- `long_click_time`、`repeat_click_time`。
- `vibration_enabled`、`vibration_effect`。
- `sound_enabled`、`sound_effect`。

### 29.4 按键状态和提示层

- 普通态与 `pressed` 态完整切换。
- `pressed.scale_x`、`scale_y`、`translation_x`、`translation_y`、`translation_z`。
- `key.hint` 主助记样式。
- `key.hint.up`、`down`、`left`、`right` 方向样式。
- `key.long_click` 长按提示样式。
- `key.preview` 按键预览气泡样式。
- `show` 对文字层、提示层和预览层的显示/隐藏。
- `margins` 与 `padding` 视觉差异预览。
- 图片标签按 `text_color` 染色的预览。

### 29.5 Popup 和按键行为

- `popup` 字符串逐字符拆分。
- `popup` 字符串单字母大小写变体行为。
- `popup` 字符串数组按预设顺序显示。
- `popup.column_count`。
- `popup.key.width`、`popup.key.height`。
- Popup 背景、圆角、边框、阴影和按键样式。
- `click`、`long_click`、四方向 `swipe_*`、`combo` 七种事件入口。
- `label`、`hint`、`description`、`style`、`width`、`height`、`x`、`y`。
- `swipe_repeatable`、`send_bindings`。
- `hint_long`、`hint_left`、`hint_right`、`hint_up`、`hint_down`。
- `composing`、`has_menu`、`paging`、`ascii` 状态替代。
- `{KeyName}` 键名/组合键解析。
- `.lua` 脚本事件识别和代码保留。
- 键盘 Lua chunk 直接返回 Android `View` userdata 时识别为自定义原生布局。
- 自定义 View userdata 项目只提供源码保留、代码编辑、资源检查和只读预览入口。
- 自定义 View userdata 不强制转换为 `rows`、`flex_box`、`keys` 或 `key_maps`,不因打开或导出而丢失原始返回值。
- `label = 'action_labels'`。
- `label = 'schema_name'`。
- `double_click`、`triple_click` 不提供有效配置入口,只提供兼容性说明。
- `combo`、`swipe` 提供保存入口和不可达警告,不伪造预览成功。

### 29.6 事件字段

所有直接事件表和 `preset_keys` 事件必须支持:

- `send`。
- `text`。
- `commit`。
- `command`。
- `option`。
- `select`。
- `toggle`。
- `states`。
- `label`。
- `preview`。
- `description`。
- `shift_lock`。
- `sticky`。
- `repeatable`。
- `functional`。
- `index` 的源码保留和兼容性说明。

工具栏方案开关表还必须支持 `name`、`options`、`states`、`reset` 和独立样式提示。

### 29.7 四种键盘布局

#### `rows`

- 行新增、删除、复制、排序和跨行移动。
- 行 `width`、`height`。
- 行默认 `key_width`、`key_height`。
- 单键 `width`、`height`。
- 行宽、行高总和检查。
- 自动均分和撤销。

#### `flex_box`

- 递归容器新增、删除、复制、排序和拖入。
- `direction` 精确 `column` 规则。
- `width`、`height`、`grow`、`style`。
- 固定主轴尺寸强制 `grow = 0` 的提示。
- 容器背景、阴影和 elevation 预览。

#### `keys`

- X/Y/宽/高百分比编辑。
- 拖动、缩放、网格吸附、对齐、等距和锁定。
- 越界检测。

#### `key_maps`

- 分页新增、删除、复制、排序。
- `name` 页名和缺省序号标题。
- 每页按键增删复制排序。
- Tab 栏、Tab 指示器、符号文本和工具栏预览。

### 29.8 候选栏和工具栏

- `candidate.height`。
- 候选背景、文字、字体、字号、阴影。
- 候选按下态。
- 候选 `comment` 和 `comment.pressed`。
- 候选展开按钮 `candidate.key.text`、图标、样式和按下态。
- `candidate.expanded` 基础样式和继承。
- `filter_bar.show`、`filter_bar.gravity`。
- 展开工具栏位置和字符串按键数组。
- `toolbar.schema_switches`。
- `toolbar.hide` 字符串或样式表、`toolbar.hide.text`。
- `toolbar.key` 和 `toolbar.keys`。
- 直接事件表、预设键名和方案开关项。
- 方案开关 `name`、`options`、`states`、`reset`。
- `candidate.expanded.tool_bar.keys` 不提供直接事件表入口。

### 29.9 符号、剪贴板和预编辑

- `symbol` 基础样式。
- `symbol.text`、`symbol.key`、`symbol.key.pressed`。
- `symbol.indicator_color`。
- `symbol.tab_bar.gravity`、`height`、`indicator_color`。
- `symbol.tool_bar.gravity`、`height`、字符串 `keys`。
- 符号 Tab 未选中/选中文字颜色回退链。
- `clipboard` 基础样式。
- `clipboard.item` 和四边 padding。
- `clipboard.tab_bar`。
- `clipboard.tool_bar` 字符串数组和默认撤销按钮。
- 剪贴板 Tab 文字颜色回退链。
- `preedit.inline` 的 `none`、`input`、`preedit`、`composition`、`preview`、`true`。
- 预编辑颜色、背景、字体和字号。

### 29.10 悬浮组合窗

- `show`、`position`、`movable`。
- `min_length`、`max_length`、`sticky_lines`。
- `max_entries`、`cloud_max_entries`、`all_phrases`、`use_cursor`。
- `min_width`、`min_height`、`max_width`、`max_height`。
- `padding` 四边值。
- `line_spacing`、`line_spacing_multiplier`。
- 组合文字、候选按下态和内部 `key` 样式。
- `composition.window` 组件顺序。
- `when` 的 `paging`、`has_menu` 条件。
- `start`、`end`、`move`。
- `composition`、`label`、`candidate`、`comment`、`sep`。
- `align` 的 `left/normal`、`right/opposite`、`center`。
- `letter_spacing`。
- `click` 事件。

### 29.11 资源查找和导出

- 当前样式目录、主题目录和共享目录的图片查找顺序。
- 当前样式目录、主题 `fonts/` 和共享字体目录查找顺序。
- 当前样式目录和主题 `sounds/` 查找顺序。
- PNG 标签图标自动匹配。
- 图片重命名、删除、引用查找和冲突处理。
- 字体 fallback 和 Android 版本兼容提示。
- 音效引用检查、按键音和长按音效字段编辑。
- 振动 timings/amplitudes 长度匹配和振幅限制。
- 资源相对路径写出。
- 仅 Lua、仅资源、完整主题和 ZIP 导出。
- 导出选项:字体、图片、音效、脚本、未使用资源、注释和诊断报告。

### 29.12 代码和原生实现约束

- 最终应用必须使用原生 Android UI,不能以 WebView 套壳替代。
- `layout-3-canvas.html` 只作为布局参考,不作为运行时预览引擎。
- 预览必须复用或等价实现 Trime2 当前 LuaJ、ThemeManager、Style、KeyStyle、Key、Event、KeyboardView 和 Composition 语义。
- 任何字段新增必须同时登记模型、UI、预览、Lua 写出和诊断责任人/模块。
- 键盘 Lua 直接返回 Android `View` userdata 时,必须走自定义布局兼容路径,不能套用标准布局编辑器。
- 任何无法实现或无法自动触发的字段必须显示兼容性状态。

### 29.13 主题项目和目录管理

- 新建主题向导。
- 复制内置默认主题作为模板,不修改内置目录。
- 创建浅色、深色两个初始模板。
- 主题名称、作者、默认样式和默认键盘初始化。
- 打开主题目录时自动查找 `styles/`、`keyboards/`、`fonts/`、`images/`、`sounds/` 和 `scripts/`。
- 打开单个 `main.lua` 时自动推断相邻主题目录。
- 打开 ZIP 时检查目录根层级并处理单层嵌套目录。
- 最近项目列表。
- 多主题项目切换。
- 项目重命名、复制、关闭和删除前确认。
- 内置主题目录只读保护。
- 外部主题目录覆盖前差异预览。
- 项目打开、保存、导出和关闭前 dirty 状态检查。
- 自动草稿、恢复快照和崩溃恢复。

### 29.14 键盘资产管理和布局迁移

- 键盘列表新增、删除、复制、重命名。
- 键盘名称、作者、样式引用、`lock`、`ascii_mode`、`key_width` 和 `key_height` 编辑。
- 设置主题默认键盘。
- 设置方案 ID 到键盘 ID 的映射。
- 从模板导入完整键盘。
- 在键盘之间复制整行、递归容器、按键和符号页。
- 跨键盘粘贴时生成新的稳定节点 ID,避免引用污染。
- 删除键盘前检查默认键盘、`get_keyboard`、方案映射和其他引用。
- `rows`、`flex_box`、`keys`、`key_maps` 模式切换前显示迁移预览。
- 模式切换前提供复制备份、转换、隐藏原数据和取消四种选择。
- 模式转换失败时保留原布局,不得产生半转换项目。
- 布局字段冲突时显示实际生效优先级。

### 29.15 颜色、尺寸和编辑器操作

- ARGB 十六进制输入。
- 可视化颜色选择器。
- 最近使用颜色。
- 透明度单独编辑。
- dp、sp、百分比、整数和浮点字段按参数类型显示不同控件。
- Lua integer 字段输入小数时给出修正或拒绝提示。
- gravity 组合选择器和原始字符串预览。
- 数字字段范围、负值、空值和 `nil` 语义处理。
- 批量属性编辑前显示影响对象数量。
- 粘贴按键、行、容器和样式时显示目标位置。
- 删除引用对象时显示受影响文件和节点。

### 29.16 脚本和高级代码安全

- `scripts/` 文件列表、导入、重命名、删除和引用查看。
- Lua 回调、命令事件和脚本事件显示执行风险。
- 默认只读查看,启用执行必须单独确认。
- 预览运行时禁止访问网络、任意文件、系统设置和输入法外部私有数据。
- 脚本超时、异常和输出日志隔离处理。
- 导出脚本前显示脚本清单和风险确认。
- 删除脚本前检查 `.lua` 事件、`command` 和主题回调引用。
- 代码页支持跳转到文件、行号和 RawLuaNode 来源。

### 29.17 导入、导出、分享和报告

- 导入前显示文件清单、覆盖项、缺失项、未知字段和冲突项。
- 导入同名文件提供跳过、覆盖、保留两份和取消。
- 导出前显示将写入的文件清单和资源体积。
- 导出到新目录时避免覆盖现有主题。
- 覆盖已有目录前自动备份并提供回滚。
- ZIP 根目录、路径穿越、非法文件名和重复文件检查。
- 生成兼容性报告 Markdown 或文本文件。
- 使用 Android 分享面板分享 ZIP 主题包。
- 导出完成后提供打开目录、分享、重新导入和查看报告入口。
- 安装到 Trime2 前显示目标目录、备份位置和预计覆盖文件。
- 安装完成后验证入口、默认样式、默认键盘和资源引用。

### 29.18 预览数据和状态覆盖

- 中文、英文、ASCII 锁定、数字输入和方案切换。
- 无候选、单候选、多候选、有注释、候选按下和候选翻页。
- 展开候选、筛选栏、候选工具栏和隐藏按钮。
- 普通组合、不同长度组合、分页组合、候选组合和拖动组合窗。
- `action_labels` 不同 Editor Action。
- `schema_name` 当前方案名称。
- `composing`、`has_menu`、`paging` 和 `ascii` 按键替代。
- 长按 Popup 字符串、数组、大小写变体和 Popup 状态样式。
- 预览模式与编辑模式分离,预览模式不能修改主题结构。
- 预览状态重置、状态组合限制和当前状态标识。

### 29.19 第三轮实现边界和防漏规则

#### 事件执行边界

- 事件类型、来源形式、原始 Lua 表达式、源码位置、兼容性级别和是否允许 PreviewRuntime 模拟必须分别保存。
- `command` 和 `.lua` 脚本默认只解析不执行,预览执行必须经过沙箱能力检查。
- `onSpeechResults` 必须区分原文本、替换文本、取消提交和异常返回。
- `repeatable`、`swipe_repeatable`、`sticky` 和 `send_bindings` 必须分别处理。
- 事件执行异常不得中断主题加载,必须转为诊断并保留原事件。

#### 默认值、继承和回退

- UI 显示显式值、继承值、默认值和回退值四种来源。
- 删除显式值恢复继承,不能写入同样的默认值替代。
- 所有回退链保存 `FallbackTrace`,诊断面板显示每一级来源。
- 组件缺失、样式缺失、资源缺失和字段类型错误分别显示。
- 实际默认值由集中 `ThemeDefaults` 提供,禁止在多个控件中重复硬编码。

#### 解析、写出和并发

- 解析、资源索引、ZIP 解压和 Lua 写出在后台线程执行。
- 模型更新通过单一状态流提交,避免预览 View、属性面板和代码页各自维护副本。
- 解析期间显示进度、可取消状态和部分诊断,取消后不替换当前有效模型。
- 写出使用临时文件和原子替换;预览刷新失败保留上一次有效预览。

#### 导入、复制和删除保护

- 项目导入、键盘复制、行复制、容器复制和资源复制生成稳定且不重复的 ID。
- 跨项目复制不得保留来源项目 URI、样式 ID 或资源绝对路径。
- 删除键盘、样式、资源、脚本和预设键前显示全部引用者。
- 导入冲突结果写入导入日志并支持撤销本次导入。
- ZIP 解压限制文件数量、单文件大小、总大小、目录深度和路径长度。

#### 预览和真实行为差异

- 预览显示模拟输入上下文、方案 ID、Editor Action、候选数据和设备参数。
- 预览状态切换不能修改主题模型,不同组件使用不同状态数据。
- 不可达字段显示禁用态和源码位置。
- 自定义 Android `View` userdata、动态 Lua 和生命周期回调进入只读/高级代码路径。

#### 可访问性和数据导出

- 每个可操作按钮有 content description。
- 颜色、选中、警告和错误同时提供图标、文字或语义状态。
- 诊断报告包含主题版本、编辑器版本、Trime2 源码版本、文件路径、Lua 路径、严重级别和修复建议。
- 分享内容只使用用户明确选择的文件,不得默认分享原始输入内容或私有 URI。
### 29.20 第四轮实现闭环

- 所有模型类型有唯一来源和序列化规则。
- 所有公共接口定义成功、失败、取消、权限拒绝和部分结果状态。
- 所有默认值从 `ThemeDefaults` 读取,不在 UI 和 View 中重复定义。
- 所有回退链可追踪到实际文件或默认值。
- 所有资源解析结果保存查找范围和实际命中路径。
- 所有预览组件通过 `PreviewContext` 接收状态,不得读取全局可变输入法状态。
- 所有编辑操作产生可撤销 `EditCommand`,预览状态切换除外。
- 所有后台任务支持取消,取消不会覆盖最后一次有效模型或文件。
- 所有导出结果在交付前重新解析和验证。
- 所有诊断具备稳定 code,便于自动化测试、报告生成和版本迁移。
- 所有 UI 可见文本进入 Android string resources,不可散落在模型和解析器中。
- 所有敏感路径、用户输入、脚本和资源在日志中脱敏或不记录。

### 29.21 第五轮源码保真规则

- `main.lua` 必须先于样式 Lua 处理,样式 Lua 可以读取主题入口已定义的全局变量。
- 显式配置选择的键盘优先于 `get_keyboard(id, alphabet)` 的动态结果,两者冲突时显示来源和最终生效值。
- 样式文件执行失败必须保留原文件、标记诊断并使用内置 light 样式预览。
- `rows`、`flex_box`、`keys`、`key_maps` 同时存在时只允许一个实际预览布局,其他节点保留为隐藏源数据。
- 普通 Lua table 返回值与可转换 Android `View` userdata 返回值必须走不同解析路径。
- 未知字段、未知回调、动态表达式和脚本不得静默删除或自动改写。
- 预设事件引用和直接事件表必须在模型中保留原始来源形式。
- 样式目录、主题目录、共享目录和系统目录的资源查找结果必须可追溯。
- 每个导出文件都必须能够回溯到模型节点或 RawLuaNode 来源。
- 重新导入导出结果时,字段类型、空值语义、注释保留策略和未知节点数量必须进入差异报告。

### 29.22 第六轮精确语义约束

#### 字符串枚举

- `gravity` 编辑器必须提供 `top`、`bottom`、`left`、`right`、`center`、`center_vertical`、`center_horizontal`、`start`、`end`,并支持使用 `|` 组合;必须同时保留无法识别的原始字符串。
- `shift_lock` 提供空值、`click`、`double`、`long`;这里的 `double` 是 Shift 锁定枚举,不能误判为不受支持的 `double_click` 事件。
- `flex_box.direction` 只有精确字符串 `column` 表示纵向,其余值按 `row` 解释并显示规范化提示。
- `preedit.inline` 必须保留 `none`、`input`、`preedit`、`composition`、`preview` 和布尔 `true` 的来源;`preedit`/`composition`、`preview`/`true` 虽语义相近,导出时仍保留原表达形式。
- `composition.position` 提供 `left`、`right`、`left_up`、`right_up`、`drag`、`fixed`、`bottom_left`、`bottom_right`、`top_left`、`top_right`;未知值显示警告并按 `fixed` 预览。
- `composition.movable` 是字符串枚举 `'false'`、`'true'`、`'once'`,不能建模成普通 boolean;导出时必须保留引号和字符串类型。
- `composition.window.align` 支持 `left/normal`、`right/opposite`、`center` 别名组;预览采用规范值,写出尽量保留原值。
- `composition.window.when` 只有 `paging` 和 `has_menu` 产生过滤行为,其他值原样保留并提示不会过滤。

#### 尺寸和单位语义

- Trime2 尺寸通过 `ThemeManager.dp2px()` 处理,当前内部采用 `COMPLEX_UNIT_SP`;编辑器界面必须明确标记“Trime2 尺寸/SP 语义”,不能错误宣称全部为标准 dp。
- `text_size` 和尺寸类字段按 Lua integer 读取;输入浮点值时必须在保存前阻止、取整或要求用户确认,不能静默写出后让 Trime2 回退。
- `rows`、绝对布局和 Popup 中的宽高百分比与 `flex_box` 固定宽高整数不是同一单位类型。
- `rows[i].width` 缺省时优先使用 `key_width`,再使用 `100/首行键数`;`rows[i].height` 缺省时优先使用 `key_height`,再使用 `100/行数`。
- `rows[i].keys[j].width/height` 分别继承所在行的宽高。
- `flex_box.width/height > 0` 时是固定主轴尺寸并使对应 `grow = 0`;`-1` 和非正值必须保留其填充/权重语义。
- `composition.line_spacing_multiplier = 0` 时预览按 `1.0` 处理,原值保留并显示规范化提示。

#### 事件默认和来源语义

- `send`、`text`、`commit`、`command`、`option`、`select`、`toggle`、`label`、`preview`、`description` 缺省为字符串空值,与字段不存在在源码保留模式下仍需区分。
- `sticky = false`、`repeatable = false`、`functional = true` 和 `index = 0` 使用当前源码默认值。
- 事件未指定 `send` 但指定 `command` 时按 function 事件语义预览和诊断。
- `index` 只在直接事件表路径解析,预设键名解析路径不读取,且当前未找到业务消费点;UI 必须标记“可保留但当前无可靠效果”。
- 按键 `style` 缺失时先尝试使用 `click` 字符串作为样式名,再回退到 `key`;属性面板必须显示实际命中的样式来源。
- `hint_long` 和四方向 hint 缺失时使用对应事件自身标签,不是固定空字符串。
- `send_bindings` 缺失且不存在条件事件时自动为 `false`;存在 `composing`、`has_menu`、`paging` 或 `ascii` 时必须按源码路径确定最终行为并显示来源。
- Popup 字符串按字符拆分;单字母标签的大小写变体插入位置取决于按键位置。Popup 数组不自动追加大小写变体。

#### 工具栏和内置键语义

- `toolbar.keys` 支持预设键字符串、直接事件表、完整按键表以及带 `options` 的方案开关表。
- `candidate.expanded.tool_bar.keys`、`symbol.tool_bar.keys`、`clipboard.tool_bar.keys` 只提供字符串数组编辑器,不能复用 `toolbar.keys` 的直接事件表控件。
- 展开候选内置键至少识别 `hide`、`page_up`、`page_down`、`char_filter`。
- 符号工具栏默认键为 `hide`、`page_up`、`page_down`、`BackSpace`。
- 剪贴板工具栏默认键为 `hide`、`page_up`、`page_down`、`undo`。
- `toolbar.hide` 同时支持字符串和样式表两种来源形式。
- 方案开关表的 `style` 虽被读取,当前构造仍使用 `toolbar.key`;编辑器必须显示兼容性提示,不能在预览中伪造独立样式。

#### 特殊资源和标签语义

- 背景颜色整数和背景图片文件名使用联合类型,不能通过字符串后缀猜测全部资源类型。
- 标签同名 PNG 图标先按 label 查找并用 `text_color` 染色;图标大小由 `text_size` 控制。
- `label = 'action_labels'` 只在对应回车事件语义下显示当前 Editor Action 标签。
- `label = 'schema_name'` 在中文模式优先显示当前 Rime 方案名称,其他状态必须显示实际回退标签。
- `{KeyName}` 字符串按键名/组合键解析;以 `.lua` 结尾的字符串按脚本命令事件处理。

### 29.23 参数注册表与自动覆盖门禁

建立 `ThemeFieldRegistry` 作为参数能力的唯一登记表。每个字段路径必须登记:

```kotlin
data class ThemeFieldSpec(
    val path: String,
    val acceptedTypes: Set<LuaValueType>,
    val defaultValue: RawLuaValue?,
    val unit: FieldUnit?,
    val enumValues: Set<String>,
    val aliases: Map<String, String>,
    val fallbackPaths: List<String>,
    val resourceKind: ResourceKind?,
    val consumption: ConsumptionStatus,
    val editorSupport: EditorSupport,
    val previewSupport: PreviewSupport,
    val writeSupport: WriteSupport,
    val diagnosticCodes: Set<String>,
    val sourceEvidence: List<SourceEvidence>,
)
```

注册表至少覆盖以下路径族:

- `main.lua` 的主题元数据、动态键盘、动态标签、预设键和七个回调。
- 样式根、`keyboard`、默认 `key` 和任意命名样式。
- 通用样式、`pressed`、`hint`、四方向 hint、`long_click` 和 `preview`。
- `popup` 及 `popup.key`。
- `candidate`、`candidate.comment`、`candidate.key`、`candidate.expanded`、筛选栏和展开工具栏。
- `toolbar`、`toolbar.hide`、`toolbar.key`、普通 keys 和方案开关表。
- `symbol`、`symbol.text`、`symbol.key`、Tab 栏和工具栏。
- `clipboard`、`clipboard.item`、Tab 栏和工具栏。
- `preedit` 与全部 inline 值。
- `composition`、`pressed`、`key` 和全部 `window` 组件字段。
- 键盘顶层字段、`rows`、`flex_box`、`keys`、`key_maps`。
- 按键基础字段、七种事件、状态替代、Popup、提示和特殊标签。
- 当前不可靠字段和完全不解析字段。

能力状态必须使用明确枚举:

```text
ConsumptionStatus = CONSUMED | PARSED_NOT_TRIGGERED | UNRELIABLE | NOT_PARSED | RAW_ONLY
EditorSupport = VISUAL | CODE_ONLY | READ_ONLY | HIDDEN_INVALID
PreviewSupport = EXACT | SIMULATED | DISABLED_WITH_REASON | NONE
WriteSupport = STRUCTURED | PRESERVE_RAW | REJECT
```

自动覆盖门禁必须检查:

1. 参数文档中的每个字段路径都能在注册表找到。
2. 每个注册字段都有类型、消费状态和写出策略。
3. `VISUAL` 字段必须有属性控件工厂和输入验证器。
4. `EXACT` 或 `SIMULATED` 字段必须有预览映射器。
5. `STRUCTURED` 字段必须有解析和写出往返测试。
6. `UNRELIABLE`、`NOT_PARSED` 和 `PARSED_NOT_TRIGGERED` 必须绑定兼容性诊断 code。
7. `PRESERVE_RAW` 字段必须通过未知节点不丢失测试。
8. 注册表默认值必须与 `ThemeDefaults` 一致。
9. 注册表回退链必须能生成 `FallbackTrace`。
10. 源码提交变化后,注册表必须重新执行消费点审计。

构建流程生成字段覆盖报告,至少包含字段总数、可视化字段数、代码专用字段数、预览精确/模拟/禁用数量、结构化写出数量、原文保留数量和缺失数量。缺失数量不为零时禁止发布构建。

### 29.24 第七轮完整用户工作流约束

#### 页面导航和状态恢复

应用必须提供并连接以下原生页面或工作区入口:

- 项目首页:最近项目、新建、打开目录、导入 ZIP、示例模板和恢复草稿。
- 新建向导:主题元数据、模板选择、默认样式、默认键盘、目标目录和创建预览。
- 主题设置:主题入口、动态键盘、动态标签、预设事件和高级回调。
- 主编辑工作台:键盘、布局、按键、样式、预览、结构、历史和属性。
- 样式管理:样式列表、继承关系、引用者、复制、重命名、替换和删除。
- 资源管理:图片、字体、音效、脚本、筛选、排序、引用和安全删除。
- Lua 代码页:文件树、编辑器、诊断、搜索替换和同步。
- 验证导出页:诊断筛选、兼容性报告、导出选项、安装、分享和结果。
- 设置页:默认项目目录、自动保存、预览设备、主题外观、日志和版本信息。

页面切换、系统旋转、分屏、进入后台和进程重建后必须恢复项目 ID、当前文件、当前键盘、当前样式、选中节点、属性 Tab、画布缩放和平移、预览状态以及未保存草稿。已经失效的节点 ID 必须安全回退到最近有效父节点,不能崩溃。

#### 图片资源导入

图片只做导入、复制、预览和引用检查,不实现裁剪、旋转、EXIF 修正或格式转换。

1. 通过系统相册或 SAF 选择图片,支持 PNG、WebP、JPG/JPEG 和 Android 可解码格式。
2. 复制到目标样式目录或主题 `images/`。
3. 更新资源索引和引用。
4. 标签同名 PNG 图标由 Trime2 自动显示,编辑器检查 label 与文件名一致。
#### 多选、批量编辑和内部剪贴板

- 支持点击追加选择、全选、反选和按行选择。
- 属性面板使用“统一值、混合值、未设置、不可应用”四种状态。
- 批量修改样式、尺寸、背景、文字颜色、事件和资源引用前显示影响数量。
- 一次批量操作生成一个原子 `EditCommand`,撤销时全部恢复。
- 内部剪贴板支持按键、行、Flex 容器、符号页、样式和事件。
- 跨项目粘贴仅限可安全复制的对象;样式、预设键和资源依赖以诊断提示列出,不实现自动映射。
- 系统剪贴板只用于可安全序列化的文本/Lua 片段,不得泄露用户资源 URI 或应用私有路径。

#### Lua 代码与结构化模型同步事务

代码页必须维护“磁盘指纹、结构化模型版本、当前编辑缓冲区”。同步流程:

1. 用户编辑代码时只更新缓冲区并标记代码 dirty。
2. 解析缓冲区到候选模型,不立即覆盖当前工作模型。
3. 展示语法错误、结构差异、RawLuaNode 变化、将丢失的可视化编辑和资源引用变化。
4. 无冲突时用户确认后原子替换工作模型并生成一个可撤销命令。
5. 有冲突时提供保留代码、保留可视化模型或取消。
6. 模型生成代码时保留未修改 RawLuaNode、注释策略和用户选择的 WriteMode。
7. 外部文件发生变化时先比较校验值,不得静默覆盖缓冲区或模型。

动态 Lua 和 RawLuaNode 不自动格式化。搜索替换仅限当前文件。

#### 安装、刷新、备份和回滚

- 安装目标必须由用户通过 SAF 授权,不得猜测或绕过 Trime2 私有目录。
- 安装前检查目标主题同名目录、可写权限、可用空间、入口文件、默认样式、默认键盘和资源完整性。
- 覆盖安装前创建带时间、主题 ID 和校验清单的备份。
- 复制完成后重新读取目标目录并比较文件数量、大小和校验值。
- 若当前 Android/Trime2 没有可靠的公开刷新接口,只显示明确的手动刷新步骤,不能伪造“刷新成功”。
- 安装失败支持继续、重试和从备份回滚;回滚也必须校验。
- 覆盖前自动备份;安装失败提供回滚到该备份,不实现备份管理界面。

#### 分享和 Android 文件暴露

- 仅 ZIP 主题包通过系统分享面板发送。
- 分享使用受控 `content://` URI 和临时读取权限,不暴露 `file://` 路径。
- MIME 类型必须与实际内容一致。
- 分享完成或超时后清理临时副本和临时授权。
- 分享内容只包含用户明确选择的 ZIP 文件。
#### 发布和交付闭环

本节是整个计划的最后环节,不得提前执行。只有第 28 节“一次性开发完成”全部满足,且必做功能清单、自动化测试和人工测试均已关闭缺口,才允许开始本节。发布构建前必须完成:

- debug/release 构建配置分离,release 禁止调试开关和详细敏感日志。
- 固定 `versionCode`、`versionName`、目标 Trime2 源码提交和编辑器 schema 版本。
- 生成可重复构建记录、依赖清单、开源许可、变更日志和已知限制。
- 使用正式签名配置,签名密钥不进入仓库和文档。
- APK 安装冒烟测试、冷启动、项目创建、主题导入、编辑、保存、导出和重新导入通过。
- 字段覆盖报告缺失数为零,自动化测试通过,人工清单结果已记录。
- 示例主题和测试资源不得包含用户私有内容。
- 日志不记录用户输入、资源路径和 URI 等敏感数据;不实现崩溃日志收集和上传。

### 29.25 第八轮现有工程构建与集成约束

#### Gradle 和依赖

- 保持 `settings.gradle.kts` 现有 `:app`、`:codegen` 和 included build `build-logic` 结构;首版不新增应用模块。
- 所有新增库优先登记到 `gradle/libs.versions.toml`,不在多个 build 文件散落版本号;仓库已有直接依赖的历史写法不在本任务中顺便重构。
- 不升级 Gradle 9.2.0、AGP 8.11.0、Kotlin 2.2.0、KSP、SDK、NDK 28.0.13004108 或 CMake 3.31.6。
- 新增 Kotlin 源码遵守 JVM 11 和现有 ktlint/Spotless 配置。
- 编辑器功能不需要新的网络依赖;导入、编辑、预览和导出必须离线工作。
- Room、图片裁剪库、颜色选择器或代码编辑器第三方库必须先评估;若现有组件可实现则不新增。
- 新依赖必须进入 AboutLibraries/许可证输出并检查 GPL-3.0-or-later 兼容性。

#### 测试依赖现状

- `app/src/test` 已有 Kotest 测试源码,但 `testImplementation` 当前在 `app/build.gradle.kts` 中被注释。
- 开发自动化测试前必须恢复 `kotest-runner-junit5` 和 `kotest-assertions-core` 等实际所需依赖,并确保 `useJUnitPlatform()` 可发现测试。
- 不把 JUnit4 的 `androidTestImplementation(libs.junit)` 当成 AndroidX instrumentation runner;UI 测试需明确引入 AndroidX Test、JUnit 扩展和 Espresso 或选定的原生 UI 测试栈。
- 新增测试依赖只服务测试配置,不得打入 release APK。
- 第一次实现任务先运行现有测试建立基线;现有测试失败要区分环境问题、依赖缺失和本次回归。

#### 构建配置阻塞项

当前 `app/build.gradle.kts` 存在必须在开发前处理的配置风险:

- `API_KEY`、`API_ID` 使用强制属性读取,缺失时可能在 Gradle 配置阶段失败。应改为可选安全默认值或仅在需要语音功能的构建中要求,但不得将真实密钥写入源码、计划书或版本库。
- debug/release 均引用 `myCustomConfig`,而该签名配置只在 `signKeyFile` 存在时创建。必须让无正式签名的开发环境可使用默认 debug 签名,release 在缺少签名时给出明确任务错误。
- 签名密码和 API 属性只从本地未跟踪属性或 CI secrets 注入。
- 修改构建脚本前保存现有可构建基线和环境要求文档。

#### 原生构建和 ABI

- `:app` 使用 librime、OpenCC、Lua 扩展和 CMake 原生构建,支持 `armeabi-v7a`、`arm64-v8a`、`x86`、`x86_64`。
- 纯编辑器 Kotlin/Java 开发的日常验证优先使用已有 `app/prebuilt` JNI 或单 ABI `BUILD_ABI` 加快构建;正式 release 必须按发布 ABI 清单构建。
- 不修改 JNI、librime、OpenCC 或其 submodule 提交,除非主题编辑器功能有不可替代的原生需求并另行审批。
- submodule 未初始化或版本不一致必须在构建前诊断,不能让 CMake 在后期才以模糊错误失败。
- 原生缓存 hash、assets checksums 和 OpenCC 数据生成任务必须保持现有依赖关系。

#### Manifest 和入口

- 新增 `ThemeEditorActivity` 使用独立原生主题,`exported=false`,除非后续明确设计受控文件打开 Intent。
- 从现有 `PrefLauncher` 或合适的工具入口增加“主题编辑器”,不替换输入法 Service 和现有启动器行为。
- Activity 声明、configChanges、窗口 Insets、横竖屏和大屏行为以编辑器实际状态恢复能力决定,不能仅靠 `configChanges` 规避生命周期。
- 不新增与编辑器无关的危险权限;SAF 不要求 `MANAGE_EXTERNAL_STORAGE`。
- 现有 Manifest 的宽泛存储权限属于历史行为,编辑器新代码不得依赖它们。

#### FileProvider 和分享安全

- 现有 `provider_paths.xml` 包含 `root-path`、`external-path` 等宽范围。编辑器分享不得直接复用“任意路径均可暴露”的能力。
- 为编辑器建立受控 cache 子目录和专用分享文件生成器;URI 只指向本次用户明确选择的 ZIP 文件。
- 如调整 provider paths,必须先审计现有 ToolActivity 和其他分享功能,避免破坏兼容性;不得在未审计时删除历史路径。
- 分享 Intent 必须设置临时读取权限、ClipData 和正确 MIME,分享结束后按生命周期清理缓存。

#### Java/Kotlin 互操作和线程

- 现有主题核心主要是 Java 静态状态,例如 `ThemeManager` 的 Globals、Style、SoundPool 和 Vibrator。编辑器不能直接在后台线程并发调用会修改全局主题状态的方法。
- 建立 `ThemeRuntimeLock` 或串行调度器隔离“输入法当前主题运行时”和“编辑器临时预览运行时”。
- 预览不得调用会永久修改 `Config.setTheme()`、当前输入法主题或真实 Rime 状态的路径;需要适配器或临时上下文。
- Kotlin 空安全边界必须包装 Java/Lua 可能返回的 null、LuaNil 和异常。
- Android View 创建和更新在主线程,Lua 解析、资源扫描和文件 I/O 在受控后台调度器。

#### 许可证和源码交付

- 当前仓库为 GPL-3.0-or-later,编辑器代码、修改记录和分发方式必须保持许可证义务。
- 新建源码文件添加与仓库一致的 SPDX 头。
- 使用现有 AndroLua、LuaJ、原生库和第三方依赖时保留许可证说明。
- 分发 APK 时同步提供对应源码获取方式、依赖许可证和修改说明。

### 29.26 第九轮实施门禁、状态所有权和并发契约

#### 唯一状态所有权

- `ProjectViewModel` 持有当前 `ProjectSessionState`,UI Fragment/View 只渲染 StateFlow 并提交 action,不得保存第二份可变主题模型。
- `ProjectSessionState` 至少包含 `projectId`、`modelRevision`、`savedRevision`、`draftRevision`、`sourceFingerprint`、dirty、保存状态、解析状态、当前选择和诊断版本。
- `modelRevision` 每次成功 `EditCommand` 单调递增;撤销和重做同样产生新 revision,不能倒退 revision 数字。
- `savedRevision == modelRevision` 才能显示“已保存”;保存旧 revision 成功时不能清除后续编辑产生的 dirty。
- 预览状态拥有独立 `previewRevision`,不进入主题 dirty;设备预设若只是预览不生成 `EditCommand`,若写入项目配置则显式区分。

#### 后台结果防过期

- 解析、诊断、资源索引、缩略图、预览 diff 和代码同步任务携带 `projectId + inputRevision + generationId`。
- 结果提交前比较当前 session;项目已切换、revision 已变化或 generation 已取消时丢弃结果。
- 新解析请求取消旧请求;即使底层 Lua 或图片解码无法即时取消,旧任务完成后也不能提交状态。
- `collectLatest` 只用于可安全取消的派生任务;文件提交、备份和回滚不能因收集器切换而留下半完成状态。
- 后台异常转换为带 operation ID 的 `Diagnostic` 或操作结果,不得通过未捕获异常终止 ViewModel scope。

#### 保存协调器

建立单例作用域的 `SaveCoordinator`,以规范化项目 URI/项目 ID 为 key 使用 `Mutex` 串行化同一项目的草稿、手动保存、导出准备和安装快照。不同项目可并行,但共享 `ThemeRuntimeLock` 的预览解析仍按运行时规则串行。

保存状态机:

```text
Idle -> Preparing -> WritingTemp -> Validating -> Committing -> Succeeded
                              |            |             |
                              +---------- Failed <-------+
Idle/Preparing -> Cancelled
```

约束:

- 进入 `WritingTemp` 前可以安全取消;进入原子 commit 后必须完成提交或回滚,UI 的取消只停止后续工作。
- 保存开始时捕获 `targetRevision` 和源文件 fingerprint。
- commit 前重新比较目标文件 fingerprint;外部变化时进入 `ExternalConflict`,不得覆盖。
- 保存成功只设置 `savedRevision = targetRevision`;若 `modelRevision > targetRevision`,状态仍为 dirty 并可排队保存最新 revision。
- 连续点击保存合并为当前保存加一次 latest-revision follow-up,不能无限排队相同任务。
- 自动草稿写入独立编辑器私有存储,不得与用户目标目录临时文件共用名称。
- 临时文件名包含 project ID、operation ID 和随机部分;启动时清理已确认不属于活动事务的过期临时文件。
- ZIP 导出、安装备份、安装复制和回滚使用同样的 operation journal,便于崩溃后判断完成、重试或清理。

#### 外部修改和多窗口

- 打开项目时记录受管理文件的大小、mtime 和内容 hash;关键 Lua 文件必须使用内容 hash,不能只依赖 mtime。
- 保存前和显式刷新时检查外部变化。
- 外部变化提供重新加载、保留当前编辑或取消保存;冲突时不得覆盖目标文件。
- 同一项目在多窗口打开时,第二个会话只读;不实现多会话共享编辑。
- 最近项目删除、项目重命名和 URI 重新授权必须更新 session registry,不能留下指向旧 URI 的写锁。

#### 生命周期恢复层级

恢复数据按大小分层:

1. `SavedStateHandle`:项目 ID、草稿 ID、当前页面、对象 ID、属性 Tab 和轻量画布参数。
2. 私有草稿文件:结构化模型、RawLuaNode 引用、revision、源码 fingerprint 和未提交命令摘要。
3. 用户项目目录:最后明确保存版本。
4. 可再生成缓存:缩略图、资源索引、预览树和诊断。

恢复时先验证 schema 版本和 checksum;草稿损坏时隔离损坏文件并回退用户已保存版本,同时生成可导出的恢复诊断。撤销栈无法迁移时只丢弃撤销历史,不得丢弃当前草稿模型。

#### 里程碑进入和退出门禁

- M0 构建基线:无私密属性也能配置并构建 debug,现有自动测试可发现,输入法入口冒烟通过。
- M1 只读导入:SAF 打开、解析、字段注册报告和诊断可用;不得写用户文件。
- M2 往返核心:HybridMode、RawLuaNode、临时写入和重新导入测试通过后才能开放保存。
- M3 rows 编辑:选择、属性、EditCommand、撤销和预览闭环通过后才能增加其他布局。
- M4 全布局与样式:四布局、事件、资源、样式回退和组件预览达到注册表零缺口。
- M5 高级代码与恢复:三版本同步、冲突处理、草稿、迁移和并发保存测试通过。
- M6 导出安装:ZIP、安全分享、备份、校验和回滚闭环通过。
- M7 Release:字段报告零缺失、自动测试通过、人工清单有结果、许可证和签名材料齐全;确认全部必做功能一次性开发完成后,才允许创建公开仓库、推送完整源码、触发 GitHub Actions、构建 APK 和上传 Artifact。M7 之前禁止任何最终上传或交付构建。

每个门禁记录构建变体、commit、设备/API、测试报告和已知例外。未满足门禁不得通过隐藏功能、吞掉诊断或标记 TODO 进入下一阶段。

#### 自动化与人工测试归属

- revision、保存状态机、过期结果、幂等、迁移、fingerprint 冲突和草稿恢复属于自动化单元/集成测试。
- Activity recreation 可用 AndroidX instrumentation 自动验证核心状态;进程终止、SAF 系统 UI 和真实外部应用修改保留人工验证。
- content description、无硬编码字符串和最小触控尺寸可增加静态/Lint 检查;TalkBack 完整体验不作为首版门禁。
- 所有人工项必须关联稳定 test ID 和适用 API/设备条件,结果记录通过、失败、阻塞或不适用,不能只保存自由文本结论。

## 30. 参考文件

- [主题Lua参数文档.md](主题Lua参数文档.md)
- [Trime2主题编辑器功能汇总.md](Trime2主题编辑器功能汇总.md)
- [layout-3-canvas.html](design-prototypes/layout-3-canvas.html)
- [Trime2主题编辑器人工测试清单.md](Trime2主题编辑器人工测试清单.md)
- `app/src/main/java/com/osfans/trime/theme/ThemeManager.java`
- `app/src/main/java/com/osfans/trime/theme/Style.java`
- `app/src/main/java/com/osfans/trime/theme/KeyStyle.java`
- `app/src/main/java/com/osfans/trime/Key.java`
- `app/src/main/java/com/osfans/trime/Event.java`
- `app/src/main/java/com/osfans/trime/Composition.java`
