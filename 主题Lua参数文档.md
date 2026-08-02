# Trime2 主题 Lua 可配置参数

> 基于仓库当前 `develop` 分支(提交 `97f0a8d5`)的 Lua 示例和 Java 实际读取逻辑整理。
>
> 主题入口:`app/src/main/assets/themes/default/`。自定义时请复制默认主题目录;不要直接修改内置默认目录,否则更新应用可能覆盖修改。

## 目录和加载顺序

```text
themes/<theme>/
├── main.lua                    # 主题元数据、默认键盘、预设按键、回车标签、回调
├── styles/<style>/main.lua     # 视觉样式
├── keyboards/<keyboard>.lua    # 键盘布局与按键行为
├── fonts/                      # 字体资源
├── images/                     # 图片资源
├── sounds/                     # 音效资源
└── scripts/                    # 自定义按键脚本
```

主题先执行 `main.lua`,再执行 `styles/<style>/main.lua`。样式文件不存在或执行失败时,应用回退到内置 `styles/light/main.lua`。

主题 Lua 中定义的全局变量可在样式 Lua 中使用;样式文件可通过下列方式复用模块:

```lua
require 'styles.light'
require 'styles.light.a'
require 'my_helper'
```

## 数据格式和资源规则

- 颜色为 Android ARGB 整数,例如 `0xff1976D2`。
- 尺寸最终经 `ThemeManager.dp2px()` 转换;实现采用 `COMPLEX_UNIT_SP`,系统字体缩放可能影响尺寸。
- `background` 可为颜色整数或图片文件名。背景图片依次从当前样式目录和主题 `images/` 查找;按键标签图标还会继续查共享数据目录 `images/`。
- `font` 可为单个字体文件名或字体数组,优先查样式目录,再查主题 `fonts/`,最后查共享数据目录 `fonts/`。字体数组仅在 Android 10(API 29)及以上构建 fallback 字体族;更低版本会回退系统默认字体。
- `sound_effect` 优先查样式目录,再查主题 `sounds/`。
- 按键 `label` 若有同名图片(通常为 `<label>.png`),可自动作为图标显示并按 `text_color` 染色。

---

# 1. 主题入口 `main.lua`

```lua
name = '我的主题'
author = '作者'
style = 'light'
keyboard = 'qwerty26'
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `name` | string | 主题选择界面的显示名称。 |
| `author` | string | 作者元数据;当前 Java UI 不读取,仅供 Lua/人工识别。 |
| `style` | string | 默认样式 ID,对应 `styles/<style>/main.lua`。 |
| `keyboard` | string | 默认键盘 ID,对应 `keyboards/<keyboard>.lua`。 |
| `get_keyboard(id, alphabet)` | function | 根据方案 ID 和字母表动态选择键盘,返回键盘 ID。 |
| 生命周期/语音回调 | function | 可选的主题级回调,见下节。 |
| `action_labels` | table | 回车键随编辑器 IME Action 改变的标签。 |
| `preset_keys` | table | 命名的可复用按键/事件定义。 |

## `get_keyboard(id, alphabet)`

```lua
function get_keyboard(id, alphabet)
  if id == '' then
    return keyboard
  end
  if string.find(alphabet, '%d') then
    return 'qwerty36'
  end
  return keyboard
end
```

若不定义该函数,则使用 `keyboard`。配置设置中显式选择的键盘优先于该回调。

## 主题级生命周期与语音回调

主题 `main.lua` 还可以定义以下可选函数:

```lua
function onWindowShown() end
function onWindowHidden() end
function onStartInput(editorInfo, restarting) end
function onFinishInput() end
function onConfigurationChanged(configuration) end
function onDestroy() end

function onSpeechResults(text)
  -- 返回 false:取消提交;返回字符串:替换识别结果;其他返回值:提交原结果
  return text
