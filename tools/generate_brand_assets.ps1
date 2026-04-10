param(
    [string]$ProjectRoot = (Get-Location).Path
)

$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.Drawing

function New-FontSafe {
    param(
        [string[]]$Families,
        [float]$Size,
        [System.Drawing.FontStyle]$Style = [System.Drawing.FontStyle]::Regular
    )

    foreach ($family in $Families) {
        try {
            return New-Object System.Drawing.Font($family, $Size, $Style, [System.Drawing.GraphicsUnit]::Pixel)
        } catch {
        }
    }

    return New-Object System.Drawing.Font("Arial", $Size, $Style, [System.Drawing.GraphicsUnit]::Pixel)
}

function Set-Quality {
    param([System.Drawing.Graphics]$Graphics)

    $Graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $Graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $Graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
    $Graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $Graphics.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
}

function New-RoundedPath {
    param(
        [float]$X,
        [float]$Y,
        [float]$Width,
        [float]$Height,
        [float]$Radius
    )

    $diameter = $Radius * 2
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $path.AddArc($X, $Y, $diameter, $diameter, 180, 90)
    $path.AddArc($X + $Width - $diameter, $Y, $diameter, $diameter, 270, 90)
    $path.AddArc($X + $Width - $diameter, $Y + $Height - $diameter, $diameter, $diameter, 0, 90)
    $path.AddArc($X, $Y + $Height - $diameter, $diameter, $diameter, 90, 90)
    $path.CloseFigure()
    return $path
}

function Save-Png {
    param(
        [System.Drawing.Bitmap]$Bitmap,
        [string]$Path
    )

    $dir = Split-Path -Parent $Path
    if (-not (Test-Path $dir)) {
        New-Item -ItemType Directory -Path $dir -Force | Out-Null
    }
    $Bitmap.Save($Path, [System.Drawing.Imaging.ImageFormat]::Png)
}

function Resize-Bitmap {
    param(
        [System.Drawing.Bitmap]$Bitmap,
        [int]$Size
    )

    $resized = New-Object System.Drawing.Bitmap($Size, $Size)
    $graphics = [System.Drawing.Graphics]::FromImage($resized)
    Set-Quality $graphics
    $graphics.Clear([System.Drawing.Color]::Transparent)
    $graphics.DrawImage($Bitmap, 0, 0, $Size, $Size)
    $graphics.Dispose()
    return $resized
}

function Draw-BrandGlyph {
    param(
        [System.Drawing.Graphics]$Graphics,
        [float]$X,
        [float]$Y,
        [float]$Size
    )

    $white = [System.Drawing.Color]::FromArgb(250, 255, 255, 255)
    $gold = [System.Drawing.Color]::FromArgb(255, 255, 190, 78)
    $softGold = [System.Drawing.Color]::FromArgb(255, 255, 212, 120)
    $halo = [System.Drawing.Color]::FromArgb(38, 255, 255, 255)

    $fontMain = New-FontSafe -Families @("Segoe UI Semibold", "Arial Bold") -Size ($Size * 0.70) -Style ([System.Drawing.FontStyle]::Bold)
    $format = New-Object System.Drawing.StringFormat
    $format.Alignment = [System.Drawing.StringAlignment]::Center
    $format.LineAlignment = [System.Drawing.StringAlignment]::Center

    $ringPen = New-Object System.Drawing.Pen($halo, ($Size * 0.034))
    $ringRect = New-Object System.Drawing.RectangleF -ArgumentList ($X + ($Size * 0.12)), ($Y + ($Size * 0.15)), ($Size * 0.54), ($Size * 0.54)
    $Graphics.DrawEllipse($ringPen, $ringRect)

    $brushWhite = New-Object System.Drawing.SolidBrush($white)
    $drRect = New-Object System.Drawing.RectangleF -ArgumentList ($X + ($Size * 0.03)), ($Y + ($Size * 0.00)), ($Size * 0.62), ($Size * 0.72)
    $Graphics.DrawString("d", $fontMain, $brushWhite, $drRect, $format)

    $antennaPen = New-Object System.Drawing.Pen($softGold, ($Size * 0.026))
    $antennaPen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
    $antennaPen.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
    $mastX = $X + ($Size * 0.73)
    $mastY = $Y + ($Size * 0.30)
    $Graphics.DrawLine($antennaPen, $mastX, $mastY, ($X + ($Size * 0.85)), ($Y + ($Size * 0.16)))
    $Graphics.DrawLine($antennaPen, $mastX, ($mastY + ($Size * 0.08)), ($X + ($Size * 0.89)), ($Y + ($Size * 0.27)))

    for ($i = 0; $i -lt 3; $i++) {
        $offset = $Size * (0.038 * $i)
        $arcRect = New-Object System.Drawing.RectangleF -ArgumentList ($X + ($Size * 0.60) - $offset), ($Y + ($Size * 0.05) - $offset), (($Size * 0.28) + ($offset * 2)), (($Size * 0.28) + ($offset * 2))
        $Graphics.DrawArc($antennaPen, $arcRect, 312, 84)
    }

    $nodeBrush = New-Object System.Drawing.SolidBrush($gold)
    $Graphics.FillEllipse($nodeBrush, ($X + ($Size * 0.74)), ($Y + ($Size * 0.56)), ($Size * 0.09), ($Size * 0.09))

    $fontMain.Dispose()
    $format.Dispose()
    $ringPen.Dispose()
    $brushWhite.Dispose()
    $antennaPen.Dispose()
    $nodeBrush.Dispose()
}

