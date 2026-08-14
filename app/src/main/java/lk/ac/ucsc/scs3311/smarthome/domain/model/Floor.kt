package lk.ac.ucsc.scs3311.smarthome.domain.model

/**
 * One storey of the house, with the abstract grid that devices are placed on.
 *
 * The plan image is only a backdrop: device positions are stored as integer
 * cell coordinates, never as pixels. That keeps placement correct across screen
 * sizes, orientations and plan images of different resolutions — the mapping
 * between the two lives in `GridMapper`.
 */
data class Floor(
    val id: String = "",
    val name: String = "",
    /** Storey number: 0 = ground floor, 1 = first floor, -1 = basement. */
    val level: Int = 0,
    /** Asset-relative path of the plan image, e.g. "plans/ground_floor.png". */
    val planAsset: String = "",
    val gridCols: Int = DEFAULT_COLS,
    val gridRows: Int = DEFAULT_ROWS,
) {
    val cellCount: Int get() = gridCols * gridRows

    fun isInsideGrid(x: Int, y: Int): Boolean = x in 0 until gridCols && y in 0 until gridRows

    companion object {
        const val DEFAULT_COLS = 8
        const val DEFAULT_ROWS = 6
        val COL_RANGE = 2..16
        val ROW_RANGE = 2..16
    }
}