end
```

| 回调 | 参数 | 返回值/作用 |
|---|---|---|
| `onWindowShown()` | 无 | 输入法窗口显示后调用。 |
| `onWindowHidden()` | 无 | 输入法窗口隐藏后调用。 |
| `onStartInput(editorInfo, restarting)` | `EditorInfo`、boolean | 开始输入时调用。 |
| `onFinishInput()` | 无 | 结束输入时调用。 |
| `onConfigurationChanged(configuration)` | Android `Configuration` | 屏幕方向、夜间模式等配置变化时调用。 |
| `onDestroy()` | 无 | 输入法服务销毁时调用。 |
| `onSpeechResults(text)` | string | 返回 `false` 取消提交;返回 string 替换识别结果;返回 `nil`/其他值时继续提交原文本。 |

回调异常由 Java/LuaJ 调用链处理;主题回调中应避免耗时或不可恢复操作。

## `action_labels`

```lua
action_labels = {
  none = 'Enter',
  send = '发送',
  go = '前往',
  done = '完成',
  search = '搜索',
  previous = '上一个',
  next = '下一个',
}
```

## `preset_keys.<name>`：预设事件

```lua
preset_keys = {
  MyCustomKey = {
    label = '示例',
    send = 'Control+a',
  },
}
```

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---:|---|
| `send` | string | `''` | 发送 Rime/Android 键名或组合键,如 `Return`、`BackSpace`、`Control+a`。 |
| `text` | string | `''` | 直接上屏文本。 |
| `commit` | string | `''` | 提交文本。 |
| `command` | string | `''` | 执行命令或 Lua 脚本;未指定 `send` 时自动作为 `function` 事件。 |
| `option` | string | `''` | 命令参数。 |
| `select` | string | `''` | 选择目标,如键盘、输入法或设置页。 |
| `toggle` | string | `''` | Rime 开关名。 |
| `states` | string array | 无 | `toggle` 对应的状态显示文本。 |
| `label` | string | `''` | 显示文本;取值为 `action_labels` 时显示动态回车标签。 |
| `preview` | string | `''` | 按键预览文字。 |
| `description` | string | `''` | 按键说明。 |
| `shift_lock` | string | `''` | Shift 锁定模式,如 `click`、`double`、`long`。 |
| `sticky` | boolean | `false` | 是否保持锁定。 |
| `repeatable` | boolean | `false` | 是否允许按住重复触发。 |
| `functional` | boolean | `true` | 是否作为功能键处理。 |
| `index` | integer | `0` | 仅直接事件表构造路径会解析;当前项目未找到业务消费点,预设键名解析路径也不会读取它。 |

---

# 2. 样式根 `styles/<style>/main.lua`

```lua
name = '自定义样式'
author = '作者'
background = 0xffdddddd
height = keyboard.height + candidate.height
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `name` | string | 样式选择界面显示名。 |
| `author` | string | 作者元数据;当前 Java UI 不读取,仅供 Lua/人工识别。 |
| `background` | color / image path | 输入法根容器背景;颜色值还用于系统导航栏颜色。 |
| `height` | number | 输入区域总高度,通常为 `keyboard.height + candidate.height`。 |
| `keyboard` | table | 键盘容器样式。 |
| `key` | table | 默认按键样式。 |
| `popup` | table | 长按弹出面板样式。 |
| 任意样式名 | table | 如 `enter`、`functional`、`space`;由按键 `style = '名字'` 引用。 |

## `keyboard`

```lua
keyboard = {
  height = 240,
  background = 0xffdddddd,
  font = 'keyboard-font.ttf',
}
```

| 字段 | 类型 | 代码默认值 | 说明 |
|---|---|---:|---|
| `height` | number | `240` | 主键盘高度。 |
| `background` | color / image path | `0xffdddddd` | 键盘背景。 |
| `font` | string / string array | 系统默认 | 键盘区域使用的字体后备来源。 |

---

# 3. 通用按键样式

以下字段适用于 `key` 以及由按键引用的自定义样式,如 `enter`、`functional`、`space`、`BackSpace`。子样式也会继承父样式。 `text_size` 使用整数读取;尺寸类字段也只有 Lua integer 才会生效,浮点值会回退默认值。

```lua
key = {
  text_color = 0xff000000,
  text_size = 22,
  background = 0xffffffff,
  elevation = 4,
  corner_radius = 8,
  stroke_width = 1,
  stroke_color = 0xffd0d0d0,
  shadow_color = 0x66000000,
  font = 'my-font.ttf',
  gravity = 'center',
  offset_x = 0,
  offset_y = 0,
  long_click_time = 1000,
  repeat_click_time = 200,
  vibration_enabled = true,
  sound_enabled = true,
  sound_effect = 'click.ogg',
}
```

