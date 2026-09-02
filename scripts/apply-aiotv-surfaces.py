#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def write(rel: str, text: str) -> None:
    (ROOT / rel).write_text(text, encoding="utf-8")


def replace_required(rel: str, old: str, new: str, count: int = 1) -> None:
    text = read(rel)
    found = text.count(old)
    if found < count:
        raise SystemExit(
            f"AIOtv surfaces: expected {count} occurrence(s) in {rel}, found {found}: {old!r}"
        )
    write(rel, text.replace(old, new, count))
    print(f"AIOtv surfaces: updated {rel}")


# ---------------------------------------------------------------------------
# Details hero/actions
# ---------------------------------------------------------------------------
hero = "app/src/main/java/com/nuvio/tv/ui/screens/detail/HeroSection.kt"
replace_required(
    hero,
    'import com.nuvio.tv.ui.theme.NuvioTheme\n',
    'import com.nuvio.tv.ui.theme.NuvioTheme\n'
    'import com.nuvio.tv.ui.aiotv.design.AioColors\n'
    'import com.nuvio.tv.ui.aiotv.design.AioMotion\n'
    'import com.nuvio.tv.ui.aiotv.design.AioRadii\n'
    'import com.nuvio.tv.ui.aiotv.design.AioSpacing\n',
)
replace_required(
    hero,
    '.padding(start = NuvioTheme.spacing.xxxl, end = NuvioTheme.spacing.xxxl, bottom = NuvioTheme.spacing.lg),\n',
    '.padding(start = AioSpacing.ScreenHorizontal, end = AioSpacing.ScreenHorizontal, bottom = AioSpacing.Section),\n',
)
replace_required(
    hero,
    '''        colors = ButtonDefaults.colors(
            containerColor = androidx.compose.ui.graphics.Color.White,
            focusedContainerColor = androidx.compose.ui.graphics.Color.White,
            contentColor = androidx.compose.ui.graphics.Color.Black,
            focusedContentColor = androidx.compose.ui.graphics.Color.Black
        ),
        shape = ButtonDefaults.shape(
            shape = RoundedCornerShape(NuvioTheme.spacing.xxl)
        ),
        border = ButtonDefaults.border(
            focusedBorder = Border(
                border = NuvioTheme.focusRing.border(NuvioTheme.spacing.xxs),
                shape = RoundedCornerShape(NuvioTheme.spacing.xxl)
            )
        ),
        contentPadding = PaddingValues(horizontal = NuvioTheme.spacing.xl, vertical = 14.dp)
''',
    '''        colors = ButtonDefaults.colors(
            containerColor = AioColors.TextPrimary,
            focusedContainerColor = AioColors.TextPrimary,
            contentColor = AioColors.Canvas,
            focusedContentColor = AioColors.Canvas
        ),
        shape = ButtonDefaults.shape(
            shape = RoundedCornerShape(AioRadii.Small)
        ),
        border = ButtonDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, AioColors.FocusBorder),
                shape = RoundedCornerShape(AioRadii.Small)
            )
        ),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp)
''',
)

# Both detail action-icon implementations use the same Nuvio circular treatment.
replace_required(
    hero,
    '''        colors = IconButtonDefaults.colors(
            containerColor = NuvioTheme.colors.BackgroundCard,
            focusedContainerColor = NuvioTheme.colors.Secondary,
            contentColor = NuvioTheme.colors.TextPrimary,
            focusedContentColor = NuvioTheme.colors.OnSecondary
        ),
        border = IconButtonDefaults.border(
            focusedBorder = Border(
                border = NuvioTheme.focusRing.border(NuvioTheme.spacing.xxs),
                shape = CircleShape
            )
        ),
        shape = IconButtonDefaults.shape(
            shape = CircleShape
        )
''',
    '''        colors = IconButtonDefaults.colors(
            containerColor = AioColors.SurfaceRaised,
            focusedContainerColor = AioColors.SurfaceFocused,
            contentColor = AioColors.TextPrimary,
            focusedContentColor = AioColors.FocusBorder
        ),
        border = IconButtonDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, AioColors.FocusBorder),
                shape = RoundedCornerShape(AioRadii.Small)
            )
        ),
        shape = IconButtonDefaults.shape(
            shape = RoundedCornerShape(AioRadii.Small)
        )
''',
    count=1,
)
replace_required(
    hero,
    '''        colors = IconButtonDefaults.colors(
            containerColor = if (selected) selectedContainerColor else NuvioTheme.colors.BackgroundCard,
            focusedContainerColor = NuvioTheme.colors.Secondary,
            contentColor = if (selected) selectedContentColor else NuvioTheme.colors.TextPrimary,
            focusedContentColor = NuvioTheme.colors.OnSecondary
        ),
        border = IconButtonDefaults.border(
            focusedBorder = Border(
                border = NuvioTheme.focusRing.border(NuvioTheme.spacing.xxs),
                shape = CircleShape
            )
        ),
        shape = IconButtonDefaults.shape(
            shape = CircleShape
        )
''',
    '''        colors = IconButtonDefaults.colors(
            containerColor = if (selected) selectedContainerColor else AioColors.SurfaceRaised,
            focusedContainerColor = AioColors.SurfaceFocused,
            contentColor = if (selected) selectedContentColor else AioColors.TextPrimary,
            focusedContentColor = AioColors.FocusBorder
        ),
        border = IconButtonDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, AioColors.FocusBorder),
                shape = RoundedCornerShape(AioRadii.Small)
            )
        ),
        shape = IconButtonDefaults.shape(
            shape = RoundedCornerShape(AioRadii.Small)
        )
''',
)


