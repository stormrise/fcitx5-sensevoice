package com.fcitx5sensevoice

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

/**
 * Displays bundled legal and attribution documents from assets.
 *
 * @author Lingxiao Li （李凌霄）
 * @date 2026/09/29
 */
class LegalDocumentActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_legal_document)

        val document = intent.getStringExtra(EXTRA_DOCUMENT) ?: DOCUMENT_PRIVACY
        val definition = LegalDocuments.byId(document)
            ?: throw IllegalArgumentException("Unknown legal document: $document")

        title = getString(definition.titleRes)
        findViewById<TextView>(R.id.legal_body).text =
            assets.open(definition.assetPath).bufferedReader().use { it.readText() }
    }

    companion object {
        const val EXTRA_DOCUMENT = "legal_document"
        const val DOCUMENT_PRIVACY = "privacy"
        const val DOCUMENT_THIRD_PARTY = "third_party"
        const val DOCUMENT_MODEL = "model"
        const val DOCUMENT_ONNX_NOTICES = "onnx_notices"
    }
}

private data class LegalDocumentDefinition(
    val id: String,
    val titleRes: Int,
    val assetPath: String,
)

private object LegalDocuments {
    private val documents = listOf(
        LegalDocumentDefinition(
            LegalDocumentActivity.DOCUMENT_PRIVACY,
            R.string.legal_privacy_title,
            "legal/privacy_policy.txt",
        ),
        LegalDocumentDefinition(
            LegalDocumentActivity.DOCUMENT_THIRD_PARTY,
            R.string.legal_third_party_title,
            "legal/third_party_notices.txt",
        ),
        LegalDocumentDefinition(
            LegalDocumentActivity.DOCUMENT_MODEL,
            R.string.legal_model_title,
            "legal/model_attribution.txt",
        ),
        LegalDocumentDefinition(
            LegalDocumentActivity.DOCUMENT_ONNX_NOTICES,
            R.string.legal_onnx_notices_title,
            "legal/onnxruntime_third_party_notices.txt",
        ),
    )

    fun byId(id: String): LegalDocumentDefinition? = documents.firstOrNull { it.id == id }
}
