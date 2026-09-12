package com.shangkeschedule

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shangkeschedule.data.model.AppThemePreset
import com.shangkeschedule.ui.glass.GlassRefractionSettings
import com.shangkeschedule.ui.glass.GlassSurface
import com.shangkeschedule.ui.glass.LiquidGlassTab
import com.shangkeschedule.ui.glass.LiquidGlassTabs
import com.shangkeschedule.ui.glass.LocalGlassRefraction
import com.shangkeschedule.ui.glass.glassBackdropSource
import com.shangkeschedule.ui.glass.glassBlur
import com.shangkeschedule.ui.glass.glassLens
import com.shangkeschedule.ui.glass.rememberGlassBackdrop
import com.shangkeschedule.ui.theme.LocalGlassBlurRadius
import com.shangkeschedule.ui.theme.LocalThemePreset
import com.shangkeschedule.ui.theme.ShangKeScheduleTheme
import com.shangkeschedule.ui.theme.appColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import javax.imageio.ImageIO

/**
 * 玻璃底栏离屏探针（仅诊断用）：直接渲染 LiquidGlassTabs + 高频背景，
 * 三组参数各渲一张 PNG，对「底栏区域」与「控制区域」做逐像素差分：
 *
 * - full     : blur=8dp + 折射开(24/24) + 色散开
 * - blurOnly : blur=8dp + 折射关
 * - none     : blur=0 + 折射关
 *
 * 判读：
 * - blurOnly vs none 底栏差 ≈ 0 ⇒ 模糊没画出来；
 * - full vs blurOnly 差 ≈ 0 ⇒ 折射/色散没画出来；
 * - 控制区（玻璃外）三张必须逐位相同，否则场景本身不可比。
 */

private val OUT_DIR = File("../build_qa/glass_probe")

private data class GlassConfig(val name: String, val blurDp: Float, val refractionOn: Boolean, val dispersion: Boolean)

private val CONFIGS = listOf(
    GlassConfig("full", 8f, true, true),
    GlassConfig("blurOnly", 8f, false, false),
    GlassConfig("none", 0f, false, false),
    // 折射隔离：无模糊 + 锐利背景 —— 模糊先把高频条纹糊成灰底，会让透镜位移的
    // 差分被稀释；这一组用锐背景直接量折射/色散的真实幅度。
    GlassConfig("lensOnly", 0f, true, true),
)

