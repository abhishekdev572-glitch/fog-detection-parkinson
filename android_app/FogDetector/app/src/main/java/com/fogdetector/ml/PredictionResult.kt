package com.fogdetector.ml

/**
 * Holds one inference result from [FogDetectionModel.predict].
 *
 * @param fogProbability  Raw FOG sigmoid output (0 – 1).
 * @param activityProbs   Raw activity softmax for each activity class.
 * @param isFog           True when fogProbability ≥ the configured threshold.
 */
data class PredictionResult(
    val fogProbability: Float,
    val activityProbs:  FloatArray,   // index: 0=Other 1=Stationary 2=Walking 3=Shuffling
    val isFog:          Boolean,
    val activityClass : Int,
    val timestampMs:    Long = System.currentTimeMillis()
) {
    val activityIndex: Int
        get() = activityProbs.indices.maxByOrNull { activityProbs[it] } ?: 0

    val activityLabel: String
        get() = ACTIVITY_LABELS.getOrElse(activityIndex) { "Unknown" }

    val fogPercent: Int
        get() = (fogProbability * 100).toInt().coerceIn(0, 100)

    companion object {
        val ACTIVITY_LABELS = arrayOf("Other", "Stationary", "Walking", "Shuffling")
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PredictionResult) return false
        return isFog == other.isFog &&
               fogProbability == other.fogProbability &&
               activityProbs.contentEquals(other.activityProbs)
    }
    override fun hashCode(): Int = activityProbs.contentHashCode() xor fogProbability.hashCode()
}
