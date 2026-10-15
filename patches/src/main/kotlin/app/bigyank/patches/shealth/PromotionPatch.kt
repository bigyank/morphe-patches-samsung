package app.bigyank.patches.shealth

import app.morphe.patcher.patch.bytecodePatch
import app.bigyank.patches.shared.Constants.COMPATIBILITY_SHEALTH

/**
 * Hide the Discover+ promotion (ad) card on the Health home screen.
 *
 * Content-scanned against the promotion package's stable log tag; no-ops on
 * builds that ship no promotion display path (same policy as the OOBE scans).
 */
@Suppress("unused")
val hideHomePromotionPanelPatch = bytecodePatch(
    name = "Hide home promotion panel",
    description = "Removes the Discover+ advertising card from the home screen by replicating the app's own offline early-exit in the promotion load path. No-ops on builds without the promotion package.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_SHEALTH)

    execute {
        stubPromotionDisplayPath()
    }
}
