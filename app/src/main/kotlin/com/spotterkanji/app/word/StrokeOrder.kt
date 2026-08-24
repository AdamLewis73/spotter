package com.spotterkanji.app.word

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.spotterkanji.app.ui.theme.SpotterTheme
import com.spotterkanji.domain.dictionary.KanjiDetail
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * KanjiVG's own coordinate space. Every path in `strokes.svg_paths` is drawn on
 * a 109×109 canvas — confirmed against the data rather than taken from the docs:
 * the largest absolute coordinate in the whole table is 108.0.
 *
 * Nothing rescales the paths. The canvas is transformed instead, which keeps
 * `PathMeasure` lengths in one fixed space and makes the stage and the 5-column
 * thumbnail grid render from identical geometry at different sizes.
 */
private const val KANJIVG_CANVAS = 109f

/**
 * Stroke weight, in KanjiVG units rather than dp.
 *
 * Expressing it in the path's own space is what makes the thumbnails
 * self-similar to the stage: both scale by the same factor, so a 40dp cell shows
 * the same drawing as the 200dp stage rather than a spidery version of it.
 *
 * KanjiVG's reference rendering uses 3. This is heavier because the paths are
 * *centrelines* — a real brush stroke tapers, and a uniform centreline at
 * hairline weight reads as a diagram of a kanji rather than as a kanji. At stage
 * size this lands near 8dp, which matches the weight of Noto Sans JP at the
 * 132px the design drew the placeholder glyph at.
 */
private const val STROKE_UNITS = 5f

/** Per-stroke duration at 1×. 29-stroke 鬱 therefore takes about 13 seconds. */
private const val MS_PER_STROKE = 450

/**
 * How far a traced stroke may start or end from the real one and still count,
 * in KanjiVG units — so about a fifth of the character's width.
 *
 * Deliberately forgiving. The check is only ever made against **the stroke
 * currently expected**, never against all of them, so a loose tolerance cannot
 * match the wrong stroke; it can only decide whether this attempt was that
 * stroke. Being strict would turn a practice aid into a dexterity test on a
 * phone screen, which teaches nothing about kanji (D-72).
 */
private const val TRACE_TOLERANCE = 24f

/** What the stage is doing. */
private enum class StageMode { WATCH, TRACE }

/**
 * Screen **3b** from the Claude Design project (D-67), filled in with real data.
 *
 * The design drew this tab with a font glyph standing in for the animation and
 * said so: *"the stroke frames are placeholders — these should come from real
 * stroke-order data (KanjiVG). Sequence, numbering and layout are the design;
 * the glyphs inside are stand-ins."* Sequence, numbering and layout are
 * therefore followed exactly; what is inside the frames is drawn from
 * `strokes.svg_paths`.
 *
 * **The tab has two modes, and the artboard's Trace button switches between
 * them** (D-72). Watching plays the character being written; tracing makes the
 * same stage writable and has the learner draw it themselves over the ghost.
 * There is no scoring and no scheduler involved — the ghost *is* the answer, and
 * assessment belongs to the review flow, which is a different screen in a
 * different phase (artboard 2c).
 *
 * **The speed control is real**, not the artboard's static text: slowing a
 * 29-stroke character down is the reason a learner would want it at all.
 *
 * The ghost is the one addition the artboard could not express, because it used
 * a font glyph as the stand-in. It earns its place twice over — in watch mode it
 * stops the stage being empty before the animation starts and makes the motion
 * read as a character filling in, and in trace mode it is the thing being traced.
 */
