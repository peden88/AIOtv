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
            f"AIOtv player: expected {count} occurrence(s) in {rel}, found {found}: {old!r}"
        )
    write(rel, text.replace(old, new, count))
    print(f"AIOtv player: updated {rel}")


player = "app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerScreen.kt"
replace_required(
    player,
    'import com.nuvio.tv.ui.theme.NuvioTheme\n',
    'import com.nuvio.tv.ui.theme.NuvioTheme\n'
    'import com.nuvio.tv.ui.aiotv.design.AioColors\n'
    'import com.nuvio.tv.ui.aiotv.design.AioMotion\n'
    'import com.nuvio.tv.ui.aiotv.design.AioRadii\n'
    'import com.nuvio.tv.ui.aiotv.design.AioSpacing\n',
)
replace_required(player, '.height(150.dp)\n', '.height(110.dp)\n', count=1)
replace_required(
    player,
    '''                        colors = listOf(
                            Color.Black.copy(alpha = 0.7f),
                            Color.Transparent
                        )
''',
    '''                        colors = listOf(
                            Color.Black.copy(alpha = 0.52f),
                            Color.Transparent
                        )
''',
)
replace_required(player, '.height(200.dp)\n', '.height(250.dp)\n', count=1)
replace_required(
    player,
    '''                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.8f)
                        )
''',
    '''                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.88f)
                        )
''',
)
replace_required(
    player,
    '.padding(horizontal = NuvioTheme.spacing.xxl, vertical = NuvioTheme.spacing.xl)\n',
    '.padding(horizontal = AioSpacing.ScreenHorizontal, vertical = 28.dp)\n',
    count=1,
)
replace_required(
    player,
    '''        colors = IconButtonDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.White,
            contentColor = Color.White,
            focusedContentColor = Color.Black
        ),
        shape = IconButtonDefaults.shape(shape = CircleShape)
''',
    '''        colors = IconButtonDefaults.colors(
            containerColor = AioColors.Surface.copy(alpha = 0.55f),
            focusedContainerColor = AioColors.SurfaceFocused,
            contentColor = AioColors.TextPrimary,
            focusedContentColor = AioColors.FocusBorder
        ),
        border = IconButtonDefaults.border(
            border = Border.None,
            focusedBorder = Border(
                border = BorderStroke(2.dp, AioColors.FocusBorder),
                shape = RoundedCornerShape(AioRadii.Small)
            )
        ),
        shape = IconButtonDefaults.shape(shape = RoundedCornerShape(AioRadii.Small))
''',
)
replace_required(
    player,
    '    val accentBrush = ThemeColors.getColorPalette(NuvioTheme.currentTheme).accentBrush()\n',
    '    val accentBrush = Brush.horizontalGradient(listOf(AioColors.Accent, AioColors.FocusBorder))\n',
)
replace_required(
    player,
    '''            .background(
                if (isFocused) Color.White.copy(alpha = 0.45f)
                else Color.White.copy(alpha = 0.3f)
            )
''',
    '''            .background(
                if (isFocused) AioColors.TextPrimary.copy(alpha = 0.42f)
                else AioColors.TextMuted.copy(alpha = 0.44f)
            )
''',
)
replace_required(
    player,
    '.background(NuvioTheme.colors.Secondary.copy(alpha = 0.35f))\n',
    '.background(AioColors.Accent.copy(alpha = 0.30f))\n',
)

print("AIOtv player: complete")
