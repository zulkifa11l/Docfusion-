package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.example.util.AdMobManager
import java.io.File

data class MockDocument(
    val id: String,
    val name: String,
    val fileType: String,
    val size: String,
    val date: String,
    val isPremium: Boolean = false,
    val isEncrypted: Boolean = false
)

class DocfusionViewModel(application: Application) : AndroidViewModel(application) {

    private val _documents = MutableStateFlow<List<MockDocument>>(emptyList())
    val documents: StateFlow<List<MockDocument>> = _documents.asStateFlow()

    private val _unlockedFeatures = MutableStateFlow<Set<String>>(emptySet())
    val unlockedFeatures: StateFlow<Set<String>> = _unlockedFeatures.asStateFlow()

    init {
        // Initialize Google Mobile Ads SDK immediately
        AdMobManager.initialize(application)
        loadMockDocuments()
    }

    private fun loadMockDocuments() {
        _documents.value = listOf(
            MockDocument("1", "Invoice_May2026.pdf", "PDF", "1.2 MB", "2026-05-15"),
            MockDocument("2", "LeaseAgreement.pdf", "PDF", "4.8 MB", "2026-05-24", isEncrypted = true),
            MockDocument("3", "Receipt_CameraScan.jpg", "Image", "450 KB", "2026-06-01"),
            MockDocument("4", "Resume_JohnDoe.docx", "Word", "320 KB", "2026-06-03"),
            MockDocument("5", "Docfusion_Confidential_Whitepaper.pdf", "PDF", "12.4 MB", "2026-06-05", isPremium = true)
        )
    }

    fun addDocument(name: String, extension: String, size: String, isPremium: Boolean = false) {
        val newDoc = MockDocument(
            id = System.currentTimeMillis().toString(),
            name = if (name.endsWith(".$extension")) name else "$name.$extension",
            fileType = extension.uppercase(),
            size = size,
            date = "Today",
            isPremium = isPremium
        )
        _documents.value = listOf(newDoc) + _documents.value
    }

    fun unlockFeature(featureKey: String) {
        _unlockedFeatures.value = _unlockedFeatures.value + featureKey
    }

    fun isFeatureUnlocked(featureKey: String): Boolean {
        return _unlockedFeatures.value.contains(featureKey)
    }
}