| 字段 | 类型 | 代码默认值 | 说明 |
|---|---|---:|---|
| `text_color` | color | `0xff000000` | 文字或图标染色颜色。 |
| `text_size` | number | `18` | 文字/图标尺寸。 |
| `background` | color / image path | `0xffffffff` | 背景。 |
| `corner_radius` | number | `0` | 背景圆角半径。 |
| `stroke_width` | number | `0` | 边框宽度;大于零时绘制。 |
| `stroke_color` | color | `0` | 边框颜色。 |
| `elevation` | number | `0` | Android elevation/阴影高度。 |
| `shadow_color` | color | `0` | 阴影颜色。 |
| `font` | string / string array | 默认字体 | 字体或字体 fallback 列表。 |
| `gravity` | string | `center` | 内容位置。支持 `top`、`bottom`、`left`、`right`、`center`、`center_vertical`、`center_horizontal`、`start`、`end`,可用 `&#124;` 组合。 |
| `offset_x` / `offset_y` | number | `0` | 内容横向/纵向偏移。 |
| `long_click_time` | integer (ms) | `1000` | 长按触发时间。 |
| `repeat_click_time` | integer (ms) | `200` | 重复触发间隔。 |
| `show` | boolean | `true` | 是否显示该文字层。 |
| `vibration_enabled` | boolean | `false` | 是否启用此样式按键的振动。 |
| `vibration_effect` | `{ timings, amplitudes }` | 无 | Android O+ 波形振动。两个数组按较短长度匹配,振幅限制到 `0..255`。 |
| `sound_enabled` | boolean | `false` | 是否启用按键音。 |
| `sound_effect` | string | 无 | 音效文件名。 |
| `width` / `height` | integer | popup 中为 `10` / `15` | 长按弹出面板中单键的宽高。 |

## `margins` 与 `padding`

```lua
key.margins = { left = 2, top = 2, right = 2, bottom = 3 }
key.padding = { left = 4, top = 2, right = 4, bottom = 2 }
```

| 路径 | 说明 |
|---|---|
| `margins.left/top/right/bottom` | 按键外边距。 |
| `padding.left/top/right/bottom` | 按键内容内边距。 |

## `pressed`：按下态

```lua
key.pressed = {
  background = 0xff888888,
  text_color = 0xffffffff,
  scale_x = 0.9,
  scale_y = 0.9,
  translation_x = 0,
  translation_y = 0,
  translation_z = 8,
  hint = { text_color = 0xffdddddd },
  long_click = { text_color = 0xffdddddd },
}
```

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---:|---|
| `scale_x` / `scale_y` | number | `1.0` | 按下态缩放。 |
| `translation_x/y/z` | number | `0` | 按下态位移与 Z 轴变化。 |
| `background`、`text_color`、`text_size` | 通用样式字段 | 继承父样式 | 按下态文字与背景。 |
| `elevation`、`shadow_color`、`corner_radius`、`stroke_*` | 通用样式字段 | 继承父样式 | 按下态轮廓与阴影。 |
| `hint` / `long_click` | 子样式表 | 继承 | 按下态助记和长按标签。 |

## `hint`：助记文字

```lua
key.hint = {
  show = true,
  text_color = 0xff444444,
  text_size = 12,
  gravity = 'top|right',
  offset_x = 0,
  offset_y = 0,
  up = { text_color = 0xff00aa00 },
  down = { text_color = 0xffaa0000 },
  left = { text_color = 0xff0000aa },
  right = { text_color = 0xffaa00aa },
}
```

方向覆盖项:`key.hint.up`、`key.hint.down`、`key.hint.left`、`key.hint.right`。可使用通用按键样式字段。

## `long_click`：长按提示文字

```lua
key.long_click = {
  show = true,
  text_color = 0xff444444,
  text_size = 12,
  gravity = 'top|left',
  offset_x = 0,
  offset_y = 0,
}
```

可使用通用按键样式字段。

## `preview`：按键预览

```lua
key.preview = {
  show = true,
  text_color = 0xff000000,
  text_size = 22,
  background = 0xffffffff,
  corner_radius = 16,
  stroke_width = 1,
  stroke_color = 0x88dddddd,
  elevation = 16,
  shadow_color = 0xffff0000,
  scale_x = 1.2,
  scale_y = 1.2,
}
```

将预览设为 `nil` 可禁用:

```lua
enter.preview = nil
```

---

# 4. 长按弹出面板 `popup`

```lua
popup = {
  background = 0xffdddddd,
  elevation = 16,
  shadow_color = 0xff000000,
  corner_radius = 8,
  stroke_width = 1,
  stroke_color = 0x88dddddd,
  column_count = 5,
  key = {
    text_size = 18,
    width = 10,
    height = 15,
    preview = nil,
  },
}
```

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---:|---|
| `column_count` | integer | `5` | 弹出面板最大列数。 |
| `background`、`elevation`、`shadow_color`、`corner_radius`、`stroke_width`、`stroke_color` | 通用样式字段 | 见上 | 弹出面板外观。 |
| `key` | key style | 继承全局 `key` | 弹出面板按键样式。 |
| `key.width` / `key.height` | integer | `10` / `15` | 弹出面板单键宽高,按键盘宽高百分比解释。 |