@Composable
internal fun StrokeOrderTab(detail: KanjiDetail) {
    val tokens = SpotterTheme.tokens

    // Parsed once per character, not per frame. Compose ships an SVG path
    // parser — `PathParser` is the same one `ImageVector` uses — so no new
    // dependency and no hand-rolled parser. The whole table uses only M/m, C/c
    // and S/s, all of which it handles.
    //
    // A path that fails to parse discards the whole character rather than
    // rendering what parsed. A kanji missing one stroke is a worse thing to show
    // a learner than a kanji that admits it has no diagram, and V-09 is the case
    // that would catch it upstream.
    val paths: List<Path> = remember(detail.character, detail.strokePaths) {
        runCatching {
            detail.strokePaths.map { PathParser().parsePathString(it).toPath() }
        }.getOrDefault(emptyList())
    }

    if (paths.isEmpty()) {
        NoStrokeData(detail)
        return
    }

    val count = paths.size

    // Where each stroke begins and ends, in KanjiVG units. Computed once per
    // character because trace mode compares against them on every gesture.
    val endpoints: List<Pair<Offset, Offset>> = remember(paths) {
        val m = PathMeasure()
        paths.map { path ->
            m.setPath(path, false)
            m.getPosition(0f) to m.getPosition(m.length)
        }
    }

    var mode by remember(detail.character) { mutableStateOf(StageMode.WATCH) }
    val progress = remember(detail.character) { Animatable(0f) }
    // Autoplays on arrival. The payoff for opening the tab is watching the
    // character get written, so making that wait for a tap is a worse trade than
    // the motion costs.
    var playing by remember(detail.character) { mutableStateOf(true) }
    var speed by remember { mutableFloatStateOf(1f) }
    // Trace mode's own progress, kept separate from the animation's so that
    // switching back to Watch does not throw away what was traced.
    var traced by remember(detail.character) { mutableIntStateOf(0) }
    var attempt by remember(detail.character) { mutableStateOf<List<Offset>>(emptyList()) }
    val scope = rememberCoroutineScope()

    // Restarting on `speed` is what lets a mid-animation speed change continue
    // from where it is rather than jumping back to the first stroke: the
    // remaining duration is computed from the *current* value each time.
    LaunchedEffect(detail.character, playing, speed, mode) {
        if (mode != StageMode.WATCH || !playing) return@LaunchedEffect
        if (progress.value >= count) progress.snapTo(0f)
        val remaining = count - progress.value
        progress.animateTo(
            targetValue = count.toFloat(),
            animationSpec = tween(
                durationMillis = (remaining * MS_PER_STROKE / speed).toInt(),
                easing = LinearEasing,
            ),
        )
        playing = false
    }

    // Reused across frames instead of allocated inside the draw pass. Drawing is
    // single-threaded, and a 29-stroke character redrawn at 60fps would
    // otherwise churn 1,700 objects a second for nothing.
    val measure = remember { PathMeasure() }
    val segment = remember { Path() }

    val tracing = mode == StageMode.TRACE
    val strokesStarted = ceil(progress.value).toInt().coerceIn(0, count)
    // The one number both modes agree on: which stroke the screen is currently
    // about. Watch counts the one being drawn; trace counts the one expected.
    val current = if (tracing) min(traced + 1, count) else max(1, strokesStarted)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = tokens.spaceMd),
    ) {
        StrokeStage(
            paths = paths,
            progress = if (tracing) traced.toFloat() else progress.value,
            tracing = tracing,
            traceTarget = if (tracing && traced < count) traced else null,
            attempt = attempt,
            measure = measure,
            segment = segment,
            label = when {
                tracing && traced >= count -> "TRACED ALL $count"
                tracing -> "TRACE STROKE $current OF $count"
                else -> "STROKE $current OF $count"
            },
            onTraceStart = { attempt = listOf(it) },
            onTraceMove = { attempt = attempt + it },
            onTraceEnd = {
                if (traced < count && accepts(attempt, endpoints[traced])) traced++
                attempt = emptyList()
            },
            onTraceCancel = { attempt = emptyList() },
        )

        Transport(
            count = count,
            filled = if (tracing) traced else strokesStarted,
            tracing = tracing,
            playing = playing,
            complete = if (tracing) traced >= count else progress.value >= count,
            speed = speed,
            onPlayPause = {
                if (tracing) {
                    // The same button meaning the same thing in both modes:
                    // start this over.
                    traced = 0
                    attempt = emptyList()
                } else {
                    playing = !playing
                }
            },
            onSpeed = { speed = it },
            onToggleMode = {
                mode = if (tracing) StageMode.WATCH else StageMode.TRACE
                // Watching and writing at once would fight for the stage, so
                // entering trace stops the animation.
                playing = false
                attempt = emptyList()
            },
            modifier = Modifier.padding(top = tokens.spaceSm + 3.dp),
        )

        Text(
            text = "EVERY STROKE",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = tokens.spaceMd + 2.dp),
        )

        StrokeGrid(
            paths = paths,
            selected = current,
            onSelect = { index ->
                if (tracing) {
                    // Same meaning as in watch mode — "go to this stroke" — so a
                    // learner can practise stroke 12 of 鬱 without redrawing the
                    // eleven before it.
                    traced = index
                    attempt = emptyList()
                } else {
                    // `playing = false` stops the LaunchedEffect restarting the
                    // animation; the snapTo cancels the one in flight, because
                    // Animatable serialises its own mutations.
                    playing = false
                    scope.launch { progress.snapTo((index + 1).toFloat()) }
                }
            },
            modifier = Modifier.padding(top = tokens.spaceSm + 1.dp),
        )

        Spacer(modifier = Modifier.height(tokens.spaceMd))
    }
}

