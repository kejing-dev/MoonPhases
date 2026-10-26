package io.embodit.moonphases.scene

import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.Color
import com.pico.spatial.core.container.SpatialViewContent
import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.DirectionalLightComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.HoverEffectComponent
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicallyBasedMaterial
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.ecs.resource.SphereOption
import com.pico.spatial.core.ecs.resource.TextureResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.ui.foundation.content.SpatialViewAttachments
import io.embodit.moonphases.domain.Direction
import io.embodit.moonphases.domain.Lesson
import io.embodit.moonphases.domain.Live
import io.embodit.moonphases.domain.Q4
import io.embodit.moonphases.domain.Screen
import io.embodit.moonphases.domain.V3
import io.embodit.moonphases.domain.dirOf
import io.embodit.moonphases.domain.faceHead
import io.embodit.moonphases.domain.lerp
import io.embodit.moonphases.domain.smoothstep
import io.embodit.moonphases.domain.springStep
import io.embodit.moonphases.domain.toRad
import io.embodit.moonphases.domain.wrap180
import io.embodit.moonphases.domain.wrap360
import io.embodit.moonphases.platform.HeadTracker
import io.embodit.moonphases.platform.MoonLog
import io.embodit.moonphases.ui.MoonViewModel
import io.embodit.moonphases.ui.theme.MoonColors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/** Ids of the pre-built attachment panels (declared once in MoonScreen, with fixed sizes). */
object PanelIds {
    const val CARD = "card"
    const val LABEL = "label"
    const val EDGE_LEFT = "edgeL"
    const val EDGE_RIGHT = "edgeR"
    const val SETTINGS = "settings"
    val ALL = listOf(CARD, LABEL, EDGE_LEFT, EDGE_RIGHT, SETTINGS)
}

/** Test hooks of the debug receiver (`mod M1|M2|M4|M7=0|1`). */
object DebugFlags {
    @Volatile var overshoot = false
    @Volatile var hintArrow = false
    @Volatile var sunEdge = false
}

/**
 * The 3D lesson: Sun lamp, Moon, orbit ring, floor ring, star field, hint arrow and the holders of the
 * attachment panels. Everything is written once per frame from [onFrame] (the ECS system) and never
 * goes through Compose; the ViewModel only gets events and a reading a few times a second.
 *
 * Headings use the domain convention: 0 = -Z, positive = turning left (counter-clockwise from above).
 * θ = wrap360(moonYaw − yaw0) is the Moon's phase angle; the direction reading is the signed shortest
 * angle d = wrap180(target − θ).
 *
 * Resources are built from a work queue paced by the frame rate (factory finding: creating a mesh or a
 * texture waits for the render thread, and the idle emulator renders at 1-2 fps, so a dozen in one
 * frame is a start-up ANR). Eight meshes in all: shared unit sphere / disc / cube scaled per entity.
 */
class MoonScene(private val context: Context, private val vm: MoonViewModel, private val head: HeadTracker) {
    /** Compose composes a panel's content only while this says so (a panel drawn while parked stays broken). */
    val panelShown = mutableStateMapOf<String, Boolean>()

    /** The Moon's collider: the gesture target for "hold the Moon". */
    @Volatile var moonHit: Entity? = null
        private set

    private var root: Entity? = null
    private var attachments: SpatialViewAttachments? = null
    private val shared = SharedResources()
    private var attached = false
    private val meshWork = ArrayDeque<Pair<String, () -> Unit>>()
    private var lastMeshMs = 0L
    private var buildStartMs = 0L

    // --- entities
    private var stars: ModelEntity? = null
    private var sunGroup: Entity? = null
    private var sunCore: ModelEntity? = null
    private var sunGlow: ModelEntity? = null
    private var sunCorona: ModelEntity? = null
    private var light: Entity? = null
    private var moonGroup: Entity? = null
    private var moon: ModelEntity? = null
    private var heldRing: ModelEntity? = null
    private var orbitRing: ModelEntity? = null
    private var floorGroup: Entity? = null
    private var notches: List<ModelEntity> = emptyList()
    private var sunMark: ModelEntity? = null
    private var arrow: ModelEntity? = null
    private var moonMaterial: PhysicallyBasedMaterial? = null
    private var glowMaterial: UnlitMaterial? = null
    private var coronaMaterial: UnlitMaterial? = null
    private var notchIdle: UnlitMaterial? = null
    private var notchLearned: UnlitMaterial? = null
    private var notchTarget: UnlitMaterial? = null
    private val notchState = IntArray(8) { -1 }
    private var unitSphere: MeshResource? = null
    private var unitDisc: MeshResource? = null
    private var quadMesh: MeshResource? = null