`popup` 样式本身缺失时回退到 `keyboard` 样式(`FloatKeyboard`);弹出按键使用 `popup.key`(缺失时继承全局 `key`)。

---

# 5. 候选栏 `candidate`

```lua
candidate = {
  height = 48,
  background = 0xffdddddd,
  text_color = 0xff000000,
  text_size = 22,
  elevation = 2,
  shadow_color = 0xff000000,

  pressed = {
    background = 0x44888888,
    text_color = 0xff000000,
  },

  comment = {
    text_color = 0xff444444,
    text_size = 12,
  },

  key = {
    text = '▽',
    text_color = 0xff000000,
    text_size = 18,
    background = 0xffdddddd,
  },
}
```

| 路径 | 说明 |
|---|---|
| `candidate.height` | 候选栏高度,代码默认 `48`。 |
| `candidate.background` | 候选栏背景。 |
| `candidate.text_color` / `text_size` / `font` | 候选词样式。 |
| `candidate.elevation` / `shadow_color` | 候选栏阴影。 |
| `candidate.pressed` | 候选项按下态。 |
| `candidate.comment` | 候选注释样式。 |
| `candidate.comment.pressed` | 按下时的候选注释样式。 |
| `candidate.key` | 展开候选面板按钮样式。 |
| `candidate.key.text` | 展开按钮文字,默认 `▽`。 |
| `candidate.key.pressed` | 展开按钮按下态。 |

## 展开候选面板 `candidate.expanded`

```lua
candidate.expanded = {
  background = 0xffdddddd,
  text_color = 0xff000000,
  text_size = 22,
  pressed = { background = 0xffffffff },
  comment = { text_color = 0xff444444, text_size = 12 },
  key = { text_color = 0xff000000, text_size = 18 },
  filter_bar = { show = true, gravity = 'bottom' },
  tool_bar = {
    gravity = 'right',
    keys = { 'hide', 'page_up', 'page_down', 'char_filter', 'BackSpace' },
  },
}
```

| 路径 | 说明 |
|---|---|
| `candidate.expanded` | 展开态基础样式;缺失时继承 `candidate`。 |
| `candidate.expanded.pressed` | 展开态候选按下样式。 |
| `candidate.expanded.comment` | 展开态候选注释样式。 |
| `candidate.expanded.key` | 展开态按键样式。 |
| `candidate.expanded.filter_bar.show` | 是否显示笔画筛选栏,默认 `true`。 |
| `candidate.expanded.filter_bar.gravity` | 筛选栏位置,默认 `left`。 |
| `candidate.expanded.tool_bar.gravity` | 工具栏位置,默认 `right`。 |
| `candidate.expanded.tool_bar.keys` | 字符串数组;内置识别 `hide`、`page_up`、`page_down`、`char_filter`,其他字符串按预设键/事件名处理。此处不支持直接事件 table。 |

`candidate.expanded.tool_bar.keys` 缺失时,默认按钮为 `hide`、`page_up`、`page_down`、`char_filter`。

---

# 6. 符号面板 `symbol`

适用于 `key_maps` 类型键盘。

```lua
symbol = {
  background = 0xffdddddd,
  text_color = 0xff000000,
  text_size = 22,
  indicator_color = 0xff0055ff,
  text = { text_color = 0xff000000, text_size = 22 },
  key = { text_color = 0xff000000, text_size = 18, background = 0xffeeeeee },
  tab_bar = { gravity = 'top', height = 48, indicator_color = 0xff0055ff },
  tool_bar = { gravity = 'right', height = 48, keys = { 'hide', 'page_up', 'page_down', 'BackSpace' } },
}
```

| 路径 | 说明 |
|---|---|
| `symbol` | 符号面板基础样式。 |
| `symbol.text` | 符号/文本项样式。 |
| `symbol.key` / `symbol.key.pressed` | 符号按键及按下态。 |
| `symbol.indicator_color` | Tab 指示器后备颜色。 |
| `symbol.tab_bar.gravity/height/indicator_color` | Tab 栏位置、高度与指示器颜色。 |
| `symbol.tool_bar.gravity/height/keys` | 工具栏位置、高度和字符串按键数组;内置识别 `hide`、`page_up`、`page_down`,其他字符串按事件名处理,不支持直接事件 table。 |

