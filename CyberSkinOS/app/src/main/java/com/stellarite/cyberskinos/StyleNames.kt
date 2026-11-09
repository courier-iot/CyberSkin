package com.stellarite.cyberskinos

/**
 * The name shown for a widget in the Add list, its heading and its settings. CyberSkinOS has one style, so this is
 * simply the widget's own name from [WType.title]. A title you type yourself always wins.
 */
fun WType.label(): String = title