fun main() {
    runBlocking(Dispatchers.Default) {
        val images = LinkedHashMap<String, java.awt.image.BufferedImage>()
        for (cfg in CONFIGS) {
            val file = renderScene(cfg)
            ImageIO.read(file)?.let { images[cfg.name] = it } ?: error("cannot read back $file")
        }
        // 裸 renderEffect 对照：绕开全部自研引擎，验证平台 GraphicsLayer.renderEffect 本身是否可用
        images["raw_on"] = ImageIO.read(renderRawScene(blur = true))
        images["raw_off"] = ImageIO.read(renderRawScene(blur = false))
        // 实验H：record 层 + saveLayer(imageFilter) + drawLayer —— 候选修复路径
        images["sl_on"] = ImageIO.read(renderSaveLayerScene(true))
        images["sl_off"] = ImageIO.read(renderSaveLayerScene(false))

        val base = images.getValue("none")
        val w = base.width
        val h = base.height
        // 底栏区域：bar 64dp 高，BottomCenter + padding(16,24) ⇒ y ∈ [H-24-64, H-24]
        val barRect = IntRange(40, w - 41) to IntRange(h - 88, h - 30)
        // 控制区：底栏上方的纯内容区，三张图必须逐位相同
        val ctrlRect = IntRange(40, w - 41) to IntRange(h - 300, h - 260)

        fun diff(a: java.awt.image.BufferedImage, b: java.awt.image.BufferedImage, rect: Pair<IntRange, IntRange>): Pair<Double, Int> {
            var sum = 0.0
            var max = 0
            var n = 0
            for (y in rect.second) {
                for (x in rect.first) {
                    val ca = a.getRGB(x, y)
                    val cb = b.getRGB(x, y)
                    val d = maxOf(
                        Math.abs((ca shr 16 and 0xFF) - (cb shr 16 and 0xFF)),
                        Math.abs((ca shr 8 and 0xFF) - (cb shr 8 and 0xFF)),
                        Math.abs((ca and 0xFF) - (cb and 0xFF)),
                    )
                    sum += d
                    if (d > max) max = d
                    n++
                }
            }
            return (sum / n) to max
        }

        println("=== GLASS PROBE (scene ${w}x$h) ===")
        val ctrl = diff(images.getValue("full"), base, ctrlRect)
        println("[control] full vs none  mean=%.3f max=%d  (必须≈0，否则场景不可比)".format(ctrl.first, ctrl.second))

        val dBlur = diff(images.getValue("blurOnly"), base, barRect)
        println("[bar] blurOnly vs none  mean=%.3f max=%d  (模糊是否生效；≈0 ⇒ 模糊死)".format(dBlur.first, dBlur.second))

        val dRef = diff(images.getValue("full"), images.getValue("blurOnly"), barRect)
        println("[bar] full vs blurOnly  mean=%.3f max=%d  (折射+色散是否生效；≈0 ⇒ 折射/色散死)".format(dRef.first, dRef.second))

        val dLens = diff(images.getValue("lensOnly"), base, barRect)
        println("[bar] lensOnly vs none  mean=%.3f max=%d  (折射+色散·无模糊锐背景；≈0 ⇒ 折射/色散死)"
            .format(dLens.first, dLens.second))

        val dFull = diff(images.getValue("full"), base, barRect)
        println("[bar] full vs none      mean=%.3f max=%d".format(dFull.first, dFull.second))

        val rawMid = IntRange(180, 250) to IntRange(400, 460)
        val dRaw = diff(images.getValue("raw_on"), images.getValue("raw_off"), rawMid)
        println("[raw] blur vs no-blur   mean=%.3f max=%d  (平台 renderEffect 能力对照；≈0 ⇒ 平台不支持/未应用)"
            .format(dRaw.first, dRaw.second))

        // 机制对照：隔离「渲染效果应用方式」这一个变量
        val mech = ImageIO.read(renderMechanismScene())
        println("=== MECHANISM (640x200, 4 格并排) ===")
        println("[mech] 2 self+direct     vs 1 base : mean=%.3f max=%d".format(*cellDiff(mech, 1)))
        println("[mech] 3 self+drawLayer  vs 1 base : mean=%.3f max=%d".format(*cellDiff(mech, 2)))
        println("[mech] 4 parent+childLyr vs 1 base : mean=%.3f max=%d".format(*cellDiff(mech, 3)))
        println("[mech] 5 = 4 + 容器clip      vs 1 base : mean=%.3f max=%d".format(*cellDiff(mech, 4)))
        println("[mech] 6 realGlass blur=0   vs 1 base : mean=%.3f max=%d".format(*cellDiff(mech, 5)))
        println("[mech] 7 realGlass blur=24  vs 1 base : mean=%.3f max=%d".format(*cellDiff(mech, 6)))
        println("[mech] 7 realGlass24 vs 6 realGlass0  : mean=%.3f max=%d".format(*cellPairDiff(mech, 6, 5)))
        println("[mech] 8 drawWithContent+record vs 0  : mean=%.3f max=%d".format(*cellDiff(mech, 7)))
        println("[mech] 9 separate-layer+parenEffect   : mean=%.3f max=%d".format(*cellDiff(mech, 8)))
        println("[mech] 10 drawContent-in-layer+blur   : mean=%.3f max=%d".format(*cellDiff(mech, 9)))
        println("[mech] 11 层内容(修复写法)   非白占比=%.3f  (≈1 ⇒ 层里有内容；≈0 ⇒ 层为空)"
            .format(nonWhiteFraction(mech, 10)))
        println("[mech] 12 层内容(修复前写法) 非白占比=%.3f  (预期 ≈0 ⇒ 老写法层为空)"
            .format(nonWhiteFraction(mech, 11)))
        println("[mech] 13/14 realGlass lens off/on     : mean=%.3f max=%d  (折射链是否生效)"
            .format(*cellPairDiff(mech, 13, 12)))
        println("[mech] 13/14 整格(含边缘带)             : mean=%.3f max=%d  (折射只作用在边缘，必须整格测)"
            .format(*cellFullDiff(mech, 13, 12)))
    }
}

/**
 * 相位对齐的单图多格差分：把第 i 格与第 0 格按「格内相同偏移」逐像素比较
 * （每格宽 150px）。条纹内容相同 ⇒ 差异只可能来自效果本身。
 */