`symbol.tab_bar`、`symbol.tool_bar` 缺失时回退到 `candidate` 样式;指示器颜色按 `tab_bar.indicator_color` → `symbol.indicator_color` → 按下态文字色依次回退。`symbol.tool_bar.keys` 缺失时,默认按钮为 `hide`、`page_up`、`page_down`、`BackSpace`。Tab 文字颜色使用 `symbol.key` 的 `text_color`(未选中)与 `symbol.key.pressed.text_color`(选中)。

# 7. 剪贴板面板 `clipboard`

```lua
clipboard = {
  background = 0xffdddddd,
  item = {
    text_color = 0xff000000,
    text_size = 14,
    padding = { left = 4, top = 4, right = 4, bottom = 4 },
  },
  tab_bar = { gravity = 'top', height = 48, indicator_color = 0xff0055ff },
  tool_bar = { gravity = 'right', height = 48, keys = { 'hide', 'page_up', 'page_down', 'undo' } },
}
```

| 路径 | 说明 |
|---|---|
| `clipboard` | 剪贴板面板基础样式。 |
| `clipboard.item` | 单个剪贴板内容项样式。 |
| `clipboard.item.padding.*` | 内容项内边距。 |
| `clipboard.tab_bar.gravity/height/indicator_color` | Tab 栏位置、高度、指示器颜色。 |
| `clipboard.tool_bar.gravity/height/keys` | 工具栏位置、高度和字符串按键数组;内置识别 `hide`、`page_up`、`page_down`,其他字符串按事件名处理,不支持直接事件 table。 |

`clipboard.tab_bar`、`clipboard.tool_bar` 缺失时回退到 `candidate` 样式;`clipboard.tool_bar.keys` 缺失时,默认按钮为 `hide`、`page_up`、`page_down`、`undo`(撤销)。Tab 文字颜色使用 `clipboard.key` 的 `text_color`(未选中)与 `clipboard.key.pressed.text_color`(选中)。

# 8. 工具栏 `toolbar`

```lua
toolbar = {
  background = 0xffdddddd,
  text_color = 0xff000000,
  text_size = 22,
  elevation = 2,
  shadow_color = 0xff000000,
  schema_switches = false,
  hide = { text = '▽', text_color = 0xff000000 },
  key = {
    text_size = 22,
    padding = { left = 8, top = 0, right = 8, bottom = 0 },
  },
  keys = {
    { label = '菜单', send = 'Control+grave' },
    'Mode_switch',
    'Keyboard_clipboard',
  },
}
```

| 路径 | 说明 |
|---|---|
| `toolbar` | 工具栏基础背景、文字、阴影样式。 |
| `toolbar.schema_switches` | 是否显示当前 Rime 方案定义的开关,默认 `false`。 |
| `toolbar.hide` | 隐藏按钮样式;也可直接给字符串作为按钮文字,如 `hide = '▽'`。 |
| `toolbar.hide.text` | 隐藏按钮文字,默认 `▽`。 |
| `toolbar.key` | 工具栏普通按键默认样式。 |
| `toolbar.keys` | 项目可为预设键名、直接事件表(事件表带 `click` 时按完整按键表解析),或带 `options` 的方案开关表;普通事件表支持 `style`,方案开关表虽读取 `style`,当前构造代码仍使用 `toolbar.key`。 |

方案开关项示例:

```lua
{
  name = 'ascii_mode',
  options = { 'ascii_mode', 'full_shape' },
  states = { '中文', '英文' },
  reset = 0,
  style = 'functional',
}
```

---

# 9. 预编辑区 `preedit`

```lua
preedit = {
  text_color = 0xff222222,
  text_size = 18,
  background = 0xaaffffff,
  font = 'my-font.ttf',
  inline = 'none',
}
```

| 字段 | 可选值 / 说明 |
|---|---|
| `inline` | `none`:独立显示;`input`:嵌入输入框;`preedit` 或 `composition`:嵌入组合区;`preview` 或 `true`:预览模式。 |
| `text_color`、`text_size`、`background`、`font` | 预编辑文字与背景样式。 |

悬浮候选窗(`FloatCandidateView`)顶部的预编辑文字也复用 `preedit` 的 `text_color`、`background`、`text_size` 渲染。

---

# 10. 悬浮组合窗 `composition`