/**
 * Does this gesture count as the expected stroke?
 *
 * Start and end points only, against a generous tolerance — not handwriting
 * recognition, and deliberately not shape matching. A learner who begins and
 * ends a stroke in the right places has done the thing this screen teaches:
 * where the stroke goes and which direction it runs.
 *
 * **Direction falls out for free, and it matters.** Drawing a stroke backwards
 * puts the start near the target's end, so the check rejects it — and writing
 * strokes in the wrong direction is a genuine and common beginner error, not a
 * technicality.
 */
private fun accepts(attempt: List<Offset>, target: Pair<Offset, Offset>): Boolean {
    if (attempt.size < 2) return false
    val (start, end) = target
    return (attempt.first() - start).getDistance() <= TRACE_TOLERANCE &&
        (attempt.last() - end).getDistance() <= TRACE_TOLERANCE
}

/**
 * The 200dp stage: recessed well, faint centre crosshair, the character, and the
 * stroke counter tucked into the top-left corner.
 *
 * The crosshair is in the design and is not decoration — kanji are written
 * inside an imaginary square and beginners place strokes badly without a centre
 * reference. It matters more in trace mode, where it is the only guide the
 * learner has for their own hand. It is the same guide the review screen's
 * writing box (2c) uses.
 */
@Composable
private fun StrokeStage(
    paths: List<Path>,
    progress: Float,
    tracing: Boolean,
    traceTarget: Int?,
    attempt: List<Offset>,
    measure: PathMeasure,
    segment: Path,
    label: String,
    onTraceStart: (Offset) -> Unit,
    onTraceMove: (Offset) -> Unit,
    onTraceEnd: () -> Unit,
    onTraceCancel: () -> Unit,
) {
    val tokens = SpotterTheme.tokens
    val guide = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
    val ghost = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val ink = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.background)
            .border(
                1.dp,
                if (tracing) accent else MaterialTheme.colorScheme.outline,
                RoundedCornerShape(14.dp),
            ),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .padding(tokens.spaceMd - 2.dp)
                // Padding first, so gesture coordinates and the DrawScope size
                // are the same space — otherwise every traced stroke lands
                // offset by the padding.
                .pointerInput(tracing, traceTarget) {
                    if (!tracing) return@pointerInput
                    detectDragGestures(
                        onDragStart = { onTraceStart(it.toKanjiSpace(size)) },
                        onDrag = { change, _ -> onTraceMove(change.position.toKanjiSpace(size)) },
                        onDragEnd = { onTraceEnd() },
                        onDragCancel = { onTraceCancel() },
                    )
                }
                // A drawing announces nothing on its own. The counter beside it
                // is real text and is read too, so this names only the subject.
                .semantics {
                    contentDescription =
                        if (tracing) "Trace the character here" else "Stroke order diagram"
                },
        ) {
            drawLine(guide, Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height))
            drawLine(guide, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f))

            onKanjiCanvas {
                val stroke = strokeStyle()
                // Every stroke faintly, first, so the character is present
                // before it is written.
                paths.forEach { drawPath(it, ghost, style = stroke) }

                // The stroke expected next, lifted out of the ghost. This
                // replaces error feedback: rather than telling a learner they
                // got it wrong after the fact, the screen says which one it is
                // waiting for, the whole time.
                traceTarget?.let { drawPath(paths[it], accent.copy(alpha = 0.35f), style = stroke) }

                val done = floor(progress).toInt()
                paths.take(done).forEach { drawPath(it, ink, style = stroke) }

                val fraction = progress - done
                if (done < paths.size && fraction > 0f) {
                    measure.setPath(paths[done], false)
                    segment.reset()
                    measure.getSegment(0f, measure.length * fraction, segment, true)
                    drawPath(segment, ink, style = stroke)
                }

                // The learner's own ink, shown while the finger is down and then
                // either replaced by the real stroke or dropped.
                if (attempt.size > 1) {
                    drawPoints(
                        points = attempt,
                        pointMode = PointMode.Polygon,
                        color = accent,
                        strokeWidth = STROKE_UNITS,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 13.dp, top = 11.dp),
        )
    }
}