    // --- panel slots
    private inner class Slot(val id: String, val holder: Entity, val scale: Float) {
        var panel: Entity? = null
        var shown = false
        var shownFrame = 0L
        var lastPosition: V3 = STOW

        /**
         * The holder is never disabled: a panel entity parented under a disabled holder never appears
         * (Spatial Ruler finding 1). Hidden = stowed 40 m below the floor at a thousandth of its size.
         */
        fun place(position: V3, rotation: Q4) {
            lastPosition = position
            holder.setLocal(position = position, rotation = rotation, scale = V3(scale, scale, scale))
            if (!shown) {
                shown = true
                shownFrame = frame
            }
            val visible = frame - shownFrame >= SHOW_FRAMES
            if (panelShown[id] != visible) panelShown[id] = visible
        }

        fun hide() {
            if (shown || frame == 0L) {
                shown = false
                panelShown[id] = false
                lastPosition = STOW
                holder.setLocal(position = STOW, scale = V3(PARK_SCALE, PARK_SCALE, PARK_SCALE))
            }
        }
    }

    private val slots = LinkedHashMap<String, Slot>()

    // --- per-frame state
    private var frame = 0L
    private var timeS = 0f
    private var moonYaw = 0f
    private var moonYawVel = 0f
    private var moonYawInit = false
    private var tilt = Lesson.TILT_DEG
    private var sunAnchor: V3? = null
    private var cardYaw = 0f
    private var cardInit = false
    private var cardFrom = 0f
    private var cardTo = 0f
    private var cardMoveT = 1f
    private var cardOffSince = -1L
    private var held = false
    private var holdUntilMs = 0L
    private var cooldownUntilMs = 0L
    private var holdT = 0f
    private var targetKey = ""
    private var bestAbsD = 999f
    private var stagnantMs = 0L
    private var lastReadingMs = 0L
    private var lastLive = Live()
    private var opStars = 0f
    private var opSun = 0f
    private var opMoon = 0f
    private var opFloor = 0f
    private var lastLunarK = -1f
    private var lastSolarK = -1f
    private var lastGlowBase = -1f
    private var lastFloorYaw = Float.NaN
    private var sunMoving = false

    // ------------------------------------------------------------------------------------------ setup

    fun attach(content: SpatialViewContent, attachments: SpatialViewAttachments) {
        if (attached) detach()
        val t0 = SystemClock.elapsedRealtime()
        buildStartMs = t0
        this.attachments = attachments
        val root = Entity()
        runCatching { root.setName("moonRoot") }
        content.addEntity(root)
        // An entity added to the SpatialView is not at the Stage origin; move the root once so that
        // its local space equals Stage space (Spatial Ruler's finding, 0.256 m off in z).
        val offset = root.convertPositionFrom(Vector3.ZERO, null)
        val rotation = root.convertRotationFrom(Quat.identity(), null)
        root.components[TransformComponent::class.java]?.apply {
            setPosition(offset)
            setQuaternion(rotation)
        }
        this.root = root
        // groups and the light cost nothing; the meshes and textures are queued (see onFrame)
        sunGroup = Entity().also { root.addChild(it); it.setOpacity(0f) }
        moonGroup = Entity().also { root.addChild(it); it.setOpacity(0f) }
        floorGroup = Entity().also { root.addChild(it); it.setOpacity(0f) }
        light = Entity().also {
            it.components.set(DirectionalLightComponent(Color4(1f, 0.945f, 0.855f, 1f), LIGHT_LUX, false))
            root.addChild(it)
        }
        queueBuild(root)
        for (id in PanelIds.ALL) {
            val holder = Entity()
            runCatching { holder.setName("holder:$id") }
            root.addChild(holder)
            val scale = if (id == PanelIds.CARD || id == PanelIds.SETTINGS) CARD_SCALE else 1f
            slots[id] = Slot(id, holder, scale).also { it.hide() }
        }
        bind(attachments)
        head.start()
        ActiveScene.scene = this
        attached = true
        vm.setSceneReady()
        MoonLog.i("SCENE attached setup_ms=${SystemClock.elapsedRealtime() - t0} queued=${meshWork.size}")
    }

