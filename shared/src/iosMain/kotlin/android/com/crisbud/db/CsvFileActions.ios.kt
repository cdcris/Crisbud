package android.com.crisbud.db

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.uikit.LocalUIViewController
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.writeToURL
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTTypeCommaSeparatedText
import platform.UniformTypeIdentifiers.UTTypePlainText
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
@Composable
internal actual fun rememberCsvFileActions(
    csv: () -> String,
    sample: () -> String,
    onImport: (String) -> Unit,
    onMessage: (String) -> Unit
): CsvFileActions {
    val controller = LocalUIViewController.current
    val latestImport = rememberUpdatedState(onImport)
    val latestMessage = rememberUpdatedState(onMessage)
    val delegate = remember { CsvDocumentDelegate() }

    fun present(picker: UIDocumentPickerViewController, onResult: (NSURL?) -> Unit) {
        delegate.onResult = onResult
        picker.delegate = delegate
        controller.presentViewController(picker, animated = true, completion = null)
    }

    fun save(name: String, content: () -> String, success: String) {
        val url = NSURL.fileURLWithPath(NSTemporaryDirectory() + name)
        runCatching {
            val text = NSString.create(string = content())
            check(text.writeToURL(url, atomically = true, encoding = NSUTF8StringEncoding, error = null)) {
                "Could not write the CSV file."
            }
            present(UIDocumentPickerViewController(forExportingURLs = listOf(url), asCopy = true)) { saved ->
                NSFileManager.defaultManager.removeItemAtURL(url, error = null)
                if (saved != null) latestMessage.value(success)
            }
        }.onFailure {
            NSFileManager.defaultManager.removeItemAtURL(url, error = null)
            latestMessage.value(it.message ?: "Could not save the CSV file.")
        }
    }

    return CsvFileActions(
        export = { save(CSV_EXPENSES_NAME, csv, "Expense CSV exported.") },
        downloadSample = { save(CSV_SAMPLE_NAME, sample, "CSV sample saved.") },
        importExpenses = {
            val picker = UIDocumentPickerViewController(
                forOpeningContentTypes = listOf(UTTypeCommaSeparatedText, UTTypePlainText),
                asCopy = true
            )
            present(picker) { url ->
                if (url != null) runCatching {
                    val text = NSString.create(contentsOfURL = url, encoding = NSUTF8StringEncoding, error = null)
                    latestImport.value(checkNotNull(text) { "Could not read the CSV file as UTF-8." }.toString())
                }.onFailure { latestMessage.value(it.message ?: "Could not import the CSV file.") }
            }
        }
    )
}

@OptIn(ExperimentalForeignApi::class)
private class CsvDocumentDelegate : NSObject(), UIDocumentPickerDelegateProtocol {
    var onResult: ((NSURL?) -> Unit)? = null

    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        finish(didPickDocumentsAtURLs.firstOrNull() as? NSURL)
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        finish(null)
    }

    private fun finish(url: NSURL?) {
        val callback = onResult
        onResult = null
        callback?.invoke(url)
    }
}
