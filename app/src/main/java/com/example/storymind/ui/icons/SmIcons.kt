package com.example.storymind.ui.icons

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.storymind.R
import com.example.storymind.ui.theme.SmColors

/** Lucide-style line icon (1.8px stroke, round caps/joins) rendered from a vector drawable. */
@Composable
fun SmIcon(
    @DrawableRes id: Int,
    modifier: Modifier = Modifier,
    tint: Color = SmColors.textSecondary,
    size: Dp = 20.dp,
) {
    val vector: ImageVector = ImageVector.vectorResource(id = id)
    Image(
        imageVector = vector,
        contentDescription = null,
        modifier = modifier.size(size),
        colorFilter = ColorFilter.tint(tint),
    )
}

/** Icon drawable ids, named to match the prototype's `Ic*` helpers. */
object SmIcons {
    val Edit = R.drawable.ic_edit
    val Brain = R.drawable.ic_brain
    val Book = R.drawable.ic_book
    val Wiki = R.drawable.ic_book
    val Gear = R.drawable.ic_gear
    val Close = R.drawable.ic_close
    val Search = R.drawable.ic_search
    val More = R.drawable.ic_more
    val Bold = R.drawable.ic_bold
    val Italic = R.drawable.ic_italic
    val Link = R.drawable.ic_link
    val Logo = R.drawable.ic_logo
}