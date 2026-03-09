name = "暗夜"
author = "nirenr"
--输入主颜色或图片
background = 0xff222222

--键盘
keyboard = {
    --键盘高度
    height = 240,
    --键盘背景颜色或图片
    background = 0xff222222
}

--默认按键样式
key = {
    --按键文字颜色
    text_color = 0xffffffff,
    --按键文字大小
    text_size = 22,
    --按键背景颜色或图片
    background = 0xff666666,
    --按键阴影高度
    elevation = 4,
    --按键圆角半径
    corner_radius = 8,
    --按键背景颜色
    shadow_color = 0xffffffff
}
--按键四周留白
key.margins={
    left=2,
    top=2,
    right=2,
    bottom=3
}
--按键助记
key.hint = {
    --助记文字颜色
    text_color = 0xffdddddd,
    --助记文字大小
    text_size = 12
}
--按键长按
key.long_click = {
    --长按文字颜色
    text_color = 0xffdddddd,
    --长按文字大小
    text_size = 12,
    vibration_enabled = true,--震动开关
}
--按键按下状态
key.pressed = {
    --宽度缩放
    scale_x = 0.9,
    --高度缩放
    scale_y = 0.9,
    --高度改变
    translation_z = 8,
    --水平移动
    translation_x = 0,
    --垂直移动
    translation_y = 0,
    --阴影颜色
    shadow_color = 0xff00ffff,
    --背景按键颜色或图片
    background = 0xff888888,
    text_color = 0xffffffff,
    --助记文字颜色
    hint = {
        text_color = 0xff444444,
    },
    --长按文字颜色
    long_click = {
        text_color = 0xff444444,
    }
}

space=table.clone(key)
space.text_size=18

--回车键样式，需要在回车键定义style="enter"
enter=table.clone(key)
enter.text_size=18
enter.background = 0xff1976D2
enter.pressed.background = 0xff1565C0
enter.pressed.text_color = 0xffffffff

enter2=table.clone(enter)
enter2.corner_radius = 32

--功能键样式，需要在功能按键定义style="functional"
functional=table.clone(key)
functional.text_size=18
functional.background = 0xff222222
functional.pressed.background = 0xff888888
functional.pressed.text_color = 0xffffffff

--符号面板
symbol={
    background = 0xff222222,
    text_size = 22,
    text_color = 0xffffffff,
    indicator_color = 0xffFF00D1FF
}
symbol.key = {
    text_color = 0xffffffff,
    text_size = 18,
    background = 0xff222222,
    elevation = 2,
    corner_radius = 8,
    shadow_color = 0xffffffff
}
symbol.key.pressed = {
    scale_x = 0.9,
    scale_y = 0.9,
    translation_z = -1,
    translation_x = 0,
    translation_y = 0,
    text_color = 0xffffffff,
    shadow_color = 0xff00ffff,
    background = 0xff888888,
}


--候选栏样式
candidate = {
    height = 48,
    background = 0xff222222,
    text_size = 22,
    text_color = 0xffffffff,
    elevation = 2,
    shadow_color = 0xffffffff
}

candidate.pressed = {
    background = 0x44888888,
    text_color = 0xffffffff,
    corner_radius = 0,
}

candidate.comment = {
    text_size = 12,
    text_color = 0xff888888
}
candidate.comment.pressed  = {
    text_size = 12,
    text_color = 0xff888888
}

candidate.key = {
    text_color = 0xffffffff,
    text_size = 18,
    background = 0xff222222,
    elevation = 0,
    corner_radius = 8,
    shadow_color = 0xffffffff
}
candidate.key.pressed = {
    scale_x = 0.9,
    scale_y = 0.9,
    translation_z = 2,
    translation_x = 0,
    translation_y = 0,
    shadow_color = 0xff00ffff,
    background = 0xff444444,
}
--候选面板样式
candidate.expanded = {
    background = 0xff222222,
    text_size = 22,
    text_color = 0xffffffff,
}
candidate.expanded.pressed = {
    background = 0xff222222,
    ripple_color = 0x40000000,
}
candidate.expanded.comment = {
    text_size = 12,
    text_color = 0xff888888
}

candidate.expanded.key = {
    text_color = 0xffffffff,
    text_size = 18,
    background = 0xff222222,
    elevation = 2,
    corner_radius = 8,
    shadow_color = 0xffffffff
}
candidate.expanded.key.pressed = {
    scale_x = 0.9,
    scale_y = 0.9,
    text_color = 0xffddffff,
    translation_z = 10,
    translation_x = 0,
    translation_y = 0,
    shadow_color = 0xff00ffff,
    background = 0xff444444,
}

--剪贴板样式
clipboard=table.clone(candidate.expanded)
clipboard.item=table.clone(key)
clipboard.item.text_size=14
clipboard.item.padding={
    left=4,
    top=4,
    right=4,
    bottom=4
}

--工具栏样式
toolbar=table.clone(candidate)
toolbar.keys={"F4","Mode_switch","Keyboard_clipboard"}
toolbar.key.text_size=22
toolbar.key.padding={
    left=8,
    top=0,
    right=8,
    bottom=0
}

--提示区样式
preedit = {
    text_size = 18,
    text_color = 0xffdddddd,
    background = 0xaa444444
}

--总高度
height = keyboard.height + candidate.height

