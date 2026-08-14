package lk.ac.ucsc.scs3311.smarthome.ui.plan

/**
 * A floor layout, described as geometry rather than shipped as a photograph.
 *
 * The specification asks for an **abstract, simple** mapping over a floor
 * layout, so the plans are defined as rooms and openings in a unit-less
 * coordinate space and drawn with Compose `Canvas`. Three consequences, all of
 * which we wanted anyway:
 *
 *  - it is crisp at every screen density, with no bitmap to scale;
 *  - the aspect ratio is exact, so [GridMapper]'s letterboxing is exact;
 *  - there is no third-party image licence to track — the plans below are our
 *    own work.
 *
 * Coordinates run from (0,0) at the top-left to ([widthUnits], [heightUnits]).
 */
data class FloorPlanSpec(
    val id: String,
    val displayName: String,
    val widthUnits: Float,
    val heightUnits: Float,
    val rooms: List<PlanRoom>,
    val openings: List<PlanOpening> = emptyList(),
) {
    val aspect: Float get() = widthUnits / heightUnits
}

data class PlanRoom(
    val name: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
)

/** A door or window: a gap drawn across a wall. */
data class PlanOpening(
    val x: Float,
    val y: Float,
    val length: Float,
    val horizontal: Boolean,
    val kind: OpeningKind = OpeningKind.DOOR,
)

enum class OpeningKind { DOOR, WINDOW }

/**
 * The sample plans bundled with the app.
 *
 * A [lk.ac.ucsc.scs3311.smarthome.domain.model.Floor] stores only the plan's
 * [FloorPlanSpec.id], so swapping a floor's layout never touches the devices
 * placed on it.
 */
object PlanLibrary {

    val groundFloor = FloorPlanSpec(
        id = "ground_floor",
        displayName = "Ground floor",
        widthUnits = 16f,
        heightUnits = 12f,
        rooms = listOf(
            PlanRoom("Living room", 0f, 0f, 9f, 7f),
            PlanRoom("Kitchen", 9f, 0f, 7f, 5f),
            PlanRoom("Utility", 9f, 5f, 4f, 3f),
            PlanRoom("Store", 13f, 5f, 3f, 3f),
            PlanRoom("Hall", 0f, 7f, 9f, 5f),
            PlanRoom("Porch", 9f, 8f, 7f, 4f),
        ),
        openings = listOf(
            PlanOpening(3f, 7f, 2f, horizontal = true),
            PlanOpening(9f, 2f, 2f, horizontal = false),
            PlanOpening(11f, 5f, 1.6f, horizontal = true),
            PlanOpening(9f, 9f, 2f, horizontal = false),
            PlanOpening(1f, 0f, 3f, horizontal = true, kind = OpeningKind.WINDOW),
            PlanOpening(12f, 0f, 3f, horizontal = true, kind = OpeningKind.WINDOW),
        ),
    )

    val firstFloor = FloorPlanSpec(
        id = "first_floor",
        displayName = "First floor",
        widthUnits = 16f,
        heightUnits = 12f,
        rooms = listOf(
            PlanRoom("Master bedroom", 0f, 0f, 8f, 6f),
            PlanRoom("Bedroom 2", 8f, 0f, 8f, 6f),
            PlanRoom("Landing", 5f, 6f, 6f, 6f),
            PlanRoom("Bathroom", 0f, 6f, 5f, 6f),
            PlanRoom("Balcony", 11f, 6f, 5f, 6f),
        ),
        openings = listOf(
            PlanOpening(6f, 6f, 2f, horizontal = true),
            PlanOpening(9f, 6f, 1.6f, horizontal = true),
            PlanOpening(5f, 8f, 2f, horizontal = false),
            PlanOpening(11f, 8f, 2f, horizontal = false),
            PlanOpening(2f, 0f, 3f, horizontal = true, kind = OpeningKind.WINDOW),
            PlanOpening(11f, 0f, 3f, horizontal = true, kind = OpeningKind.WINDOW),
        ),
    )

    val annex = FloorPlanSpec(
        id = "annex",
        displayName = "Annex / studio",
        widthUnits = 12f,
        heightUnits = 9f,
        rooms = listOf(
            PlanRoom("Studio", 0f, 0f, 8f, 9f),
            PlanRoom("Kitchenette", 8f, 0f, 4f, 4f),
            PlanRoom("Shower", 8f, 4f, 4f, 5f),
        ),
        openings = listOf(
            PlanOpening(8f, 1f, 2f, horizontal = false),
            PlanOpening(8f, 6f, 2f, horizontal = false),
            PlanOpening(2f, 9f, 2.5f, horizontal = true),
            PlanOpening(1f, 0f, 4f, horizontal = true, kind = OpeningKind.WINDOW),
        ),
    )

    /** A fallback for a floor whose plan id is unknown: bare grid, no rooms. */
    val blank = FloorPlanSpec(
        id = "blank",
        displayName = "Blank grid",
        widthUnits = 4f,
        heightUnits = 3f,
        rooms = emptyList(),
    )

    val all: List<FloorPlanSpec> = listOf(groundFloor, firstFloor, annex, blank)

    fun byId(id: String): FloorPlanSpec = all.firstOrNull { it.id == id } ?: blank
}