function New-LauncherIcon {
    param([bool]$TransparentBackground = $false)

    $size = 1024
    $bitmap = New-Object System.Drawing.Bitmap($size, $size)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    Set-Quality $graphics
    $graphics.Clear([System.Drawing.Color]::Transparent)

    if (-not $TransparentBackground) {
        $shadowBrush = New-Object System.Drawing.Drawing2D.PathGradientBrush((New-RoundedPath 168 168 688 688 180))
        $shadowBrush.CenterColor = [System.Drawing.Color]::FromArgb(120, 0, 34, 53)
        $shadowBrush.SurroundColors = @([System.Drawing.Color]::FromArgb(0, 0, 34, 53))
        $graphics.FillEllipse($shadowBrush, 150, 180, 724, 724)
        $shadowBrush.Dispose()

        $cardPath = New-RoundedPath 120 120 784 784 190
        $gradient = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
            (New-Object System.Drawing.Point -ArgumentList 120,120),
            (New-Object System.Drawing.Point -ArgumentList 904,904),
            [System.Drawing.Color]::FromArgb(255, 9, 41, 63),
            [System.Drawing.Color]::FromArgb(255, 22, 146, 167)
        )
        $graphics.FillPath($gradient, $cardPath)

        $glowPen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(96, 255, 255, 255), 5)
        $graphics.DrawPath($glowPen, $cardPath)
        $glowPen.Dispose()
        $gradient.Dispose()
        $cardPath.Dispose()

        $topGlow = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
            (New-Object System.Drawing.Rectangle -ArgumentList 180,132,640,220),
            [System.Drawing.Color]::FromArgb(48, 255, 255, 255),
            [System.Drawing.Color]::FromArgb(0, 255, 255, 255),
            90
        )
        $graphics.FillEllipse($topGlow, 190, 122, 624, 196)
        $topGlow.Dispose()

        Draw-BrandGlyph -Graphics $graphics -X 160 -Y 145 -Size 704
    } else {
        Draw-BrandGlyph -Graphics $graphics -X 165 -Y 145 -Size 694
    }

    $graphics.Dispose()
    return $bitmap
}