private fun cellDiff(img: java.awt.image.BufferedImage, i: Int): Array<Any> =
    cellPairDiff(img, i, 0)

/** 第 i 格 vs 第 j 格的相位对齐差分。 */
private fun cellPairDiff(img: java.awt.image.BufferedImage, i: Int, j: Int): Array<Any> {    val cellW = 150
    var s = 0.0
    var m = 0
    var n = 0
    for (y in 40 until 160) {
        for (dx in 20 until 130) {
            val ca = img.getRGB(dx + cellW * j, y)
            val cb = img.getRGB(dx + cellW * i, y)
            val d = maxOf(
                Math.abs((ca shr 16 and 0xFF) - (cb shr 16 and 0xFF)),
                Math.abs((ca shr 8 and 0xFF) - (cb shr 8 and 0xFF)),
                Math.abs((ca and 0xFF) - (cb and 0xFF)),
            )
            s += d
            if (d > m) m = d
            n++
        }
    }
    return arrayOf(s / n, m)
}

/**
 * 某格区域内「非白像素」占比 —— 直证录制层内容：
 * 白底 + 只画一层 backdrop ⇒ 格内出现内容（条纹）则占比高，层为空则 ≈0（纯白）。
 */
private fun nonWhiteFraction(img: java.awt.image.BufferedImage, i: Int): Double {
    val cellW = 150
    var hit = 0
    var n = 0
    for (y in 40 until 160) {
        for (dx in 20 until 130) {
            val c = img.getRGB(dx + cellW * i, y)
            val r = c shr 16 and 0xFF
            val g = c shr 8 and 0xFF
            val b = c and 0xFF
            if (minOf(r, g, b) < 245) hit++
            n++
        }
    }
    return hit.toDouble() / n
}

/**
 * 整格差分（覆盖格子的整个 150×200 区域）。
 *
 * 注意：`cellPairDiff` 只取 `dx 20..130` 的「内容区」，会**切掉左右各 20px 边缘带**。
 * 折射恰好只作用在形状边缘（圆角矩形的左右带），用内容区测会把生效的折射量成
 * mean≈0.27（max 却 255）—— 这正是历史上把「正常工作的折射」误判为失效的第二类坑。
 */
private fun cellFullDiff(img: java.awt.image.BufferedImage, i: Int, j: Int): Array<Any> {
    val cellW = 150
    var s = 0.0
    var m = 0
    var n = 0
    for (y in 5 until 195) {
        for (dx in 2 until 148) {
            val ca = img.getRGB(dx + cellW * j, y)
            val cb = img.getRGB(dx + cellW * i, y)
            val d = maxOf(
                Math.abs((ca shr 16 and 0xFF) - (cb shr 16 and 0xFF)),
                Math.abs((ca shr 8 and 0xFF) - (cb shr 8 and 0xFF)),
                Math.abs((ca and 0xFF) - (cb and 0xFF)),
            )
            s += d
            if (d > m) m = d
            n++
        }
    }
    return arrayOf(s / n, m)
}

/**
 * 机制对照场景：4 格并排，内容同为竖条纹，唯一变量是「效果的应用方式」。
 * 1 = 基线（无效果）；2 = 节点自带 renderEffect + 直接内容；
 * 3 = 节点自带 renderEffect + 内容经 drawLayer 绘制；
 * 4 = 父节点带 renderEffect + 子节点经 drawLayer 绘制。
 */
private fun renderMechanismScene(): File {
    val scene = ImageComposeScene(width = 2110, height = 200, density = Density(1f)) {
        Room {
            StripesCell()
            StripesCell(Modifier.graphicsLayer {
                renderEffect = androidx.compose.ui.graphics.BlurEffect(null, 12f, 12f, androidx.compose.ui.graphics.TileMode.Clamp)
            })
            LayerCell(selfEffect = true)
            LayerCell(selfEffect = false)
            ClippedLayerCell()
            RealGlassCell(0f)
            RealGlassCell(24f)
            OfficialRecordCell()
            SeparateLayerCell()
            DrawContentInLayerCell()
            LayerContentCell()
            BrokenLayerContentCell()
            RealGlassLensCell(lens = false)
            RealGlassLensCell(lens = true)
        }
    }
    scene.render()
    Thread.sleep(300)
    val image = scene.render()
    scene.close()
    val bytes = image.encodeToData(EncodedImageFormat.PNG)?.bytes ?: error("encode null")
    OUT_DIR.mkdirs()
    val file = File(OUT_DIR, "probe_mech.png")
    file.writeBytes(bytes)
    println("rendered mechanism -> ${file.absolutePath}")
    return file
}

