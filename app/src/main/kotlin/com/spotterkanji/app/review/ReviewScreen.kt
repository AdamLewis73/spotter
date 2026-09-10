package com.spotterkanji.app.review

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.spotterkanji.app.R
import com.spotterkanji.app.ui.theme.SpotterTheme

/**
 * The Review destination, as a placeholder.
 *
 * **Review is Phase 7** (D-79) — FSRS, the scheduler, and the card itself are
 * all unbuilt. But D-90 puts three destinations in the bar *now*, so this tab
 * exists and a user will tap it. It has to say something true.
 *
 * So it says review is not built yet, rather than showing an empty queue. Those
 * look identical and mean opposite things: an empty queue says *you are done*,
 * and a learner who had filed twenty words and been told they had nothing to
 * review would reasonably conclude the app had lost them.
 *
 * The design's "all caught up" screen (wireflow D5) is the right thing to draw
 * here **once there is a schedule to be caught up with**. Deliberately not
 * borrowed early, for the reason above.
 */
@Composable
internal fun ReviewScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val tokens = SpotterTheme.tokens
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = tokens.spaceXl),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.review_not_yet_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(tokens.spaceSm))
        Text(
            text = stringResource(R.string.review_not_yet_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