    /** Parents freshly composed panel entities under their holders; true when none is missing. */
    fun bind(attachments: SpatialViewAttachments): Boolean {
        var complete = true
        for (slot in slots.values) {
            if (slot.panel != null) continue
            val panel = attachments.entity(slot.id)
            if (panel == null) {
                complete = false
                continue
            }
            slot.holder.addChild(panel)
            runCatching { panel.setName("panel:${slot.id}") }
            slot.panel = panel
            MoonLog.i("PANEL bound id=${slot.id}")
        }
        return complete
    }

    fun detach() {
        ActiveScene.scene = null
        attached = false
        meshWork.clear()
        head.stop()
        for (slot in slots.values) {
            runCatching { slot.holder.removeAllChildren() }
            slot.panel = null
        }
        slots.clear()
        panelShown.clear()
        moonHit = null
        notches = emptyList()
        runCatching { root?.destroy(true) }
        root = null
        shared.close()
        MoonLog.i("SCENE detached")
    }

    /** One work item per resource creation, in the order the lesson needs them. */
    private fun queueBuild(root: Entity) {
        val sunGroup = sunGroup!!
        val moonGroup = moonGroup!!
        val floorGroup = floorGroup!!
        meshWork += "sun" to {
            val sphere = shared.keep(MeshResource.createSphere(SphereOption(1f).setName("unitSphere")))
            unitSphere = sphere
            sunCore = ModelEntity(sphere, unlit(shared, MoonColors.SunCore)).also {
                it.setUniformScale(SUN_RADIUS)
                sunGroup.addChild(it)
            }
        }
        meshWork += "moonTexture" to {
            val tex = shared.keep(TextureResource.create(Textures.moon(256, 128, 42)))
            moonMaterial = pbr(shared, Color.White, roughness = 0.95f, metallic = 0f, texture = tex)
        }
        meshWork += "moon" to {
            val sphere = unitSphere ?: shared.keep(MeshResource.createSphere(SphereOption(1f).setName("unitSphere"))).also { unitSphere = it }
            val material = moonMaterial ?: pbr(shared, Color.White, roughness = 0.95f, metallic = 0f).also { moonMaterial = it }
            moon = ModelEntity(sphere, material).also {
                it.setUniformScale(MOON_RADIUS)
                runCatching { it.components.set(HoverEffectComponent()) }
                moonGroup.addChild(it)
            }
            // collider a little larger than the Moon so that a pinch at it is easy (taste D-5: gaze highlight + pinch)
            moonHit = Entity().also {
                runCatching { it.setName("moonHit") }
                runCatching { it.components.set(CollisionComponent(listOf(ShapeResource.createSphere(MOON_RADIUS * 1.6f)), PhysicsMaterialResource())) }
                moonGroup.addChild(it)
            }
        }
        meshWork += "stars" to {
            val mat = unlit(shared, Color.White, BlendingMode.TRANSPARENT, opacity = 0.45f, twoSided = true, depthWrite = false)
            stars = ModelEntity(Meshes.stars(1200, 20f, 7, "stars"), mat).also {
                root.addChild(it)
                it.setOpacity(opStars)
            }
        }
        meshWork += "orbit" to {
            val mat = unlit(shared, Color.White, BlendingMode.TRANSPARENT, opacity = 0.12f, twoSided = true, depthWrite = false)
            orbitRing = ModelEntity(Meshes.ring(MOON_DISTANCE - 0.002f, MOON_DISTANCE + 0.002f, "orbit", segments = 96), mat).also {
                root.addChild(it)
                it.setOpacity(opMoon)
            }
        }
        meshWork += "floor" to {
            val disc = shared.keep(Meshes.disc(1f, "unitDisc"))
            unitDisc = disc
            val discMat = unlit(shared, MoonColors.FloorDisc, BlendingMode.TRANSPARENT, opacity = 0.7f, depthWrite = false)
            ModelEntity(disc, discMat).also {
                it.setUniformScale(FLOOR_RADIUS)
                floorGroup.addChild(it)
            }
            sunMark = ModelEntity(disc, unlit(shared, MoonColors.SunGlow)).also {
                it.setUniformScale(0.06f)
                floorGroup.addChild(it)
            }
        }
        meshWork += "notches" to {
            notchIdle = unlit(shared, MoonColors.NotchIdle)
            notchLearned = unlit(shared, MoonColors.NotchLearned)
            notchTarget = unlit(shared, MoonColors.NotchTarget)
            val cube = shared.keep(Meshes.unitCube("unitCube"))
            notches = List(8) { ModelEntity(cube, notchIdle!!).also { floorGroup.addChild(it) } }
            lastFloorYaw = Float.NaN
            notchState.fill(-1)
        }
        meshWork += "glowTextures" to {
            val radial = shared.keep(TextureResource.create(Textures.radial(128, 2.2f)))
            val corona = shared.keep(TextureResource.create(Textures.radial(128, 1.2f)))
            glowMaterial = unlit(shared, MoonColors.SunGlow, BlendingMode.ADD, opacity = GLOW_OPACITY, twoSided = true, depthWrite = false, texture = radial)
            coronaMaterial = unlit(shared, MoonColors.Corona, BlendingMode.ADD, opacity = 0f, twoSided = true, depthWrite = false, texture = corona)
        }
        meshWork += "glow" to {
            val quad = shared.keep(Meshes.quad(1f, 1f, "unitQuad"))
            quadMesh = quad
            val gm = glowMaterial ?: unlit(shared, MoonColors.SunGlow, BlendingMode.ADD, opacity = GLOW_OPACITY, twoSided = true, depthWrite = false).also { glowMaterial = it }
            val cm = coronaMaterial ?: unlit(shared, MoonColors.Corona, BlendingMode.ADD, opacity = 0f, twoSided = true, depthWrite = false).also { coronaMaterial = it }
            sunGlow = ModelEntity(quad, gm).also {
                it.setUniformScale(GLOW_SIZE)
                sunGroup.addChild(it)
            }
            sunCorona = ModelEntity(quad, cm).also {
                it.setUniformScale(CORONA_SIZE)
                sunGroup.addChild(it)
            }
            lastSolarK = -1f
        }
        meshWork += "heldRing" to {
            val mat = unlit(shared, MoonColors.Interaction, BlendingMode.TRANSPARENT, opacity = 0.85f, twoSided = true, depthWrite = false)
            heldRing = ModelEntity(Meshes.ring(MOON_RADIUS * 1.25f, MOON_RADIUS * 1.4f, "heldRing"), mat).also {
                it.setLocal(rotation = Q4.pitch(90f))
                it.setVisible(false)
                moonGroup.addChild(it)
            }
        }
        meshWork += "arrow" to {
            arrow = ModelEntity(Meshes.bentArrow(ARROW_SIZE, "hintArrow"), unlit(shared, MoonColors.Interaction, twoSided = true)).also {
                root.addChild(it)
                it.setVisible(false)
            }
        }
    }