```lua
composition = {
  show = true,
  text_color = 0xff222222,
  text_size = 18,
  background = 0xaaaaaaaa,
  font = 'my-font.ttf',
  position = 'fixed',
  movable = 'false',
  min_length = 8,
  max_length = 10,
  sticky_lines = 0,
  max_entries = -1,
  cloud_max_entries = 0,
  all_phrases = false,
  use_cursor = true,
  min_width = 40,
  min_height = 0,
  max_width = 230,
  max_height = 400,
  padding = { left = 5, top = 5, right = 5, bottom = 5 },
  line_spacing = 0,
  line_spacing_multiplier = 1.2,
  pressed = { text_color = 0xff222222, background = 0xcccccccc },
  key = {},
  window = {
    { start = '', move = '✎ ', ['end'] = '' },
    { start = '', composition = '%s', ['end'] = '', letter_spacing = 0 },
    { start = '\n', label = '%s.', candidate = '%s', comment = ' %s', sep = ' ', ['end'] = '' },
  },
}
```

| 字段 | 类型 | 代码默认值 | 说明 |
|---|---|---:|---|
| `show` | boolean | `true` | 是否显示组合文本/组合窗。 |
| `position` | string | `fixed` | 支持 `left`、`right`、`left_up`、`right_up`、`drag`、`fixed`、`bottom_left`、`bottom_right`、`top_left`、`top_right`;未知值回退 `fixed`。 |
| `movable` | string | `'false'` | 拖动模式:`'false'`、`'true'`、`'once'`。 |
| `min_length` | integer | `0` | 最小触发词长。 |
| `max_length` | integer | `5` | 超过后换行的最大词长。 |
| `sticky_lines` | integer | `0` | 固定在顶部的候选行数。 |
| `max_entries` | integer | `5` | 最大候选数;示例中 `-1` 表示全部。 |
| `cloud_max_entries` | integer | `0` | 云候选数限制;`0` 表示不限制。 |
| `all_phrases` | boolean | `false` | 是否显示全部符合条件的词语。 |
| `use_cursor` | boolean | `true` | 是否使用 Rime 当前高亮候选项。 |
| `min_width/min_height` | number | `10/10` | 窗口最小尺寸。 |
| `max_width/max_height` | number | `10000/1000` | 窗口最大尺寸。 |
| `padding.left/top/right/bottom` | number | `0` | 窗口内边距。 |
| `line_spacing` | number | `1` | 额外行间距。 |
| `line_spacing_multiplier` | float | `1.0` | 行距倍数;填 `0` 时代码会改回 `1.0`。 |
| `text_color`、`text_size`、`background`、`font` | 样式字段 | 继承/默认 | 组合窗外观。 |
| `pressed` | key style | 继承 composition | 候选按下态。 |
| `key` | key style | 继承全局 `key` | 组合窗内按键/候选元素样式。 |

`composition` 样式缺失时回退到 `preedit` 样式,例如 `show` 的读取链为 `composition.show` → `preedit.show`。

## `composition.window` 组件字段

| 字段 | 说明 |
|---|---|
| `when` | 当前只识别 `paging` 与 `has_menu`;条件不满足时隐藏按钮组件,其他值不产生过滤。 |
| `start` | 组件开始前插入文本。 |
| `end` | 组件结束后插入文本;Lua 中应写 `['end']`。 |
| `move` | 可拖动提示内容。 |
| `composition` | 组合文本格式,通常为 `'%s'`。 |
| `label` | 候选编号格式,如 `'%s.'`。 |
| `candidate` | 候选词格式,如 `'%s'`。 |
| `comment` | 注释格式,如 `' %s'`。 |
| `sep` | 候选条目间分隔符。 |
| `align` | 对齐:`left`/`normal`、`right`/`opposite`、`center`;未知或缺失按 `normal`。 |
| `letter_spacing` | 此组件文字间距。 |
| `click` | 点击时触发的事件/预设键名。 |

---

# 11. 键盘布局 `keyboards/<id>.lua`

`InputView` 按下列优先级选择键盘视图:

```text
rows > flex_box > keys > key_maps
```

## 通用顶层字段

