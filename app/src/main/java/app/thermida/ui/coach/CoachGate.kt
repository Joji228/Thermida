package app.thermida.ui.coach

/** True while the coach sheet is open over another screen: the volume keys and barcode reader on Scan stay quiet. */
object CoachGate {
    @Volatile
    var open: Boolean = false
}
