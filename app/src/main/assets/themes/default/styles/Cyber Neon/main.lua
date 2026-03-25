name = "赛博霓虹"
author = "Gemini"
background = 0xff000000 -- 纯黑背景，衬托荧光

--键盘
keyboard = {
    height = 240,
    background = 0xff000000
}

--默认按键样式
key = {
    text_color = 0xffffffff,
    text_size = 22,
    background = 0xff1a1a1a, -- 深灰黑按键
    elevation = 4,
    corner_radius = 6,
    shadow_color = 0xff00ffff, -- 默认带一点青色荧光底影
    long_click_time = 1000,
    repeat_click_time = 200
}
key.margins = { left = 4, top = 4, right = 4, bottom = 6 }

key.hint = {
    show = true,
    text_color = 0xff00ffff, -- 荧光青
    text_size = 11
}

key.long_click = {
    show = true,
    text_color = 0xffff00ff, -- 霓虹紫
    text_size = 12,
    vibration_enabled = true,
}

--按下状态：产生强烈的“光压”感
key.pressed = {
    scale_x = 0.95,
    scale_y = 0.95,
    translation_z = 12,
    shadow_color = 0xffff00ff, -- 按下时阴影变为霓虹紫
    background = 0xff333333,
    text_color = 0xff00ffff,
}

--预览：像霓虹灯牌一样闪亮
key.preview = {
    text_color = 0xff00ffff,
    text_size = 30,
    background = 0xff000000,
    elevation = 20,
    corner_radius = 8,
    stroke_color = 0xff00ffff,
    stroke_width = 2,
    shadow_color = 0xff00ffff
}

space = table.clone(key)
space.text_size = 18
space.shadow_color = 0xffbd00ff -- 空间键投射紫色光

--回车键（电子红/玫红）
enter = table.clone(key)
enter.text_size = 18
enter.background = 0xffff0055
enter.text_color = 0xffffffff
enter.shadow_color = 0xffff0055
enter.pressed.background = 0xffcc0044
enter.preview = nil

--功能键（亮紫色）
functional = table.clone(key)
functional.text_size = 18
functional.background = 0xff7000ff
functional.text_color = 0xffffffff
functional.shadow_color = 0xff7000ff
functional.pressed.background = 0xff5500cc
functional.preview = nil

--符号面板
symbol = {
    background = 0xff000000,
    text_size = 22,
    text_color = 0xff00ffff,
    indicator_color = 0xffff00ff
}
symbol.key = {
    text_color = 0xffffffff,
    text_size = 18,
    background = 0xff111111,
    elevation = 2,
    corner_radius = 6,
    shadow_color = 0x887000ff
}

--候选栏（暗底亮字，冲击力核心）
candidate = {
    height = 52,
    background = 0xff000000,
    text_size = 24,
    text_color = 0xffffffff, -- 待选词纯白
    elevation = 8,
}
candidate.pressed = {
    background = 0xff00ffff, -- 选中瞬间变为亮青色
    text_color = 0xff000000,
    corner_radius = 4,
}
candidate.comment = { text_size = 12, text_color = 0xffff00ff }

--工具栏
toolbar = table.clone(candidate)
toolbar.keys = { { label = "菜单", send = "Control+grave" }, "Mode_switch", "Keyboard_clipboard" }
toolbar.key = table.clone(key)
toolbar.key.background = 0x00000000
toolbar.key.elevation = 0
toolbar.key.text_color = 0xff00ffff

--提示区（输入码）
preedit = {
    text_size = 18,
    text_color = 0xff00ffff,
    background = 0xff1a1a1a
}

height = keyboard.height + candidate.height
