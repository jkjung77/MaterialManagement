package kr.baraplt.material.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
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

@Dao
interface MaterialDao {
    @Query("SELECT * FROM materials WHERE isActive = 1 ORDER BY codeNo")
    fun observeActive(): Flow<List<MaterialEntity>>

    @Query("SELECT * FROM materials ORDER BY codeNo")
    fun observeAll(): Flow<List<MaterialEntity>>

    @Query("SELECT * FROM materials ORDER BY codeNo")
    suspend fun getAll(): List<MaterialEntity>

    @Query("SELECT * FROM materials WHERE id = :id")
    suspend fun get(id: Long): MaterialEntity?

    @Query("SELECT COALESCE(MAX(codeNo), 0) FROM materials")
    suspend fun maxCode(): Int

    @Insert
    suspend fun insert(item: MaterialEntity): Long

    @Insert
    suspend fun insertAll(items: List<MaterialEntity>): List<Long>

    @Update
    suspend fun update(item: MaterialEntity)

    @Query("DELETE FROM materials")
    suspend fun clear()
}

@Dao
interface OpeningStockDao {
    @Query("SELECT * FROM opening_stocks WHERE yearMonth = :yearMonth")
    suspend fun forMonth(yearMonth: String): List<OpeningStockEntity>

    @Query("SELECT * FROM opening_stocks")
    suspend fun getAll(): List<OpeningStockEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: OpeningStockEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<OpeningStockEntity>)

    @Query("DELETE FROM opening_stocks")
    suspend fun clear()
}

@Dao
interface MovementDao {
    @Query("SELECT * FROM stock_movements WHERE occurredOn LIKE :prefix || '%' ORDER BY occurredOn DESC, createdAt DESC")
    fun observeMonth(prefix: String): Flow<List<StockMovementEntity>>

    @Query("SELECT * FROM stock_movements ORDER BY occurredOn DESC, createdAt DESC")
    fun observeAll(): Flow<List<StockMovementEntity>>

    @Query("SELECT * FROM stock_movements WHERE occurredOn LIKE :prefix || '%'")
    suspend fun forMonth(prefix: String): List<StockMovementEntity>

    @Query("SELECT * FROM stock_movements")
    suspend fun getAll(): List<StockMovementEntity>

    @Insert
    suspend fun insert(item: StockMovementEntity): Long

    @Insert
    suspend fun insertAll(items: List<StockMovementEntity>)

    @Delete
    suspend fun delete(item: StockMovementEntity)

    @Query("DELETE FROM stock_movements")
    suspend fun clear()
}

@Dao
interface ProductDao {
    @Query("SELECT * FROM products WHERE isActive = 1 ORDER BY codeNo")
    fun observeActive(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products ORDER BY codeNo")
    suspend fun getAll(): List<ProductEntity>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun get(id: Long): ProductEntity?

    @Query("SELECT COALESCE(MAX(codeNo), 0) FROM products")
    suspend fun maxCode(): Int

    @Insert
    suspend fun insert(item: ProductEntity): Long

    @Insert
    suspend fun insertAll(items: List<ProductEntity>): List<Long>

    @Update
    suspend fun update(item: ProductEntity)

    @Query("DELETE FROM products")
    suspend fun clear()
}

@Dao
interface BomDao {
    @Query("SELECT * FROM product_bom ORDER BY productId, sortOrder")
    fun observeAll(): Flow<List<ProductBomEntity>>

    @Query("SELECT * FROM product_bom WHERE productId = :productId ORDER BY sortOrder")
    suspend fun forProduct(productId: Long): List<ProductBomEntity>

    @Query("SELECT * FROM product_bom")
    suspend fun getAll(): List<ProductBomEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ProductBomEntity>)

    @Query("DELETE FROM product_bom WHERE productId = :productId")
    suspend fun deleteForProduct(productId: Long)

    @Query("DELETE FROM product_bom")
    suspend fun clear()
}

