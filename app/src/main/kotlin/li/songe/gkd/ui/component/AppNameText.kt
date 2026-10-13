package li.songe.gkd.ui.component

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import li.songe.gkd.data.AppInfo
import li.songe.gkd.util.appInfoMapFlow

@Composable
fun AppNameText(
    modifier: Modifier = Modifier,
    appId: String? = null,
    appInfo: AppInfo? = null,
    fallbackName: String? = null,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
) {
    val info = appInfo ?: appInfoMapFlow.collectAsStateWithLifecycle().value[appId]
    val showSystemIcon = info?.isSystem == true
    val appName = (info?.name ?: fallbackName ?: appId ?: error("appId is required"))
    if (!showSystemIcon) {
        Text(
            modifier = modifier,
            text = appName,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            style = style,
            color = color,
        )
    } else {
        val annotatedString = remember(showSystemIcon, appName) {
            buildAnnotatedString {
                appendInlineContent("icon")
                append(appName)
            }
        }
        val contentColor = style.color.takeOrElse { LocalContentColor.current }
        val inlineContent = remember(style, contentColor) {
            mapOf(
                "icon" to InlineTextContent(
                    placeholder = Placeholder(
                        width = style.fontSize,
                        height = style.lineHeight,
                        placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                    )
                ) {
                    PerfIcon(
                        imageVector = PerfIcon.VerifiedUser,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.extraSmall)
                            .fillMaxSize(),
                        tint = contentColor
                    )
                }
            )
        }
        Text(
            modifier = modifier,
            text = annotatedString,
            inlineContent = inlineContent,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            style = style,
            color = color,
        )
    }
}
