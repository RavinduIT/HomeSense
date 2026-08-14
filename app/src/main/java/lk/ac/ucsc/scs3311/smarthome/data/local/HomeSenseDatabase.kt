package lk.ac.ucsc.scs3311.smarthome.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        FloorEntity::class,
        DeviceLayoutEntity::class,
        UsageEventEntity::class,
        AlertEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class HomeSenseDatabase : RoomDatabase() {

    abstract fun floorDao(): FloorDao
    abstract fun deviceLayoutDao(): DeviceLayoutDao
    abstract fun usageDao(): UsageDao
    abstract fun alertDao(): AlertDao

    companion object {
        private const val NAME = "homesense.db"

        @Volatile
        private var instance: HomeSenseDatabase? = null

        fun get(context: Context): HomeSenseDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    HomeSenseDatabase::class.java,
                    NAME,
                )
                    // The cache is rebuildable from the cloud, so a schema change
                    // costs nothing but a re-sync. No migration ceremony needed.
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
    }
}