/**
 * Play control, one progress segment per stroke, state label, and the speed
 * chips.
 *
 * In watch mode a segment lights when its stroke *starts*, which is what makes
 * the bar agree with the counter above it — the artboard shows three of five
 * filled beside "STROKE 3 OF 5". In trace mode a segment lights when its stroke
 * has been *drawn*, because there is no partial state to represent.
 *
 * The speed chips are hidden while tracing rather than disabled: they control
 * playback, and playback is not happening. A row of dead controls is the thing
 * D-61 objects to.
 */
@Composable
private fun Transport(
    count: Int,
    filled: Int,
    tracing: Boolean,
    playing: Boolean,
    complete: Boolean,
    speed: Float,
    onPlayPause: () -> Unit,
    onSpeed: (Float) -> Unit,
    onToggleMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = SpotterTheme.tokens
    val accent = MaterialTheme.colorScheme.primary
    val idle = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
    val primaryLabel = when {
        tracing -> "Start tracing over"
        playing -> "Pause"
        else -> "Play stroke order"
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(tokens.spaceSm + 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(accent)
                .clickable(onClick = onPlayPause, onClickLabel = primaryLabel)
                .semantics { contentDescription = primaryLabel },
        ) {
            Text(
                // ‖ rather than an icon: the header's ‹ and ✚ are typographic
                // too, and Material's icon set is not bundled for glyphs this
                // simple.
                text = if (tracing || complete) "↻" else if (playing) "‖" else "▶",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimary,
                // The glyph is the icon. Announcing "‖" over the button's own
                // description would be noise.
                modifier = Modifier.clearAndSetSemantics {},
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth().height(5.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                repeat(count) { index ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (index < filled) accent else idle),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = when {
                        tracing && complete -> "YOUR TURN · DONE"
                        tracing -> "YOUR TURN"
                        complete -> "REPLAY"
                        playing -> "PLAYING"
                        else -> "PAUSED"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!tracing) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        listOf(0.5f to "0.5×", 1f to "1×", 2f to "2×").forEachIndexed { index, (value, text) ->
                            if (index > 0) {
                                Text(
                                    text = "·",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .padding(horizontal = 5.dp)
                                        .clearAndSetSemantics {},
                                )
                            }
                            Text(
                                text = text,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (speed == value) {
                                    accent
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier
                                    .clickable(
                                        onClick = { onSpeed(value) },
                                        onClickLabel = "Play at $text speed",
                                    )
                                    // Which speed is active is otherwise carried by
                                    // the accent alone, which a screen reader cannot
                                    // see and a test should not assert on.
                                    .semantics { selected = speed == value },
                            )
                        }
                    }
                }
            }
        }

        // The artboard's Trace button, wired to the stage rather than to a
        // screen that does not exist (D-72).
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .height(46.dp)
                .clip(RoundedCornerShape(11.dp))
                .border(
                    1.dp,
                    if (tracing) accent else MaterialTheme.colorScheme.outline,
                    RoundedCornerShape(11.dp),
                )
                .clickable(
                    onClick = onToggleMode,
                    onClickLabel = if (tracing) "Watch the animation" else "Trace it yourself",
                )
                .semantics { selected = tracing }
                .padding(horizontal = 13.dp),
        ) {
            Text(
                text = if (tracing) "Watch" else "Trace",
                style = MaterialTheme.typography.labelLarge,
                color = if (tracing) accent else MaterialTheme.colorScheme.onSurface,
            )
        }

    }
}

/**
 * The **EVERY STROKE** grid: cell *n* holds strokes 1 to *n*, so reading across
 * shows the character accumulating.
 *
 * Five columns, as the artboard draws it. That happens to be exactly the stroke
 * count of 生, the character the design was mocked up with, so the artboard's
 * single row is a coincidence rather than a rule — 29-stroke 鬱 wraps to six.
 *
 * The newest stroke in each cell is bright and the earlier ones dim, which is
 * the convention every printed stroke-order chart uses: it makes the cell
 * answer *"which stroke is this one?"* at a glance instead of requiring the
 * reader to diff two neighbouring cells. The accent stays on the selected
 * cell's border and number only, where the design puts it — spending it inside
 * every cell as well would leave it meaning nothing (D-67).
 */