@Composable
private fun Room(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Row(Modifier.fillMaxSize()) {
        content()
    }
}

@Composable
private fun StripesCell(modifier: Modifier = Modifier) {
    Box(modifier.size(150.dp, 180.dp)) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val stripe = 12f
            var x = 0f
            var i = 0
            val colors = listOf(Color.Black, Color.White, Color(0xFF007AFF), Color(0xFFFF9500))
            while (x < size.width) {
                drawRect(colors[i % colors.size], Offset(x, 0f), androidx.compose.ui.geometry.Size(stripe, size.height))
                x += stripe
                i++
            }
        }
    }
}

@Composable
private fun LayerCell(selfEffect: Boolean) {
    val rec = rememberGraphicsLayer()
    val drawLayerBlock: Modifier = Modifier
        .fillMaxSize()
        .drawBehind {
            rec.record(this, layoutDirection, IntSize(size.width.toInt(), size.height.toInt())) {
                drawStripesInto(size.width, size.height)
            }
            drawLayer(rec)
        }
    val blur: Modifier = Modifier.graphicsLayer {
        renderEffect = androidx.compose.ui.graphics.BlurEffect(
            null, 12f, 12f, androidx.compose.ui.graphics.TileMode.Clamp
        )
    }
    Box(Modifier.size(150.dp, 180.dp)) {
        if (selfEffect) {
            // 3：效果在绘制 drawLayer 的节点自身上
            Box(drawLayerBlock.then(blur))
        } else {
            // 4：效果在父节点，子节点负责 drawLayer
            Box(Modifier.fillMaxSize().then(blur)) {
                Box(drawLayerBlock)
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStripesInto(w: Float, h: Float) {
    val stripe = 12f
    var x = 0f
    var i = 0
    val colors = listOf(Color.Black, Color.White, Color(0xFF007AFF), Color(0xFFFF9500))
    while (x < w) {
        drawRect(colors[i % colors.size], Offset(x, 0f), androidx.compose.ui.geometry.Size(stripe, h))
        x += stripe
        i++
    }
}

/** 格 5：与 GlassSurface 同构——容器带 clip(shape)，效果在中间父节点，drawLayer 在最内子节点。 */
@Composable
private fun ClippedLayerCell() {
    val rec = rememberGraphicsLayer()
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(50)
    Box(
        Modifier
            .size(150.dp, 180.dp)
            .clip(shape)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    renderEffect = androidx.compose.ui.graphics.BlurEffect(
                        null, 12f, 12f, androidx.compose.ui.graphics.TileMode.Clamp
                    )
                }
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        rec.record(this, layoutDirection, IntSize(size.width.toInt(), size.height.toInt())) {
                            drawStripesInto(size.width, size.height)
                        }
                        drawLayer(rec)
                    }
            )
        }
    }
}

/** 格 6/7：真实 GlassSurface 隔离测试——同一背板，唯一变量是模糊半径。 */
@Composable
private fun RealGlassCell(blurDp: Float) {
    val bd = rememberGlassBackdrop()
    Box(Modifier.size(150.dp, 180.dp)) {
        // 背板源（条纹可见），供 GlassSurface 采样
        Box(
            Modifier
                .fillMaxSize()
                .glassBackdropSource(bd)
        ) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                drawStripesInto(size.width, size.height)
            }
        }
        GlassSurface(
            modifier = Modifier.fillMaxSize(),
            backdrop = bd,
            shape = androidx.compose.ui.graphics.RectangleShape,
            effects = {
                glassBlur(blurDp)
            }
        )
    }
}

/** 格 8：Compose 官方录制范式（drawWithContent + record{ drawContent() } + drawLayer）。 */
@Composable
private fun OfficialRecordCell() {
    val rec = rememberGraphicsLayer()
    Box(
        Modifier
            .size(150.dp, 180.dp)
            .drawWithContent {
                rec.record(this, layoutDirection, IntSize(size.width.toInt(), size.height.toInt())) {
                    this@drawWithContent.drawContent()
                }
                drawLayer(rec)
            }
    ) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            drawStripesInto(size.width, size.height)
        }
    }
}

