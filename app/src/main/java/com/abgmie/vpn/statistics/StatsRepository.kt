package com.abgmie.vpn.statistics

import com.abgmie.vpn.data.db.ServerEntity
import com.abgmie.vpn.data.db.SessionDao
import com.abgmie.vpn.data.db.SessionEntity
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

data class UsageSummary(
    val todayRx: Long, val todayTx: Long,
    val weekRx: Long, val weekTx: Long,
    val monthRx: Long, val monthTx: Long,
    /** Last 7 days (oldest first): day start millis to total bytes */
    val last7: List<Pair<Long, Long>>,
)

/** Local-only usage statistics, measured from WireGuard byte counters. No browsing/DNS data is ever stored. */
@Singleton
class StatsRepository @Inject constructor(private val dao: SessionDao) {
    val sessions: Flow<List<SessionEntity>> = dao.observeAll()

    suspend fun start(s: ServerEntity): Long = dao.insert(
        SessionEntity(serverId = s.id, serverName = s.name, countryCode = s.countryCode, city = s.city, startedAt = System.currentTimeMillis())
    )
    suspend fun update(id: Long, rx: Long, tx: Long) = dao.updateTotals(id, rx, tx, null)
    suspend fun finish(id: Long, rx: Long, tx: Long) = dao.updateTotals(id, rx, tx, System.currentTimeMillis())
    suspend fun closeDangling() = dao.closeDangling(System.currentTimeMillis())
    suspend fun reset() = dao.clear()

    companion object {
        private fun startOfDay(offsetDays: Int = 0): Long = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, offsetDays)
        }.timeInMillis

        fun summarize(list: List<SessionEntity>): UsageSummary {
            val today = startOfDay()
            val week = startOfDay(-6)
            val month = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            fun sum(from: Long, until: Long = Long.MAX_VALUE) = list.filter { it.startedAt in from until until }
            val t = sum(today); val w = sum(week); val m = sum(month)
            val days = (6 downTo 0).map { back ->
                val s = startOfDay(-back); val e = startOfDay(-back + 1)
                s to sum(s, e).sumOf { it.rxBytes + it.txBytes }
            }
            return UsageSummary(
                t.sumOf { it.rxBytes }, t.sumOf { it.txBytes },
                w.sumOf { it.rxBytes }, w.sumOf { it.txBytes },
                m.sumOf { it.rxBytes }, m.sumOf { it.txBytes },
                days,
            )
        }
    }
}
