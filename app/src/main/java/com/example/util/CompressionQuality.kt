package com.example.util

enum class CompressionQuality(
    val label: String,
    val estimatedSavings: String,
    val bitrate: String,
    val description: String,
    val targetHeight: Int?
) {
    ORIGINAL(
        "Original",
        "No compression",
        "Original",
        "Use the original clip without any quality reduction.",
        null
    ),
    LOW(
        "Low (60% reduction)",
        "~60%",
        "2M",
        "Fastest upload and rendering, best for quick previews.",
        360
    ),
    MEDIUM(
        "Medium (40% reduction)",
        "~40%",
        "4M",
        "Balanced quality and speed for typical clips.",
        540
    ),
    HIGH(
        "High (20% reduction)",
        "~20%",
        "6M",
        "Best visual fidelity with a moderate upload cost.",
        720
    )
}
