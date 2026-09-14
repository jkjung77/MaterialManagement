package kr.baraplt.material.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kr.baraplt.material.domain.StockStatus
import kr.baraplt.material.ui.theme.Card
import kr.baraplt.material.ui.theme.CardAlt
import kr.baraplt.material.ui.theme.Ink
import kr.baraplt.material.ui.theme.InkMute
import kr.baraplt.material.ui.theme.Line

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Card)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .border(1.dp, Line, shape)
            .padding(16.dp),
        content = content
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier.padding(vertical = 8.dp)
    )
}

@Composable
fun KpiTile(title: String, value: String, caption: String? = null, modifier: Modifier = Modifier) {
    AppCard(modifier = modifier) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = InkMute)
        Spacer(Modifier.height(6.dp))
        Text(value, style = MaterialTheme.typography.headlineMedium)
        if (caption != null) {
            Spacer(Modifier.height(4.dp))
            Text(caption, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun StatusChip(status: StockStatus, modifier: Modifier = Modifier) {
    val (label, bg, fg) = when (status) {
        StockStatus.OK -> Triple("정상", CardAlt, Ink)
        StockStatus.LOW -> Triple("안전재고", Ink, Color.White)
        StockStatus.CRITICAL -> Triple("재고없음", Ink, Color.White)
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(label, color = fg, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    suffix: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = { raw ->
            if (raw.isEmpty() || raw.matches(Regex("-?\\d*[.,]?\\d*"))) onValueChange(raw.replace(',', '.'))
        },
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        trailingIcon = if (suffix != null) ({ Text(suffix, color = InkMute, modifier = Modifier.padding(end = 8.dp)) }) else null,
        colors = fieldColors()
    )
}

@Composable
fun TextFieldPlain(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    singleLine: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = singleLine,
        colors = fieldColors()
    )
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Ink,
    unfocusedBorderColor = Line,
    focusedLabelColor = Ink,
    cursorColor = Ink
)

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Color.White, disabledContainerColor = CardAlt, disabledContentColor = InkMute)
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = Color.White)
    }
}

@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink)
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
fun MonthSwitcher(
    label: String,
    closed: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Surface(
            onClick = onPrev,
            shape = RoundedCornerShape(12.dp),
            color = CardAlt,
            modifier = Modifier.size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) { Text("‹", style = MaterialTheme.typography.titleLarge) }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.titleLarge)
            if (closed) Text("마감됨", style = MaterialTheme.typography.labelMedium, color = InkMute)
        }
        Surface(
            onClick = onNext,
            shape = RoundedCornerShape(12.dp),
            color = CardAlt,
            modifier = Modifier.size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) { Text("›", style = MaterialTheme.typography.titleLarge) }
        }
    }
}

@Composable
fun TwoCol(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), content = content)
}

@Composable
fun KeyValue(key: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(key, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
fun LockedBanner(visible: Boolean) {
    if (!visible) return
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardAlt)
            .padding(12.dp)
    ) {
        Text("이 달은 마감되어 실적을 수정하지 않습니다. 관리책임자만 마감을 해제할 수 있습니다.", style = MaterialTheme.typography.bodyMedium)
    }
}