@Composable
private fun StrokeGrid(
    paths: List<Path>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val columns = 5
    val prior = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
    val ink = MaterialTheme.colorScheme.onSurface

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        paths.indices.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { index ->
                    StrokeCell(
                        paths = paths,
                        index = index,
                        isSelected = index + 1 == selected,
                        prior = prior,
                        ink = ink,
                        onClick = { onSelect(index) },
                        modifier = Modifier.weight(1f),
                    )
                }
                // Keeps a short final row's cells the same size as a full row's
                // rather than letting three cells stretch across the width.
                repeat(columns - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun StrokeCell(
    paths: List<Path>,
    index: Int,
    isSelected: Boolean,
    prior: Color,
    ink: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val border = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }
    val number = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick, onClickLabel = "Show stroke ${index + 1}")
            // The cell is a drawing with a number in the corner. Merged, that
            // announces as a bare "3"; described, it announces as what it is.
            // `selected` carries the current-cell state that is otherwise only
            // the accent border — invisible to a screen reader, and the wrong
            // thing for a test to assert on.
            .semantics {
                contentDescription = "Show stroke ${index + 1}"
                selected = isSelected
            },
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(1f).padding(4.dp)) {
            onKanjiCanvas {
                val stroke = strokeStyle()
                paths.take(index).forEach { drawPath(it, prior, style = stroke) }
                drawPath(paths[index], ink, style = stroke)
            }
        }
        Text(
            text = "${index + 1}",
            style = MaterialTheme.typography.labelSmall,
            color = number,
            // Already spoken by the cell's description above.
            modifier = Modifier.padding(start = 4.dp, top = 2.dp).clearAndSetSemantics {},
        )
    }
}

/** The empty state, kept from the placeholder tab and still the honest one. */
@Composable
private fun NoStrokeData(detail: KanjiDetail) {
    val tokens = SpotterTheme.tokens
    Column(modifier = Modifier.padding(tokens.spaceMd)) {
        Text(
            text = if (detail.strokeCount == 1) "1 STROKE" else "${detail.strokeCount} STROKES",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "No stroke diagram for this character — KanjiVG covers 6,416 kanji, " +
                "including every common one.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = tokens.spaceSm),
        )
    }
}

/**
 * Fits KanjiVG's 109×109 square into whatever this canvas is, centred, and runs
 * [block] in that space.
 *
 * Scaling the canvas rather than the paths is what keeps `PathMeasure` honest:
 * lengths stay in KanjiVG units, so animation progress is a fraction of a fixed
 * number and looks identical on the stage and in a 40dp cell.
 */
private inline fun DrawScope.onKanjiCanvas(block: DrawScope.() -> Unit) {
    val factor = size.minDimension / KANJIVG_CANVAS
    withTransform({
        translate(
            (size.width - KANJIVG_CANVAS * factor) / 2f,
            (size.height - KANJIVG_CANVAS * factor) / 2f,
        )
        scale(factor, factor, Offset.Zero)
    }) {
        block()
    }
}

/**
 * The inverse of [onKanjiCanvas], for turning a touch into a point on the
 * character.
 *
 * Trace mode compares gestures against path endpoints, and those are in KanjiVG
 * units — so the comparison has to happen in that space rather than in pixels,
 * or the tolerance would mean something different on every screen size.
 */
private fun Offset.toKanjiSpace(size: IntSize): Offset {
    val factor = min(size.width, size.height) / KANJIVG_CANVAS
    return Offset(
        (x - (size.width - KANJIVG_CANVAS * factor) / 2f) / factor,
        (y - (size.height - KANJIVG_CANVAS * factor) / 2f) / factor,
    )
}

/**
 * Round caps and joins, because a brush leaves round ends and a butt cap makes
 * every stroke look clipped. Width is in KanjiVG units — see [STROKE_UNITS].
 */
private fun strokeStyle() = Stroke(
    width = STROKE_UNITS,
    cap = StrokeCap.Round,
    join = StrokeJoin.Round,
)