@Dao
interface ProductionDao {
    @Query("SELECT * FROM daily_production WHERE workDate LIKE :prefix || '%' ORDER BY workDate")
    fun observeMonth(prefix: String): Flow<List<DailyProductionEntity>>

    @Query("SELECT * FROM daily_production WHERE workDate LIKE :prefix || '%'")
    suspend fun forMonth(prefix: String): List<DailyProductionEntity>

    @Query("SELECT * FROM daily_production")
    suspend fun getAll(): List<DailyProductionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: DailyProductionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<DailyProductionEntity>)

    @Query("DELETE FROM daily_production WHERE productId = :productId AND workDate = :workDate")
    suspend fun delete(productId: Long, workDate: String)

    @Query("DELETE FROM daily_production")
    suspend fun clear()
}

@Dao
interface ProductPlanDao {
    @Query("SELECT * FROM product_plans WHERE yearMonth = :yearMonth")
    suspend fun forMonth(yearMonth: String): List<ProductPlanEntity>

    @Query("SELECT * FROM product_plans")
    suspend fun getAll(): List<ProductPlanEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ProductPlanEntity)

    @Query("DELETE FROM product_plans")
    suspend fun clear()
}

@Dao
interface FinishedDao {
    @Query("SELECT * FROM finished_goods WHERE isActive = 1 ORDER BY codeNo")
    fun observeActive(): Flow<List<FinishedGoodEntity>>

    @Query("SELECT * FROM finished_goods ORDER BY codeNo")
    suspend fun getAll(): List<FinishedGoodEntity>

    @Query("SELECT COALESCE(MAX(codeNo), 0) FROM finished_goods")
    suspend fun maxCode(): Int

    @Insert
    suspend fun insert(item: FinishedGoodEntity): Long

    @Insert
    suspend fun insertAll(items: List<FinishedGoodEntity>): List<Long>

    @Update
    suspend fun update(item: FinishedGoodEntity)

    @Query("DELETE FROM finished_goods")
    suspend fun clear()
}

@Dao
interface CompositionDao {
    @Query("SELECT * FROM finished_composition ORDER BY finishedGoodId, sortOrder")
    fun observeAll(): Flow<List<FinishedCompositionEntity>>

    @Query("SELECT * FROM finished_composition")
    suspend fun getAll(): List<FinishedCompositionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<FinishedCompositionEntity>)

    @Query("DELETE FROM finished_composition WHERE finishedGoodId = :finishedId")
    suspend fun deleteForFinished(finishedId: Long)

    @Query("DELETE FROM finished_composition")
    suspend fun clear()
}

@Dao
interface MonthlyPlanDao {
    @Query("SELECT * FROM monthly_plans WHERE yearMonth = :yearMonth")
    fun observeMonth(yearMonth: String): Flow<List<MonthlyPlanEntity>>

    @Query("SELECT * FROM monthly_plans WHERE yearMonth = :yearMonth")
    suspend fun forMonth(yearMonth: String): List<MonthlyPlanEntity>

    @Query("SELECT * FROM monthly_plans")
    suspend fun getAll(): List<MonthlyPlanEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: MonthlyPlanEntity)

    @Query("DELETE FROM monthly_plans")
    suspend fun clear()
}

@Dao
interface MonthCloseDao {
    @Query("SELECT * FROM month_closes ORDER BY yearMonth DESC")
    fun observeAll(): Flow<List<MonthCloseEntity>>

    @Query("SELECT * FROM month_closes WHERE yearMonth = :yearMonth")
    suspend fun get(yearMonth: String): MonthCloseEntity?

    @Query("SELECT * FROM month_closes")
    suspend fun getAll(): List<MonthCloseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: MonthCloseEntity)

    @Query("DELETE FROM month_closes WHERE yearMonth = :yearMonth")
    suspend fun delete(yearMonth: String)

    @Query("DELETE FROM month_closes")
    suspend fun clear()
}