    /** Two resources per frame while frames are fast (headset), one every 2.5 s while they are slow (idle emulator). */
    private fun runMeshWork(dt: Float, now: Long) {
        if (meshWork.isEmpty()) return
        val n = if (dt < FAST_FRAME_SEC) 2 else if (now - lastMeshMs >= SLOW_MESH_GAP_MS) 1 else 0
        repeat(n) {
            val (name, work) = meshWork.removeFirstOrNull() ?: return
            val t0 = SystemClock.elapsedRealtime()
            runCatching { work() }.onFailure { MoonLog.e("SCENE build '$name' failed: $it", it) }
            lastMeshMs = SystemClock.elapsedRealtime()
            MoonLog.i("SCENE built $name ms=${lastMeshMs - t0} left=${meshWork.size}")
        }
        if (meshWork.isEmpty()) MoonLog.i("SCENE build done total_ms=${now - buildStartMs}")
    }

    // ---------------------------------------------------------------------------------------- gestures

    /** Pinch-and-hold on the Moon freezes it (M6); a short tap holds it for 3 s (N7 fallback). */
    fun setHeld(on: Boolean, tapped: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (on) {
            held = true
            holdUntilMs = 0L
        } else if (tapped) {
            held = true
            holdUntilMs = now + TAP_HOLD_MS
        } else {
            release(now)
        }
        MoonLog.i("MOON held=$held tapped=$tapped")
    }

    private fun release(now: Long) {
        held = false
        holdUntilMs = 0L
        cooldownUntilMs = now + RELEASE_COOLDOWN_MS
        holdT = 0f
    }

    /** `theta <deg>`: moves the Sun so that the Moon, which stays in front of the camera, shows this phase angle. */
    fun debugTheta(theta: Float) {
        val pose = head.pose() ?: return
        val y = wrap360(pose.yaw - theta)
        vm.yaw0 = y
        vm.yaw0Target = y
        vm.yawSet = true
        moonYaw = pose.yaw
        moonYawVel = 0f
        MoonLog.i("DEBUG theta=$theta yaw0=$y headYaw=${pose.yaw}")
    }

    fun debugLost(lost: Boolean) {
        head.forceLost = lost
    }

