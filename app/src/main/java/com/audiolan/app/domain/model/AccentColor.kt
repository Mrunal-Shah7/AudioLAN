package com.audiolan.app.domain.model

import androidx.compose.ui.graphics.Color
import com.audiolan.app.ui.theme.AmberPrimary
import com.audiolan.app.ui.theme.AmberThumb
import com.audiolan.app.ui.theme.BluePrimary
import com.audiolan.app.ui.theme.BlueThumb
import com.audiolan.app.ui.theme.GreenPrimary
import com.audiolan.app.ui.theme.GreenThumb
import com.audiolan.app.ui.theme.LavenderPrimary
import com.audiolan.app.ui.theme.LavenderThumb
import com.audiolan.app.ui.theme.LimePrimary
import com.audiolan.app.ui.theme.LimeThumb
import com.audiolan.app.ui.theme.OnPrimary
import com.audiolan.app.ui.theme.OrangePrimary
import com.audiolan.app.ui.theme.OrangeThumb
import com.audiolan.app.ui.theme.RosePrimary
import com.audiolan.app.ui.theme.RoseThumb
import com.audiolan.app.ui.theme.SkyPrimary
import com.audiolan.app.ui.theme.SkyThumb
import com.audiolan.app.ui.theme.TealPrimary
import com.audiolan.app.ui.theme.TealThumb
import com.audiolan.app.ui.theme.VioletPrimary
import com.audiolan.app.ui.theme.VioletThumb

enum class AccentColor(
    val displayName: String,
    val primary: Color,
    val onPrimary: Color,
    val toggleThumb: Color,
) {
    SYSTEM("system", SkyPrimary, OnPrimary, SkyThumb),
    LAVENDER("lavender", LavenderPrimary, OnPrimary, LavenderThumb),
    TEAL("teal", TealPrimary, OnPrimary, TealThumb),
    AMBER("amber", AmberPrimary, OnPrimary, AmberThumb),
    ROSE("rose", RosePrimary, OnPrimary, RoseThumb),
    SKY("sky", SkyPrimary, OnPrimary, SkyThumb),
    BLUE("blue", BluePrimary, OnPrimary, BlueThumb),
    GREEN("green", GreenPrimary, OnPrimary, GreenThumb),
    LIME("lime", LimePrimary, OnPrimary, LimeThumb),
    ORANGE("orange", OrangePrimary, OnPrimary, OrangeThumb),
    VIOLET("violet", VioletPrimary, OnPrimary, VioletThumb),
}