# ---------------------------------------------------------------------------
# Stream source selection
# ---------------------------------------------------------------------------
stream = "app/src/main/java/com/nuvio/tv/ui/screens/stream/StreamScreen.kt"
replace_required(
    stream,
    'import com.nuvio.tv.ui.theme.NuvioTheme\n',
    'import com.nuvio.tv.ui.theme.NuvioTheme\n'
    'import com.nuvio.tv.ui.aiotv.design.AioColors\n'
    'import com.nuvio.tv.ui.aiotv.design.AioRadii\n'
    'import com.nuvio.tv.ui.aiotv.design.AioSpacing\n',
)
replace_required(
    stream,
    '        targetValue = if (isLoading) 0.7f else 0.5f,\n',
    '        targetValue = if (isLoading) 0.55f else 0.38f,\n',
)
replace_required(
    stream,
    '        modifier = modifier.padding(start = NuvioTheme.spacing.xxxl, end = NuvioTheme.spacing.xl),\n',
    '        modifier = modifier.padding(start = AioSpacing.ScreenHorizontal, end = 30.dp),\n',
)
replace_required(
    stream,
    '''        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(0.8f)
        ) {
''',
    '''        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start,
            modifier = Modifier.fillMaxWidth(0.86f)
        ) {
''',
)
# Restrict the following text-alignment replacements to the first four occurrences,
# which are the title/episode metadata inside LeftContentSection.
text = read(stream)
needle = '                    textAlign = TextAlign.Center\n'
for _ in range(4):
    if needle not in text:
        raise SystemExit('AIOtv surfaces: missing expected left-content text alignment')
    text = text.replace(needle, '                    textAlign = TextAlign.Start\n', 1)
write(stream, text)
print(f"AIOtv surfaces: updated {stream}")

replace_required(
    stream,
    '''    Column(
        modifier = modifier
            .padding(top = NuvioTheme.spacing.xxxl, end = NuvioTheme.spacing.xxxl, bottom = NuvioTheme.spacing.xxxl)
    ) {
''',
    '''    Column(
        modifier = modifier
            .padding(top = 30.dp, end = 34.dp, bottom = 30.dp)
            .clip(RoundedCornerShape(AioRadii.Large))
            .background(AioColors.Surface.copy(alpha = 0.90f))
            .padding(16.dp)
    ) {
''',
)
replace_required(
    stream,
    '''                    .clip(RoundedCornerShape(NuvioTheme.radii.xl))
                    .background(NuvioTheme.colors.BackgroundCard.copy(alpha = 0.5f)),
''',
    '''                    .clip(RoundedCornerShape(AioRadii.Card))
                    .background(Color.Transparent),
''',
)
replace_required(
    stream,
    '''        colors = CardDefaults.colors(
            containerColor = NuvioTheme.colors.BackgroundElevated,
            focusedContainerColor = NuvioTheme.colors.BackgroundElevated
        ),
        shape = CardDefaults.shape(shape = RoundedCornerShape(NuvioTheme.radii.md)),
        scale = CardDefaults.scale(focusedScale = 1f)
''',
    '''        colors = CardDefaults.colors(
            containerColor = AioColors.SurfaceRaised,
            focusedContainerColor = AioColors.SurfaceFocused
        ),
        border = CardDefaults.border(
            border = Border(
                border = BorderStroke(1.dp, AioColors.Divider),
                shape = RoundedCornerShape(AioRadii.Card)
            ),
            focusedBorder = Border(
                border = BorderStroke(2.dp, AioColors.FocusBorder),
                shape = RoundedCornerShape(AioRadii.Card)
            )
        ),
        shape = CardDefaults.shape(shape = RoundedCornerShape(AioRadii.Card)),
        scale = CardDefaults.scale(focusedScale = 1.012f, pressedScale = 0.995f)
''',
)

print("AIOtv surfaces: complete")