    /** `dump`: one log line with the head pose, the headings and where every panel holder is. */
    fun dumpLine(): String {
        val pose = head.pose()
        val sb = StringBuilder()
        sb.append("DUMP head=").append(pose?.position).append(" yaw=").append(pose?.yaw).append(" pitch=").append(pose?.pitch)
        sb.append(" yaw0=").append(vm.yaw0).append(" moonYaw=").append(moonYaw).append(" theta=").append(wrap360(moonYaw - vm.yaw0))
        sb.append(" floor=").append(vm.floorOrigin).append(" meshLeft=").append(meshWork.size).append(" screen=").append(vm.state.value.screen)
        for (slot in slots.values) {
            val engine = runCatching { slot.holder.convertPositionTo(Vector3.ZERO, root).domain() }.getOrNull()
            sb.append(" | ").append(slot.id).append(" shown=").append(slot.shown).append(" bound=").append(slot.panel != null)
                .append(" composed=").append(panelShown[slot.id]).append(" at=").append(slot.lastPosition).append(" engine=").append(engine)
        }
        return sb.toString()
    }

    // ------------------------------------------------------------------------------------------- frame

    fun onFrame(deltaTime: Float) {
        if (!attached) return
        val dt = if (deltaTime.isFinite() && deltaTime > 0f) deltaTime.coerceAtMost(0.1f) else 0.016f
        frame++
        timeS += dt
        val now = SystemClock.elapsedRealtime()
        runMeshWork(if (deltaTime.isFinite() && deltaTime > 0f) deltaTime else 1f, now)
        attachments?.let { if (slots.values.any { s -> s.panel == null }) bind(it) }
        val pose = head.pose() ?: return
        val state = vm.state.value
        val headPos = pose.position
        val headYaw = pose.yaw
        val lost = head.lost(now)

        // The Sun's heading: taken from the head the first time it is needed (also after a debug goto).
        if (!vm.yawSet && state.screen.ordinal >= Screen.S02.ordinal) {
            vm.yaw0 = wrap360(headYaw)
            vm.yaw0Target = vm.yaw0
            vm.yawSet = true
        }
        if (vm.floorOrigin == null && state.screen.ordinal >= Screen.S02.ordinal) {
            vm.floorOrigin = V3(headPos.x, floorHeight(headPos.y), headPos.z)
            MoonLog.i("SCENE floor origin=${vm.floorOrigin} headY=${headPos.y}")
        }

        // "I can't turn that far": the Sun and everything tied to it turn 90 degrees in 0.8 s
        val yawDiff = wrap180(vm.yaw0Target - vm.yaw0)
        if (abs(yawDiff) > 0.01f) {
            val step = (SUN_MOVE_DEG_PER_S * dt).coerceAtMost(abs(yawDiff))
            vm.yaw0 = wrap360(vm.yaw0 + if (yawDiff > 0) step else -step)
            sunMoving = true
            holdT = 0f
        } else if (sunMoving) {
            sunMoving = false
            resetStagnation(now)
        }
        val yaw0 = vm.yaw0

        // Moon heading: critically damped spring after the head's yaw; frozen while held
        if (held && holdUntilMs > 0L && now >= holdUntilMs) release(now)
        if (!moonYawInit) {
            moonYaw = headYaw
            moonYawInit = true
        }
        if (!held) {
            val error = wrap180(moonYaw - headYaw)
            val (e, v) = springStep(error, moonYawVel, MOON_TAU, dt)
            moonYawVel = v
            moonYaw = wrap360(headYaw + e)
        }
        val theta = wrap360(moonYaw - yaw0)

        // orbit tilt: 10 degrees, flattened to 0 in 0.6 s
        val tiltTarget = if (state.flat) 0f else Lesson.TILT_DEG
        val tiltStep = Lesson.TILT_DEG / FLATTEN_SEC * dt
        tilt = if (abs(tiltTarget - tilt) <= tiltStep) tiltTarget else tilt + if (tiltTarget > tilt) tiltStep else -tiltStep

        // --- fades by state
        opStars = approach(opStars, 1f, dt * 2f)
        opSun = approach(opSun, if (state.sunShown) 1f else 0f, dt)
        opMoon = approach(opMoon, if (state.moonShown) 1f else 0f, dt)
        opFloor = approach(opFloor, if (state.floorShown) 1f else 0f, dt)
        stars?.setOpacity(opStars)
        sunGroup?.setOpacity(opSun)
        moonGroup?.setOpacity(opMoon)
        orbitRing?.setOpacity(opMoon)
        floorGroup?.setOpacity(opFloor)

        // --- the Sun: 4 m along yaw0, at head height, following head translation with a 0.3 s lag
        val anchor = sunAnchor?.let { it + (headPos - it) * (1f - exp(-dt / SUN_LAG_SEC)) } ?: headPos
        sunAnchor = anchor
        val sunDir = dirOf(yaw0)
        val sunPos = anchor + sunDir * SUN_DISTANCE
        val billboard = faceHead(sunPos, headPos)
        sunCore?.setLocal(position = sunPos)
        sunGlow?.setLocal(position = sunPos, rotation = billboard)
        sunCorona?.setLocal(position = sunPos + (headPos - sunPos).normalized() * 0.01f, rotation = billboard)
        // the light shines from the Sun towards the user: the entity's -Z must point along -sunDir
        light?.setLocal(position = sunPos, rotation = Q4.yaw(yaw0 + 180f))

        // --- the Moon: 0.62 m from the head along moonYaw, elevation tilt·cos θ
        val elevation = (tilt * cos(theta.toRad())).toRad()
        val moonPos = headPos + dirOf(moonYaw) * (MOON_DISTANCE * cos(elevation)) + V3(0f, MOON_DISTANCE * sin(elevation), 0f)
        moonGroup?.setLocal(position = moonPos, rotation = Q4.yaw(moonYaw))
        heldRing?.setVisible(held)
        orbitRing?.setLocal(position = headPos, rotation = Q4.yaw(yaw0) * Q4.pitch(tilt))

        // --- eclipses (code-driven, windows of 4 degrees, strength falls with the tilt)
        val strength = 1f - tilt / Lesson.TILT_DEG
        val solarK = (1f - abs(wrap180(theta)) / Lesson.ECLIPSE_TOLERANCE_DEG).coerceIn(0f, 1f) * strength
        val lunarK = (1f - abs(wrap180(theta - 180f)) / Lesson.ECLIPSE_TOLERANCE_DEG).coerceIn(0f, 1f) * strength
        val glowBase = if (abs(wrap180(theta)) < 20f) 0.5f else 1f
        if (solarK != lastSolarK || glowBase != lastGlowBase) {
            lastSolarK = solarK
            lastGlowBase = glowBase
            sunCore?.setOpacity(1f - solarK)
            coronaMaterial?.setOpacity(solarK * 0.9f)
            glowMaterial?.setOpacity(GLOW_OPACITY * glowBase * (1f - solarK))
        }
        if (lunarK != lastLunarK) {
            lastLunarK = lunarK
            val c = MoonColors.EclipseCopper
            moonMaterial?.setBaseColor(Color4(lerp(1f, c.red, lunarK), lerp(1f, c.green, lunarK), lerp(1f, c.blue, lunarK), 1f))
            moonMaterial?.setEmissiveColor(Color4(c.red * 0.35f * lunarK, c.green * 0.35f * lunarK, c.blue * 0.35f * lunarK, 1f))
        }

        // --- floor ring: notches at yaw0 + k·45 around the lesson origin; the sun mark outside the disc
        vm.floorOrigin?.let { origin ->
            floorGroup?.setLocal(position = origin)
            if (yaw0 != lastFloorYaw) {
                lastFloorYaw = yaw0
                if (notches.size == 8) {
                    for (k in 0 until 8) {
                        val yaw = yaw0 + k * 45f
                        notches[k].setLocal(position = dirOf(yaw) * NOTCH_RADIUS + V3(0f, 0.002f, 0f), rotation = Q4.yaw(yaw))
                    }
                }
                sunMark?.setLocal(position = dirOf(yaw0) * (FLOOR_RADIUS + 0.12f))
            }
            if (notches.size == 8) {
                val targetNotch = state.targetNotch
                for (k in 0 until 8) {
                    val want = if (k == targetNotch) 2 else if (state.notchLearned(k)) 1 else 0
                    if (notchState[k] != want) {
                        notchState[k] = want
                        notches[k].setMaterial(
                            when (want) {
                                2 -> notchTarget!!
                                1 -> notchLearned!!
                                else -> notchIdle!!
                            }
                        )
                    }
                    val pulse = if (want == 2) 1f + 0.18f * sin(timeS * 2f * PI.toFloat()) else 1f
                    notches[k].transformOrNull?.setScaleVector(Vector3(NOTCH_W * pulse, NOTCH_H, NOTCH_L * pulse))
                }
            }
        }

        // --- judging: inside the tolerance for 0.8 s, once, never while held / moving / overlay open
        val target = state.target
        val judging = state.judging && !sunMoving && !lost && !held
        var direction = Direction.NONE
        if (target != null) {
            if (state.targetKey != targetKey) {
                targetKey = state.targetKey
                bestAbsD = 999f
                resetStagnation(now)
                holdT = 0f
            }
            val d = wrap180(target - theta)
            direction = Lesson.direction(d)
            if (judging) {
                if (abs(d) <= state.tolerance) {
                    holdT += dt
                    if (holdT >= Lesson.HOLD_SEC && now >= cooldownUntilMs) {
                        holdT = 0f
                        cooldownUntilMs = now + RELEASE_COOLDOWN_MS
                        vm.onMatched()
                    }
                } else {
                    holdT = 0f
                }
                if (abs(d) < bestAbsD - 10f) {
                    bestAbsD = abs(d)
                    stagnantMs = now
                }
            } else {
                holdT = 0f
                stagnantMs += (dt * 1000f).toLong()
            }
        } else {
            targetKey = ""
            holdT = 0f
        }
        if (DebugFlags.overshoot) direction = Direction.BACK
        val stagnant = if (target != null) now - stagnantMs else 0L
        val hintArrow = target != null && (state.hintAsked || stagnant > HINT_AFTER_MS || DebugFlags.hintArrow)
        val cantTurn = target != null && (state.hintAsked || stagnant > CANT_TURN_AFTER_MS) && !state.sunMovedHint

        // --- hint arrow beside the Moon, on the side to turn to, swinging a little
        arrow?.let { a ->
            if (hintArrow && target != null && opMoon > 0.5f) {
                val d = wrap180(target - theta)
                val left = d > 0f
                val side = dirOf(moonYaw + if (left) 90f else -90f)
                val wobble = 0.012f * sin(timeS * 4f * PI.toFloat())
                val pos = moonPos + side * (ARROW_OFFSET + wobble)
                val k = if (left) 1f else -1f
                a.setLocal(position = pos, rotation = faceHead(pos, headPos), scale = V3(k, 1f, 1f))
                a.setVisible(true)
            } else {
                a.setVisible(false)
            }
        }

        // --- the lesson card: 0.78 m away, 24 degrees down, lazily following the head's yaw
        if (!cardInit) {
            cardYaw = headYaw
            cardTo = headYaw
            cardInit = true
        }
        val off = wrap180(headYaw - cardYaw)
        if (cardMoveT < 1f) {
            cardMoveT = (cardMoveT + dt / CARD_MOVE_SEC).coerceAtMost(1f)
            cardYaw = wrap360(cardFrom + wrap180(cardTo - cardFrom) * smoothstep(cardMoveT))
        } else if (pose.pitch <= CARD_FREEZE_PITCH) {
            if (abs(off) > CARD_FOLLOW_DEG) {
                if (cardOffSince < 0) cardOffSince = now
                if (now - cardOffSince >= CARD_FOLLOW_DELAY_MS) {
                    cardFrom = cardYaw
                    cardTo = headYaw
                    cardMoveT = 0f
                    cardOffSince = -1
                }
            } else {
                cardOffSince = -1
            }
        }
        val cardPos = headPos + dirOf(cardYaw) * (CARD_DISTANCE * cos(CARD_DOWN_DEG.toRad())) + V3(0f, -CARD_DISTANCE * sin(CARD_DOWN_DEG.toRad()), 0f)
        val cardRot = Q4.yaw(cardYaw) * Q4.pitch(-CARD_TILT_DEG)
        slots[PanelIds.CARD]?.place(cardPos, cardRot)

        // settings pop-up beside the card (to its left, same height: above it, it would sit over the Moon)
        slots[PanelIds.SETTINGS]?.let { s ->
            if (state.settingsOpen) {
                val pos = cardPos + dirOf(cardYaw + 90f) * SETTINGS_BESIDE_M + V3(0f, 0.02f, 0f)
                s.place(pos, faceHead(pos, headPos))
            } else s.hide()
        }

        // phase name sign above the Moon
        slots[PanelIds.LABEL]?.let { s ->
            if (state.phaseLabelShown && opMoon > 0.5f) {
                val pos = moonPos + V3(0f, MOON_RADIUS + LABEL_ABOVE, 0f)
                s.place(pos, faceHead(pos, headPos))
            } else s.hide()
        }

        // "the Sun is this way" at the edge of the view (M7)
        val sunOff = wrap180(yaw0 - headYaw)
        val edge =
            when {
                DebugFlags.sunEdge -> -1
                state.screen.ordinal < Screen.S02.ordinal || opSun < 0.5f -> 0
                sunOff > SUN_EDGE_DEG -> -1
                sunOff < -SUN_EDGE_DEG -> 1
                else -> 0
            }
        slots[PanelIds.EDGE_LEFT]?.let { s ->
            if (edge < 0) {
                val pos = headPos + dirOf(headYaw + EDGE_LABEL_DEG) * EDGE_LABEL_DISTANCE + V3(0f, -0.04f, 0f)
                s.place(pos, faceHead(pos, headPos))
            } else s.hide()
        }
        slots[PanelIds.EDGE_RIGHT]?.let { s ->
            if (edge > 0) {
                val pos = headPos + dirOf(headYaw - EDGE_LABEL_DEG) * EDGE_LABEL_DISTANCE + V3(0f, -0.04f, 0f)
                s.place(pos, faceHead(pos, headPos))
            } else s.hide()
        }

        // --- the reading for the card, ten times a second at most
        if (now - lastReadingMs >= READING_MS) {
            lastReadingMs = now
            val live = Live(theta = theta, direction = direction, hintArrow = hintArrow, cantTurn = cantTurn, trackingLost = lost, moonHeld = held, sunEdge = edge, sunMoving = sunMoving)
            if (live != lastLive) {
                lastLive = live
                vm.updateLive(live)
            }
        }
    }

