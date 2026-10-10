package com.example.util

enum class CompressionQuality(
    val label: String,
    val description: String,
    val estimatedSavings: String
) {
    ORIGINAL(
        label = "Original",
        description = "No compression. Highest quality, largest file.",
        estimatedSavings = "0% savings"
    ),
    LOW(
        label = "Low (60%)",
        description = "Aggressive compression. Fast upload and preview-friendly output.",
        estimatedSavings = "~60% savings"
    ),
    MEDIUM(
        label = "Medium (40%)",
        description = "Balanced compression. Recommended for most clips.",
        estimatedSavings = "~40% savings"
    ),
    HIGH(
        label = "High (20%)",
        description = "Light compression. Best quality with moderate file reduction.",
        estimatedSavings = "~20% savings"
    )
}
