name = "26键"
author = "nirenr"
key_width = 10
key_height = 25
lock=true
--rows行键盘，宽度和高度为键盘总宽度的百分比
rows = {
    --第一行
    {
        keys = {
            { click = "q" },
            { click = "w" },
            { click = "e" },
            { click = "r" },
            { click = "t" },
            { click = "y" },
            { click = "u" },
            { click = "i" },
            { click = "o" },
            { click = "p" },
        }
    },
    --第二行
    {
        keys = {
            { width = 5 },
            { click = "a" },
            { click = "s" },
            { click = "d" },
            { click = "f" },
            { click = "g" },
            { click = "h" },
            { click = "j" },
            { click = "k" },
            { click = "l" },
        }
    },
    --第一行
    {
        keys = {
            { click = "Shift_L", style = "functional", width = 15},
            { click = "z" },
            { click = "x" },
            { click = "c" },
            { click = "v" },
            { click = "b" },
            { click = "n" },
            { click = "m" },
            { click = "BackSpace", style = "functional", label = " ⌫", width = 15 },
        }
    },
    --第四行
    {
        keys = {
            { click="Keyboard_symbols", long_click = "F4", style = "functional", width = 15 },
            { click="Keyboard_number", style = "functional" },
            { click = "，", ascii = "," },
            { click = "space", width = 30 },
            { click = "。", ascii = "." },
            { click="Mode_switch", style = "functional" },
            { click = "Return", style = "enter", label = "Enter", width = 15 },
        }
    },
}