/** 格 9：忠实还原真实结构——层由**另一个节点**录制，玻璃在「父效果 + 子 drawLayer」下绘制。 */
@Composable
private fun SeparateLayerCell() {
    val bd = rememberGraphicsLayer()
    Box(Modifier.size(150.dp, 180.dp)) {
        // 录制节点：直绘内容（确定能进层）+ 上屏
        Box(
            Modifier
                .fillMaxSize()
                .drawWithContent {
                    bd.record(this, layoutDirection, IntSize(size.width.toInt(), size.height.toInt())) {
                        drawStripesInto(size.width, size.height)
                    }
                    drawLayer(bd)
                }
        )
        // 玻璃：父效果 + 子 drawLayer(同一层)
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    renderEffect = androidx.compose.ui.graphics.BlurEffect(
                        null, 12f, 12f, androidx.compose.ui.graphics.TileMode.Clamp
                    )
                }
        ) {
            Box(Modifier.fillMaxSize().drawBehind { drawLayer(bd) })
        }
    }
}

/**
 * 格 10：判定 `drawContent()` 能否进层——录制走 `record{ drawContent() }`，
 * 外层套模糊父节点。差分接近「模糊量」⇒ 内容进了层；差分≈0 ⇒ 层是空的。
 */
@Composable
private fun DrawContentInLayerCell() {
    val rec = rememberGraphicsLayer()
    Box(Modifier.size(150.dp, 180.dp)) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    renderEffect = androidx.compose.ui.graphics.BlurEffect(
                        null, 12f, 12f, androidx.compose.ui.graphics.TileMode.Clamp
                    )
                }
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        rec.record(this, layoutDirection, IntSize(size.width.toInt(), size.height.toInt())) {
                            this@drawWithContent.drawContent()
                        }
                        drawLayer(rec)
                    }
            ) {
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                    drawStripesInto(size.width, size.height)
                }
            }
        }
    }
}

/**
 * 格 11：**层内容直证（修复路径）**——背板源（条纹）+ 白色隔层 + 直接绘制背板层。
 * 判读：录制层里有内容 ⇒ 格内出现条纹；层为空 ⇒ 纯白。
 * 这一格不依赖任何效果链，直接回答「玻璃到底采到了什么」。
 */
@Composable
private fun LayerContentCell() {
    val bd = rememberGlassBackdrop()
    Box(Modifier.size(150.dp, 180.dp)) {
        // 背板源：内容经 GlassBackdropSourceNode 录进 bd.graphicsLayer
        Box(
            Modifier
                .fillMaxSize()
                .glassBackdropSource(bd)
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawStripesInto(size.width, size.height)
            }
        }
        // 白色隔层：挡住底下的原始条纹，格内只剩「层里到底有什么」
        Box(Modifier.fillMaxSize().background(Color.White))
        Box(Modifier.fillMaxSize().drawBehind { drawLayer(bd.graphicsLayer) })
    }
}

/**
 * 格 12：**层内容直证（对照 / 修复前写法）**——与格 11 同内容，唯一变量是录制方式：
 * 沿用修复前的写法（record 块内用**外层**作用域调 `drawContent()`，且不交换画布）。
 * 预期：层为空 ⇒ 纯白。
 */
@Composable
private fun BrokenLayerContentCell() {
    val layer = rememberGraphicsLayer()
    Box(Modifier.size(150.dp, 180.dp)) {
        Box(
            Modifier
                .fillMaxSize()
                .drawWithContent {
                    layer.record(this, layoutDirection, IntSize(size.width.toInt(), size.height.toInt())) {
                        // 老写法：外层作用域 —— 内容画到屏幕画布，层拿不到
                        this@drawWithContent.drawContent()
                    }
                    drawLayer(layer)
                }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawStripesInto(size.width, size.height)
            }
        }
        Box(Modifier.fillMaxSize().background(Color.White))
        Box(Modifier.fillMaxSize().drawBehind { drawLayer(layer) })
    }
}

/**
 * 格 13/14：**折射链独立对照**（唯一变量 = 是否挂 `glassLens`）。
 *
 * 底栏的折射自 v3.50.3 起改为「只在按压态出现」（静止态随 pressProgress=0 关闭），
 * 因此主场景的静态渲染已测不出折射 —— 折射链本身由这一对格子守住：
 * 同一条纹背板 + 同一个 `GlassSurface`，一格挂 `glassLens(24, 24)`、一格不挂。
 * 形状必须用圆角类（`RectangleShape` 解析不出 cornerRadii ⇒ `glassLens` 静默跳过）。
 */
