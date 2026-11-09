package sh.lsx.untisx.ui.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import sh.lsx.untisx.R
import sh.lsx.untisx.data.share.FreeTogether
import sh.lsx.untisx.data.share.ShareCodec
import sh.lsx.untisx.data.share.SharePayload
import sh.lsx.untisx.data.model.toUntisLocalDateOrNull
import sh.lsx.untisx.ui.util.rememberDateFormat
import sh.lsx.untisx.ui.util.rememberTimeFormat

/** Holds a payload that arrived by link or scan until the main screen shows it. */
object PendingShare {
    var payload by mutableStateOf<SharePayload?>(null)
    var invalid by mutableStateOf(false)

    fun offer(link: String?) {
        if (link == null) return
        val decoded = ShareCodec.decode(link)
        if (decoded == null) invalid = true else payload = decoded
    }
}

fun qrBitmap(text: String, size: Int = 768): Bitmap {
    val matrix = QRCodeWriter().encode(
        text,
        BarcodeFormat.QR_CODE,
        size,
        size,
        mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 2),
    )
    val pixels = IntArray(size * size) { i -> if (matrix.get(i % size, i / size)) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}

/** Opens the Google Play services scanner (no camera permission needed) and hands the result on. */
fun scanShareCode(context: Context, onFailure: () -> Unit = {}) {
    val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
    GmsBarcodeScanning.getClient(context, options).startScan()
        .addOnSuccessListener { barcode -> PendingShare.offer(barcode.rawValue) }
        .addOnFailureListener { onFailure() }
}

@Composable
fun describe(payload: SharePayload): String {
    val day = rememberDateFormat("EEEEdMMMM")
    fun date(v: Int) = v.toUntisLocalDateOrNull()?.let(day::format).orEmpty()
    return when (payload) {
        is SharePayload.Homework -> listOf(
            stringResource(R.string.share_kind_homework),
            listOf(payload.subject, payload.title).filter(String::isNotBlank).joinToString(": "),
            payload.due.takeIf { it > 0 }?.let { stringResource(R.string.share_due, date(it)) },
            payload.body.takeIf(String::isNotBlank),
        ).filterNotNull().joinToString("\n")
        is SharePayload.Exam -> listOf(
            listOf(payload.type, payload.subject).filter(String::isNotBlank).joinToString(" ").ifBlank { stringResource(R.string.share_kind_exam) },
            date(payload.date) + if (payload.start > 0) ", %02d:%02d".format(payload.start / 100, payload.start % 100) else "",
            payload.name.takeIf(String::isNotBlank),
        ).filterNotNull().joinToString("\n")
        is SharePayload.Week -> stringResource(R.string.share_week_text, date(payload.monday))
    }
}

/** QR code plus "send as link" for one thing to share. */
@Composable
fun ShareDialog(payload: SharePayload, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val link = remember(payload) { ShareCodec.encode(payload) }
    val bitmap = remember(link) { qrBitmap(link) }
    val text = describe(payload)
    val intro = stringResource(R.string.share_link_intro)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.share_title)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.share_qr),
                    modifier = Modifier.size(240.dp).clip(RoundedCornerShape(12.dp)).background(Color.White),
                )
                Text(text, style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.share_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, text + "\n\n" + intro + "\n" + link)
                context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }) { Text(stringResource(R.string.share_as_link)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

/** What arrived: take it over, or for a week show the common free time. */
@Composable
fun ImportDialog(
    payload: SharePayload,
    freeTogether: (SharePayload.Week) -> List<FreeTogether>,
    onImport: (SharePayload) -> Unit,
    onDismiss: () -> Unit,
) {
    val dayFormat = rememberDateFormat("EEEdMMM")
    val timeFormat = rememberTimeFormat()
    val from = payload.from.ifBlank { stringResource(R.string.share_someone) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.share_received, from)) },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(describe(payload), style = MaterialTheme.typography.bodyMedium)
                if (payload is SharePayload.Week) {
                    val free = remember(payload) { freeTogether(payload) }
                    Text(stringResource(R.string.share_free_together), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    if (free.isEmpty()) {
                        Text(stringResource(R.string.share_free_none), style = MaterialTheme.typography.bodyMedium)
                    }
                    free.forEach { slot ->
                        Text(
                            dayFormat.format(slot.date) + "  " + slot.start.format(timeFormat) + " – " + slot.end.format(timeFormat),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (payload !is SharePayload.Week) {
                TextButton(onClick = { onImport(payload) }) {
                    Text(stringResource(if (payload is SharePayload.Homework) R.string.share_take_homework else R.string.share_take_exam))
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}