    /**
     * Height of the floor under the user (design N6). On a headset the Stage origin is on the floor
     * when the head is at standing height; the emulator room keeps its floor at head − 1.25 m.
     */
    private fun floorHeight(headY: Float): Float =
        when {
            isEmulator -> headY - EMULATOR_EYE_HEIGHT
            headY > 0.8f -> 0f
            else -> headY - STANDING_EYE_HEIGHT
        }

    private val isEmulator: Boolean by lazy {
        Build.FINGERPRINT.contains("emulator", ignoreCase = true) || Build.SUPPORTED_ABIS.any { it.startsWith("x86") }
    }

    private fun resetStagnation(now: Long) {
        stagnantMs = now
    }

    private fun approach(value: Float, target: Float, step: Float): Float =
        if (abs(target - value) <= step) target else value + if (target > value) step else -step

    companion object {
        const val SUN_DISTANCE = 4.0f
        const val SUN_RADIUS = 0.245f
        const val GLOW_SIZE = 1.6f
        const val CORONA_SIZE = 1.1f
        const val GLOW_OPACITY = 0.35f
        const val LIGHT_LUX = 2000f
        const val SUN_LAG_SEC = 0.3f
        const val SUN_MOVE_DEG_PER_S = 90f / 0.8f
        const val MOON_DISTANCE = 0.62f
        const val MOON_RADIUS = 0.038f
        const val MOON_TAU = 0.12f
        const val FLATTEN_SEC = 0.6f
        const val FLOOR_RADIUS = 1.1f
        const val NOTCH_RADIUS = 1.0f
        const val NOTCH_W = 0.03f
        const val NOTCH_H = 0.004f
        const val NOTCH_L = 0.10f
        const val ARROW_SIZE = 0.08f
        const val ARROW_OFFSET = 0.10f
        const val LABEL_ABOVE = 0.07f
        const val CARD_DISTANCE = 0.78f
        const val CARD_DOWN_DEG = 24f
        const val CARD_TILT_DEG = 15f
        const val CARD_FOLLOW_DEG = 18f
        const val CARD_FOLLOW_DELAY_MS = 250L
        const val CARD_MOVE_SEC = 0.35f
        const val CARD_FREEZE_PITCH = 50f
        const val CARD_SCALE = 0.75f
        const val SETTINGS_BESIDE_M = 0.40f
        const val SUN_EDGE_DEG = 45f
        const val EDGE_LABEL_DEG = 32f
        const val EDGE_LABEL_DISTANCE = 0.9f
        const val HINT_AFTER_MS = 10_000L
        const val CANT_TURN_AFTER_MS = 20_000L
        const val RELEASE_COOLDOWN_MS = 1000L
        const val TAP_HOLD_MS = 3000L
        const val READING_MS = 100L
        const val SHOW_FRAMES = 2L
        const val FAST_FRAME_SEC = 0.04f
        const val SLOW_MESH_GAP_MS = 2500L
        const val EMULATOR_EYE_HEIGHT = 1.25f
        const val STANDING_EYE_HEIGHT = 1.6f
        private val STOW = V3(0f, -40f, 0f)
        const val PARK_SCALE = 0.001f
    }
}