@Composable
private fun RealGlassLensCell(lens: Boolean) {
    val bd = rememberGlassBackdrop()
    Box(Modifier.size(150.dp, 180.dp)) {
        Box(
            Modifier
                .fillMaxSize()
                .glassBackdropSource(bd)
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawStripesInto(size.width, size.height)
            }
        }
        GlassSurface(
            modifier = Modifier.fillMaxSize(),
            backdrop = bd,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(28),
            effects = {
                if (lens) {
                    glassLens(24f, 24f)
                }
            }
        )
    }
}

/** 裸平台能力对照：同一条纹内容，一层挂 Compose BlurEffect，一层不挂。 */private fun renderRawScene(blur: Boolean): File {
    val scene = ImageComposeScene(width = 430, height = 932, density = Density(1f)) {
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
            androidx.compose.foundation.Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(932.dp)
                    .then(
                        if (blur) {
                            Modifier.graphicsLayer {
                                renderEffect = androidx.compose.ui.graphics.BlurEffect(
                                    null, 8f, 8f, androidx.compose.ui.graphics.TileMode.Clamp
                                )
                            }
                        } else Modifier
                    )
            ) {
                val stripe = 14f
                var x = 0f
                var i = 0
                val colors = listOf(
                    Color.Black, Color.White, Color(0xFF007AFF), Color(0xFFFF9500),
                    Color.Black, Color.White, Color(0xFF34C759), Color(0xFFFF2D55)
                )
                while (x < size.width) {
                    drawRect(colors[i % colors.size], Offset(x, 0f), androidx.compose.ui.geometry.Size(stripe, size.height))
                    x += stripe
                    i++
                }
            }
        }
    }
    scene.render()
    Thread.sleep(300)
    val image = scene.render()
    scene.close()
    val bytes = image.encodeToData(EncodedImageFormat.PNG)?.bytes ?: error("encode null")
    OUT_DIR.mkdirs()
    val file = File(OUT_DIR, if (blur) "probe_raw_on.png" else "probe_raw_off.png")
    file.writeBytes(bytes)
    println("rendered raw(blur=$blur) -> ${file.absolutePath}")
    return file
}

/**
 * 实验H：内容 record 进手动层，绘制时用 Skia saveLayer(带 imageFilter 的画笔) 包住
 * drawLayer —— 验证「层内容 + 画笔级 imageFilter」这条路在 Skiko 上是否生效。
 */
private fun renderSaveLayerScene(useFilter: Boolean): File {
    val scene = ImageComposeScene(width = 430, height = 932, density = Density(1f)) {
        val recorded = rememberGraphicsLayer()
        androidx.compose.foundation.Canvas(
            Modifier
                .fillMaxWidth()
                .height(932.dp)
                .drawWithContent {
                    // 把本节点内容（条纹）录进手动层
                    recorded.record(
                        drawContext.density,
                        drawContext.layoutDirection,
                        IntSize(size.width.toInt(), size.height.toInt())
                    ) {
                        this@drawWithContent.drawContent()
                    }
                    val skia = probeSkiaCanvas(drawContext.canvas)
                    val paint = org.jetbrains.skia.Paint()
                    if (useFilter) {
                        paint.imageFilter = org.jetbrains.skia.ImageFilter.makeBlur(
                            12f, 12f, org.jetbrains.skia.FilterTileMode.CLAMP, null
                        )
                    }
                    val clip = org.jetbrains.skia.Rect.makeWH(size.width, size.height)
                    if (skia != null) {
                        skia.saveLayer(clip, paint)
                        drawLayer(recorded)
                        skia.restore()
                    } else {
                        println("probe: skia canvas reflection FAILED")
                        drawLayer(recorded)
                    }
                }
        ) {
            val stripe = 14f
            var x = 0f
            var i = 0
            val colors = listOf(
                Color.Black, Color.White, Color(0xFF007AFF), Color(0xFFFF9500),
                Color.Black, Color.White, Color(0xFF34C759), Color(0xFFFF2D55)
            )
            while (x < size.width) {
                drawRect(colors[i % colors.size], Offset(x, 0f), androidx.compose.ui.geometry.Size(stripe, size.height))
                x += stripe
                i++
            }
        }
    }
    scene.render()
    Thread.sleep(300)
    val image = scene.render()
    scene.close()
    val bytes = image.encodeToData(EncodedImageFormat.PNG)?.bytes ?: error("encode null")
    OUT_DIR.mkdirs()
    val file = File(OUT_DIR, if (useFilter) "probe_sl_on.png" else "probe_sl_off.png")
    file.writeBytes(bytes)
    println("rendered saveLayer(filter=$useFilter) -> ${file.absolutePath}")
    return file
}

