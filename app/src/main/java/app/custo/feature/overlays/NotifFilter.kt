package app.custo.feature.overlays

/** Pure rule for whether a posted notification should fire edge lighting / the island. */
object NotifFilter {
    /** NotificationManager.IMPORTANCE_DEFAULT: the lowest importance that makes sound. */
    const val IMPORTANCE_DEFAULT = 3

    /**
     * @param isUpdate the key was already in the shade (this post updates it)
     * @param onlyAlertOnce Notification.FLAG_ONLY_ALERT_ONCE is set
     * @param importance the ranking's importance, or null if unknown
     * @param channelImportance the channel's importance, or null if unknown
     */
    fun shouldAlert(isUpdate: Boolean, onlyAlertOnce: Boolean, importance: Int?, channelImportance: Int?): Boolean {
        if (isUpdate && onlyAlertOnce) return false
        if (importance != null && importance < IMPORTANCE_DEFAULT) return false
        if (channelImportance != null && channelImportance < IMPORTANCE_DEFAULT) return false
        return true
    }
}
