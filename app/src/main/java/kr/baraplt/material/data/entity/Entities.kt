package kr.baraplt.material.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "materials", indices = [Index(value = ["codeNo"], unique = true)])
data class MaterialEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val codeNo: Int,
    val name: String,
    val unit: String,
    val packUnit: String = "",
    val unitPrice: Double,
    val safetyStock: Double = 0.0,
    val leadTimeDays: Int = 0,
    val note: String = "",
    val isActive: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "opening_stocks",
    indices = [Index(value = ["materialId", "yearMonth"], unique = true)]
)
data class OpeningStockEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val materialId: Long,
    val yearMonth: String,
    val qty: Double
)

@Entity(tableName = "stock_movements", indices = [Index(value = ["occurredOn"]), Index(value = ["materialId"])])
data class StockMovementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val materialId: Long,
    val type: String,
    val qty: Double,
    val unitPrice: Double,
    val occurredOn: String,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val createdBy: String = ""
)

@Entity(tableName = "products", indices = [Index(value = ["codeNo"], unique = true)])
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val codeNo: Int,
    val name: String,
    val sellPrice: Double = 0.0,
    val isActive: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "product_bom",
    indices = [Index(value = ["productId", "materialId"], unique = true)]
)
data class ProductBomEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val materialId: Long,
    val usQty: Double,
    val sortOrder: Int = 0
)

@Entity(
    tableName = "daily_production",
    indices = [Index(value = ["productId", "workDate"], unique = true)]
)
data class DailyProductionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val workDate: String,
    val qty: Int
)

@Entity(
    tableName = "product_plans",
    indices = [Index(value = ["productId", "yearMonth"], unique = true)]
)
data class ProductPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val yearMonth: String,
    val qty: Int
)

@Entity(tableName = "finished_goods", indices = [Index(value = ["codeNo"], unique = true)])
data class FinishedGoodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val codeNo: Int,
    val name: String,
    val sellPrice: Double = 0.0,
    val isActive: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "finished_composition",
    indices = [Index(value = ["finishedGoodId", "productId"], unique = true)]
)
data class FinishedCompositionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val finishedGoodId: Long,
    val productId: Long,
    val sortOrder: Int = 0
)

@Entity(
    tableName = "monthly_plans",
    indices = [Index(value = ["finishedGoodId", "yearMonth"], unique = true)]
)
data class MonthlyPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val finishedGoodId: Long,
    val yearMonth: String,
    val qty: Int
)

@Entity(tableName = "month_closes")
data class MonthCloseEntity(
    @PrimaryKey val yearMonth: String,
    val closedAt: Long,
    val closedBy: String
)