```lua
name = '36键'
author = '作者'
style = 'keyboard'
key_width = 10
key_height = 21
lock = true
ascii_mode = false
layout = {}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `name` | string | 键盘选择界面显示名。 |
| `author` | string | 作者元数据;当前 Java UI 不读取,仅供 Lua/人工识别。 |
| `style` | string | 键盘容器引用的样式名,默认 `'keyboard'`。 |
| `lock` | boolean | 键盘锁定状态。 |
| `ascii_mode` | boolean | 是否在 ASCII 模式锁定。 |
| `key_width` | number | 默认键宽;行式/绝对布局按百分比解释。 |
| `key_height` | number | 默认键高;行式/绝对布局按百分比解释。 |
| `layout` | table | 会被 `rows`/绝对布局类保存,但后续读取代码已注释,当前版本没有实际布局效果。 |

## 行式键盘 `rows`

```lua
rows = {
  {
    height = 17,
    width = 10,
    keys = {
      { click = '1' },
      { click = '2' },
    },
  },
}
```

| 路径 | 类型 | 默认值 | 说明 |
|---|---|---:|---|
| `rows` | array | 必填 | 行数组。 |
| `rows[i].keys` | array | 必填 | 本行按键数组。 |
| `rows[i].width` | percent | `key_width`;缺省时 `100/首行键数` | 本行默认键宽百分比。 |
| `rows[i].height` | percent | `key_height`;缺省时 `100/行数` | 本行默认键高百分比。 |
| `rows[i].keys[j].width` | percent | 行 `width` | 单键宽度百分比。 |
| `rows[i].keys[j].height` | percent | 行 `height` | 单键高度百分比。 |

## 弹性布局 `flex_box`

```lua
flex_box = {
  {
    direction = 'row',
    width = 360,
    height = 48,
    grow = 1.0,
    style = 'functional',
    keys = {
      { click = 'a' },
      { click = 'b' },
    },
  },
}
```

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---:|---|
| `flex_box` | array table | 必填 | 根弹性容器,同时也是递归子容器数组;每级均可配置 `direction`、`style`、尺寸和 `grow`。 |
| `keys` | array | 无 | 当前容器内按键;容器本身还可用数字索引嵌套更多子容器。 |
| `style` | string | 无 | 容器背景样式引用。 |
| `direction` | string | `'row'` | 仅精确值 `'column'` 使用纵向,其余值均按横向 `row`。 |
| `width` / `height` | integer (dp) | `-1` | 大于 `0` 时固定尺寸并令该轴 `grow = 0`;否则按父容器方向使用权重/填满。 |
| `grow` | float | `1.0` | 剩余空间分配比例;固定主轴尺寸时会被强制改为 `0`。 |

## 绝对布局 `keys`

```lua
keys = {
  { click = 'a', x = 0, y = 0, width = 20, height = 10 },
}
```

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---:|---|
| `keys` | array | 必填 | 按键数组。 |
| `x` / `y` | percent | `0` | 绝对位置。 |
| `width` | percent | `key_width` | 键宽。 |
| `height` | percent | `key_height` | 键高。 |

## 符号分页键盘 `key_maps`

```lua
key_maps = {
  {
    name = '常用',
    keys = {
      { click = ',' },
      { click = '。' },
    },
  },
}
```

| 字段 | 说明 |
|---|---|
| `key_maps` | 符号分页数据。 |
| `key_maps[i].name` | Tab 标题;缺失时显示页序号。 |
| `key_maps[i].keys` | 本页按键。 |

---

# 12. 键盘按键字段

最小按键:

```lua
{ click = 'a' }
```

完整示例:

```lua
{
  click = 'space',
  style = 'functional',
  label = 'schema_name',
  hint = '空格',
  description = '空格键',
  long_click = 'VOICE_ASSIST',
  combo = 'x',
  hint_long = '语音',
  hint_left = '左',
  hint_right = '右',
  hint_up = '上',
  hint_down = '下',
  swipe_left = 'Left',
  swipe_right = 'Right',
  swipe_up = 'Up',
  swipe_down = 'Down',
  swipe_repeatable = true,
  popup = { 'Keyboard_symbols', 'Keyboard_number' },
  composing = 'CommitScriptText',
  has_menu = 'Menu',
  paging = 'Page_Down',
  ascii = 'space',
  send_bindings = false,
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `click` | string / event table | 点击事件。 |
| `style` | string | 样式名;缺失时优先以 `click` 的字符串作为样式名,最终回退到 `key`。 |
| `label` | string | 主显示文本。 |
| `hint` | string | 助记文字。 |
| `description` | string | 说明文本。 |
| `width` / `height` | number | 行式或绝对布局中的单键尺寸。 |
| `x` / `y` | number | 绝对布局位置。 |
| `popup` | string / string array | 长按弹出字符,或弹出预设键/按键列表。字符串形式会逐字符拆分为弹出项,且当 `label` 为单个字母时自动在列表中加入其大写/小写变体(插入位置取决于按键在键盘中的位置);数组形式按给定顺序显示,由于 `TrimeService.isLongPressPopup()` 固定返回 `false`,数组形式不会自动追加大小写变体。 |
| `swipe_repeatable` | boolean | 手势操作是否允许重复。 |
| `send_bindings` | boolean | 是否转发 Rime 按键绑定;缺失且不存在条件事件时自动为 `false`。 |
| `long_click` | string / event table | 长按事件。 |
| `combo` | string / event table | 第七类组合事件;当前代码会解析并保存,但 `KeyView` 未找到触发它的手势逻辑。 |
| `hint_long` | string | 长按事件提示;缺失时使用长按事件自身标签。 |
| `hint_left/right/up/down` | string | 四方向滑动提示;缺失时使用对应事件自身标签。 |
| `swipe_left/right/up/down` | string / event table | 四方向滑动事件。 |
| `composing` | string | 正在组字时替代事件。 |
| `has_menu` | string | 有候选菜单时替代事件。 |
| `paging` | string | 翻页状态下替代事件。 |
| `ascii` | string / event table / key table | ASCII 模式下替代事件或完整替代按键。 |
| `swipe` | table | “滑动点按”映射表;但当前 `TrimeService.isKeySwipeTap()` 固定返回 `false`,因此该字段在当前版本不可达。 |

键盘 Lua chunk 还可直接 `return` 一个可转换为 Android `View` 的 userdata;此时应用直接使用该 View,跳过 `rows` / `flex_box` / `keys` / `key_maps` 解析。普通 Lua table 返回值不会触发此机制。

动作字段可以引用 `preset_keys` 名称,也可以直接提供完整事件表:

```lua
{
  swipe_left = {
    label = '左移',
    send = 'Left',
  },
}
```

特殊标签语义:

- 回车事件的 `label = 'action_labels'` 会使用当前编辑器 Action 对应的动态标签。
- 空格键配置 `label = 'schema_name'` 时,中文模式优先显示当前 Rime 方案名称。
- `{KeyName}` 形式可直接解析键名/组合键;字符串以 `.lua` 结尾时会作为脚本命令事件。

当前版本并不解析 `double_click` 或 `triple_click`;有效事件数组只有 `click`、`long_click`、四方向 `swipe_*` 和 `combo`。

---

# 13. 当前版本中不应视为可靠生效的示例字段

下列字段出现在内置 `light` 或 `night` 示例中,但未在当前 Java 主题实现中找到直接读取证据:

```text
composition.border
composition.spacing
composition.round_corner
composition.elevation
candidate.expanded.pressed.ripple_color
symbol.flex_basis
```

其中 `symbol.flex_basis` 的读取代码也被注释。它们可以保留以兼容未来版本,但不应依赖其在当前版本中的显示效果。

此外,任意样式表不会自动产生 UI;例如 `enter`、`functional` 必须被按键的 `style = 'enter'` 或 `style = 'functional'` 实际引用才能生效。

---

# 14. 关键源码依据

| 内容 | 文件 |
|---|---|
| 主题/样式加载、回退、动态键盘及通用回调分派 | `app/src/main/java/com/osfans/trime/theme/ThemeManager.java` |
| 背景、图片、边框、字体解析 | `app/src/main/java/com/osfans/trime/theme/Style.java` |
| 按键样式、按键音、振动 | `app/src/main/java/com/osfans/trime/theme/KeyStyle.java` |
| 预设事件字段 | `app/src/main/java/com/osfans/trime/Event.java` |
| 按键、提示和手势字段 | `app/src/main/java/com/osfans/trime/Key.java`、`enums/KeyEventType.java` |
| 行式、弹性、绝对布局 | `keyboard/RowKeyboardView.java`、`FlexboxKeyboardView.java`、`AbsKeyboardView.java` |
| 候选栏、工具栏、剪贴板、符号面板 | `candidate/` 与 `keyboard/` 下对应 View 类 |
| 悬浮组合窗 | `app/src/main/java/com/osfans/trime/Composition.java`、`enums/WindowsPositionType.java` |
| 生命周期与语音回调调用点 | `app/src/main/java/com/osfans/trime/TrimeService.java`、`Speech.java` |
| 默认参考样式 | `app/src/main/assets/themes/default/styles/light/main.lua` |
| 默认主题入口 | `app/src/main/assets/themes/default/main.lua` |
