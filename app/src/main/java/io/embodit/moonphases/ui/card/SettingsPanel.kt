package io.embodit.moonphases.ui.card

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.Icon
import com.pico.spatial.ui.design.IconButton
import com.pico.spatial.ui.design.Link
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.SegmentControl
import com.pico.spatial.ui.design.SegmentItem
import com.pico.spatial.ui.design.Switch
import com.pico.spatial.ui.design.Text
import io.embodit.moonphases.R
import io.embodit.moonphases.domain.AppLanguage
import io.embodit.moonphases.domain.UiState
import io.embodit.moonphases.ui.MoonViewModel
import io.embodit.moonphases.ui.theme.str

/** The settings pop-up above the card (400 x 320 dp): language, sound, restart, about. */
@Composable
fun SettingsPanel(state: UiState, vm: MoonViewModel) {
    GlassRoot {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(str(R.string.settings_title), style = PicoTheme.typography.titleLarge)
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = { vm.setSettingsOpen(false) }) {
                    Icon(painter = painterResource(R.drawable.ic_close), contentDescription = str(R.string.close), modifier = Modifier.size(20.dp))
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(str(R.string.language), style = PicoTheme.typography.bodyMedium, modifier = Modifier.width(120.dp))
                SegmentControl {
                    for (option in AppLanguage.entries) {
                        SegmentItem(
                            selected = state.language == option,
                            onClick = { vm.setLanguage(option) },
                            modifier = Modifier.width(96.dp),
                            title = { Text(if (option == AppLanguage.ZH) str(R.string.lang_zh) else str(R.string.lang_en), maxLines = 1) },
                        )
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(str(R.string.sound), style = PicoTheme.typography.bodyMedium, modifier = Modifier.width(120.dp))
                Switch(checked = state.sound, onCheckedChange = { vm.setSound(it) })
            }
            Link(onClick = { vm.requestRestart() }) { Text(str(R.string.restart_course), maxLines = 1) }
            Link(onClick = { vm.toggleAbout() }) { Text(str(R.string.about), maxLines = 1) }
            if (state.aboutOpen) {
                Text(str(R.string.about_body), style = PicoTheme.typography.labelMedium, color = PicoTheme.colorScheme.labelSecondary, maxLines = 3)
            }
        }
    }
}
