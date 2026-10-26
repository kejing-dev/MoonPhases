package io.embodit.moonphases

import com.pico.spatial.ui.foundation.dsl.DefaultStage
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope
import io.embodit.moonphases.ui.MoonScreen
import io.embodit.moonphases.ui.theme.MoonTheme

/**
 * App entry. One Progressive Stage (AndroidManifest: `pico.spatial.stage.style=2`, immersion 100 / 50 / 100)
 * holds the whole lesson; there is no WindowContainer (design package: archetype C).
 */
fun mainApp(scope: SpatialAppScope) =
    with(scope) {
        DefaultStage {
            MoonTheme {
                MoonScreen()
            }
        }
    }
