package caib.charge.data.time

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** Reads the current device zone without freezing it for the application's lifetime. */
class SystemZoneClock(
    private val delegate: Clock = Clock.systemUTC(),
    private val currentZone: () -> ZoneId = ZoneId::systemDefault,
) : Clock() {
    override fun getZone(): ZoneId = currentZone()

    // An explicit zone is a fixed choice, even when it equals the device's zone now.
    override fun withZone(zone: ZoneId): Clock = delegate.withZone(zone)

    override fun instant(): Instant = delegate.instant()

    override fun millis(): Long = delegate.millis()
}
