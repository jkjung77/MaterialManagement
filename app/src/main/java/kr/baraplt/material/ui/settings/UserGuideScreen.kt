package kr.baraplt.material.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kr.baraplt.material.ui.components.AppCard
import kr.baraplt.material.ui.components.AppScreenScaffold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserGuideScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val blocks = remember {
        val text = context.assets.open("user_guide.md").bufferedReader(Charsets.UTF_8).use { it.readText() }
        UserGuideParser.parse(text)
    }
    AppScreenScaffold(title = "사용자 설명서", onBack = onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(blocks.size) { index ->
                GuideBlockView(blocks[index])
            }
        }
    }
}

@Composable
private fun GuideBlockView(block: GuideBlock) {
    when (block) {
        is GuideBlock.Title -> Text(block.text, style = MaterialTheme.typography.headlineMedium)
        is GuideBlock.Heading -> {
            Spacer(Modifier.height(8.dp))
            Text(block.text, style = MaterialTheme.typography.titleLarge)
        }
        is GuideBlock.Subheading -> Text(
            block.text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        is GuideBlock.Body -> Text(
            block.text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        is GuideBlock.Bullet -> Row(Modifier.fillMaxWidth()) {
            Text("·  ", style = MaterialTheme.typography.bodyMedium)
            Text(block.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
        is GuideBlock.Table -> AppCard {
            block.rows.forEachIndexed { index, row ->
                if (index > 0) Spacer(Modifier.height(12.dp))
                val title = row.firstOrNull().orEmpty()
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.fillMaxWidth()
                )
                val labels = block.headers.drop(1)
                val values = row.drop(1)
                if (labels.isEmpty()) {
                    val detail = values.joinToString(" · ")
                    if (detail.isNotBlank()) {
                        Text(
                            detail,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    labels.forEachIndexed { i, label ->
                        val value = values.getOrElse(i) { "" }
                        if (value.isNotBlank()) {
                            Text(
                                "$label  $value",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