/** 反射取 Compose Canvas 底下的 Skia Canvas（ui-graphics 的 internal 访问器跨模块不可见）。 */
private val skiaCanvasGetter: java.lang.reflect.Method? by lazy {
    runCatching {
        Class.forName("androidx.compose.ui.graphics.SkiaBackedCanvas")
            .getDeclaredMethod("getInternalSkiaCanvas\$ui_graphics")
            .apply { isAccessible = true }
    }.onFailure { println("probe reflect fail: $it") }.getOrNull()
}

private fun probeSkiaCanvas(canvas: androidx.compose.ui.graphics.Canvas): org.jetbrains.skia.Canvas? =
    skiaCanvasGetter?.invoke(canvas) as? org.jetbrains.skia.Canvas

private fun renderScene(cfg: GlassConfig): File {
    val scene = ImageComposeScene(
        width = 430,
        height = 932,
        density = Density(1f),
    ) {
        ShangKeScheduleTheme(
            darkTheme = false,
            dynamicColor = false,
            themePreset = AppThemePreset.IOS,
        ) {
            // 显式注入被测配置，与数据层完全解耦
            CompositionLocalProvider(
                LocalThemePreset provides AppThemePreset.IOS,
                LocalGlassBlurRadius provides cfg.blurDp.dp,
                LocalGlassRefraction provides GlassRefractionSettings(
                    enabled = cfg.refractionOn,
                    heightDp = 24f,
                    amountDp = 24f,
                    dispersion = cfg.dispersion,
                    depthEffect = false
                ),
            ) {
                val backdrop = rememberGlassBackdrop()
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(appColors().pageBg)
                ) {
                    // 背板：高频**棋盘格**（不是竖条纹）。
                    // 竖条纹只能显现水平位移，而底栏玻璃上下边缘的折射/色散是**竖直**位移，
                    // 会被竖条纹完全掩盖（实测：竖条纹下折射差分仅 0.2% 像素）⇒ 棋盘格
                    // 两个方向都有边缘，模糊 / 折射 / 色散才都可测。
                    Column(
                        Modifier
                            .fillMaxSize()
                            .glassBackdropSource(backdrop)
                    ) {
                        Canvas(Modifier.fillMaxWidth().height(932.dp)) {
                            val cell = 14f
                            val colors = listOf(
                                Color.Black, Color.White, Color(0xFF007AFF),
                                Color(0xFFFF9500), Color(0xFF34C759), Color(0xFFFF2D55)
                            )
                            var iy = 0
                            var y = 0f
                            while (y < size.height) {
                                var ix = 0
                                var x = 0f
                                while (x < size.width) {
                                    drawRect(
                                        colors[(ix + iy) % colors.size],
                                        Offset(x, y),
                                        androidx.compose.ui.geometry.Size(cell, cell)
                                    )
                                    x += cell
                                    ix++
                                }
                                y += cell
                                iy++
                            }
                        }
                    }
                    LiquidGlassTabs(
                        selectedTabIndex = { 0 },
                        onTabSelected = {},
                        backdrop = backdrop,
                        tabsCount = 4,
                        accentColor = Color(0xFF007AFF),
                        isDark = false,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 16.dp, vertical = 24.dp)
                            .fillMaxWidth(),
                    ) { index ->
                        LiquidGlassTab(onClick = {}) {
                            Text(
                                text = "Tab$index",
                                fontSize = 12.sp,
                                color = if (index == 0) Color(0xFF007AFF) else Color(0xFF3C3C43),
                            )
                        }
                    }
                }
            }
        }
    }
    scene.render()
    Thread.sleep(600)
    val image = scene.render()
    scene.close()
    val bytes = image.encodeToData(EncodedImageFormat.PNG)?.bytes ?: error("encode null")
    OUT_DIR.mkdirs()
    val file = File(OUT_DIR, "probe_${cfg.name}.png")
    file.writeBytes(bytes)
    println("rendered ${cfg.name} -> ${file.absolutePath}")
    return file
}
