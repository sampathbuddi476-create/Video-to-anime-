package com.example.util

enum class CompressionQuality(
    val label: String,
    val estimatedSavings: String,
    val bitrate: String
) {
    ORIGINAL("Original", "No compression", "Original"),
    LOW("Low (60% reduction)", "~60%", "2M"),
    MEDIUM("Medium (40% reduction)", "~40%", "4M"),
    HIGH("High (20% reduction)", "~20%", "6M")
}