function New-Wordmark {
    $bitmap = New-Object System.Drawing.Bitmap(1600, 520)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    Set-Quality $graphics
    $graphics.Clear([System.Drawing.Color]::Transparent)

    $icon = New-LauncherIcon -TransparentBackground:$false
    $graphics.DrawImage($icon, 24, 40, 360, 360)
    $icon.Dispose()

    $brandBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 17, 52, 64))
    $subBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 84, 104, 122))
    $titleFont = New-FontSafe -Families @("Segoe UI Semibold", "Arial Bold") -Size 168 -Style ([System.Drawing.FontStyle]::Bold)
    $modemFont = New-FontSafe -Families @("Segoe UI Bold", "Arial Bold") -Size 64 -Style ([System.Drawing.FontStyle]::Bold)
    $serviceFont = New-FontSafe -Families @("Segoe UI Semibold", "Arial") -Size 44 -Style ([System.Drawing.FontStyle]::Regular)

    $graphics.DrawString("dRecharge", $titleFont, $brandBrush, 420, 92)

    $pillPath = New-RoundedPath 430 282 280 96 48
    $pillGradient = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
        (New-Object System.Drawing.Point -ArgumentList 430,282),
        (New-Object System.Drawing.Point -ArgumentList 710,378),
        [System.Drawing.Color]::FromArgb(255, 255, 214, 120),
        [System.Drawing.Color]::FromArgb(255, 255, 176, 52)
    )
    $graphics.FillPath($pillGradient, $pillPath)
    $graphics.DrawString("MODEM", $modemFont, (New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 13, 60, 69))), 472, 295)

    $graphics.DrawString("Service automation", $serviceFont, $subBrush, 742, 297)
    $graphics.DrawString("Reliable recharge modem control", $serviceFont, $subBrush, 430, 392)

    $pillGradient.Dispose()
    $pillPath.Dispose()
    $titleFont.Dispose()
    $modemFont.Dispose()
    $serviceFont.Dispose()
    $brandBrush.Dispose()
    $subBrush.Dispose()
    $graphics.Dispose()
    return $bitmap
}

$resRoot = Join-Path $ProjectRoot "app\src\main\res"
$drawRoot = Join-Path $resRoot "drawable-nodpi"

$squareLogo = New-LauncherIcon -TransparentBackground:$false
$foregroundLogo = New-LauncherIcon -TransparentBackground:$true
$wordmarkLogo = New-Wordmark

Save-Png $squareLogo (Join-Path $drawRoot "app_logo_square.png")
Save-Png $wordmarkLogo (Join-Path $drawRoot "app_logo_wordmark.png")

$densities = @{
    "mipmap-mdpi" = 48
    "mipmap-hdpi" = 72
    "mipmap-xhdpi" = 96
    "mipmap-xxhdpi" = 144
    "mipmap-xxxhdpi" = 192
}

foreach ($entry in $densities.GetEnumerator()) {
    $folder = Join-Path $resRoot $entry.Key
    if (-not (Test-Path $folder)) {
        New-Item -ItemType Directory -Path $folder -Force | Out-Null
    }

    Remove-Item (Join-Path $folder "ic_launcher.webp") -ErrorAction SilentlyContinue
    Remove-Item (Join-Path $folder "ic_launcher_round.webp") -ErrorAction SilentlyContinue
    Remove-Item (Join-Path $folder "ic_launcher_foreground.webp") -ErrorAction SilentlyContinue

    $size = [int]$entry.Value
    $launcher = Resize-Bitmap -Bitmap $squareLogo -Size $size
    $round = Resize-Bitmap -Bitmap $squareLogo -Size $size
    $foreground = Resize-Bitmap -Bitmap $foregroundLogo -Size $size

    Save-Png $launcher (Join-Path $folder "ic_launcher.png")
    Save-Png $round (Join-Path $folder "ic_launcher_round.png")
    Save-Png $foreground (Join-Path $folder "ic_launcher_foreground.png")

    $launcher.Dispose()
    $round.Dispose()
    $foreground.Dispose()
}

$playStoreDir = Join-Path $ProjectRoot "app\src\main"
$playStoreBitmap = Resize-Bitmap -Bitmap $squareLogo -Size 512
Save-Png $playStoreBitmap (Join-Path $playStoreDir "ic_launcher-playstore.png")

$playStoreBitmap.Dispose()
$squareLogo.Dispose()
$foregroundLogo.Dispose()
$wordmarkLogo.Dispose()

Write-Output "Generated brand assets for dRecharge Modem."
