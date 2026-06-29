package com.audiolan.app.domain.model

import androidx.compose.ui.graphics.Color
import com.audiolan.app.ui.theme.AmberSeed
import com.audiolan.app.ui.theme.BlueSeed
import com.audiolan.app.ui.theme.GreenSeed
import com.audiolan.app.ui.theme.LavenderSeed
import com.audiolan.app.ui.theme.LimeSeed
import com.audiolan.app.ui.theme.OrangeSeed
import com.audiolan.app.ui.theme.RoseSeed
import com.audiolan.app.ui.theme.SkySeed
import com.audiolan.app.ui.theme.TealSeed
import com.audiolan.app.ui.theme.VioletSeed

enum class AccentColor(
    val displayName: String,
    val seed: Color,
) {
    SYSTEM("system", SkySeed),
    LAVENDER("lavender", LavenderSeed),
    TEAL("teal", TealSeed),
    AMBER("amber", AmberSeed),
    ROSE("rose", RoseSeed),
    SKY("sky", SkySeed),
    BLUE("blue", BlueSeed),
    GREEN("green", GreenSeed),
    LIME("lime", LimeSeed),
    ORANGE("orange", OrangeSeed),
    VIOLET("violet", VioletSeed);

    val primary: Color
        get() = seed
}
