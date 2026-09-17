package kr.baraplt.material.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import java.io.File
import kr.baraplt.material.domain.WorkspaceId
import kr.baraplt.material.data.dao.BomDao
import kr.baraplt.material.data.dao.CompositionDao
import kr.baraplt.material.data.dao.FinishedDao
import kr.baraplt.material.data.dao.MaterialDao
import kr.baraplt.material.data.dao.MonthCloseDao
import kr.baraplt.material.data.dao.MonthlyPlanDao
import kr.baraplt.material.data.dao.MovementDao
import kr.baraplt.material.data.dao.OpeningStockDao
import kr.baraplt.material.data.dao.ProductDao
import kr.baraplt.material.data.dao.ProductPlanDao
import kr.baraplt.material.data.dao.ProductionDao
import kr.baraplt.material.data.entity.DailyProductionEntity
import kr.baraplt.material.data.entity.FinishedCompositionEntity
import kr.baraplt.material.data.entity.FinishedGoodEntity
import kr.baraplt.material.data.entity.MaterialEntity
import kr.baraplt.material.data.entity.MonthCloseEntity
import kr.baraplt.material.data.entity.MonthlyPlanEntity
import kr.baraplt.material.data.entity.OpeningStockEntity
import kr.baraplt.material.data.entity.ProductBomEntity
import kr.baraplt.material.data.entity.ProductEntity
import kr.baraplt.material.data.entity.ProductPlanEntity
import kr.baraplt.material.data.entity.StockMovementEntity

@Database(
    entities = [
        MaterialEntity::class,
        OpeningStockEntity::class,
        StockMovementEntity::class,
        ProductEntity::class,
        ProductBomEntity::class,
        DailyProductionEntity::class,
        ProductPlanEntity::class,
        FinishedGoodEntity::class,
        FinishedCompositionEntity::class,
        MonthlyPlanEntity::class,
        MonthCloseEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun materials(): MaterialDao
    abstract fun openings(): OpeningStockDao
    abstract fun movements(): MovementDao
    abstract fun products(): ProductDao
    abstract fun bom(): BomDao
    abstract fun production(): ProductionDao
    abstract fun productPlans(): ProductPlanDao
    abstract fun finished(): FinishedDao
    abstract fun composition(): CompositionDao
    abstract fun monthlyPlans(): MonthlyPlanDao
    abstract fun closes(): MonthCloseDao

    companion object {
        fun create(context: Context, workspaceId: String): AppDatabase {
            val fileName = resolveFileName(context, workspaceId)
            return Room.databaseBuilder(context, AppDatabase::class.java, fileName)
                .fallbackToDestructiveMigration()
                .build()
        }

        internal fun resolveFileName(context: Context, workspaceId: String): String {
            val named = WorkspaceId.dbFileName(workspaceId)
            val namedFile = context.getDatabasePath(named)
            val legacy = context.getDatabasePath("material.db")
            if (!namedFile.exists() && legacy.exists()) {
                renameDb(legacy, namedFile)
            }
            return named
        }

        private fun renameDb(from: File, to: File) {
            from.renameTo(to)
            File(from.path + "-wal").takeIf { it.exists() }?.renameTo(File(to.path + "-wal"))
            File(from.path + "-shm").takeIf { it.exists() }?.renameTo(File(to.path + "-shm"))
        }
    }
}
