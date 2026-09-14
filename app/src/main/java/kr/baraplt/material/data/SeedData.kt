package kr.baraplt.material.data

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
import kr.baraplt.material.domain.MovementType

/**
 * APP / APP1 엑셀 샘플을 그대로 넣은 데모 데이터.
 * 8월 운영 후 현재고를 9월 시작재고로 넘긴다.
 */
object SeedData {
    const val SAMPLE_MONTH = "2026-08"
    const val NEXT_MONTH = "2026-09"

    val materials = listOf(
        MaterialEntity(codeNo = 1, name = "원단(청색)", unit = "m", packUnit = "100(롤)", unitPrice = 30000.0, safetyStock = 80.0, leadTimeDays = 12),
        MaterialEntity(codeNo = 2, name = "수지(청색)", unit = "kg", packUnit = "500(포대)", unitPrice = 2300.0, safetyStock = 500.0, leadTimeDays = 4),
        MaterialEntity(codeNo = 3, name = "클립(일반)", unit = "ea", packUnit = "300(봉지)", unitPrice = 50.0, safetyStock = 1200.0, leadTimeDays = 2),
        MaterialEntity(codeNo = 4, name = "PAD(백색)15X25", unit = "ea", packUnit = "1000(묵음)", unitPrice = 5.0, safetyStock = 1000.0, leadTimeDays = 2),
        MaterialEntity(codeNo = 5, name = "스크류(4X12X1.25)", unit = "ea", packUnit = "500(봉지)", unitPrice = 15.0, safetyStock = 500.0, leadTimeDays = 2),
        MaterialEntity(codeNo = 6, name = "원단(트리코트 NNB)", unit = "m", packUnit = "100(롤)", unitPrice = 28000.0, safetyStock = 100.0, leadTimeDays = 12),
        MaterialEntity(codeNo = 7, name = "원단(스웨이드 NNB)", unit = "m", packUnit = "100(롤)", unitPrice = 42000.0, safetyStock = 60.0, leadTimeDays = 14),
        MaterialEntity(codeNo = 8, name = "수지(흑색)", unit = "kg", packUnit = "500(포대)", unitPrice = 2100.0, safetyStock = 400.0, leadTimeDays = 4)
    )

    val openingsAugust = listOf(900.0, 800.0, 8000.0, 10000.0, 2000.0, 450.0, 220.0, 600.0)

    val inboundAugust = listOf(
        Triple(2, 1000.0, "8월 입고"),
        Triple(3, 900.0, "8월 입고"),
        Triple(4, 1000.0, "8월 입고"),
        Triple(5, 500.0, "8월 입고")
    )

    val scrapAugust = listOf(Pair(2, 2.0))

    val products = listOf(
        ProductEntity(codeNo = 1, name = "FRT", sellPrice = 25000.0),
        ProductEntity(codeNo = 2, name = "CTR", sellPrice = 37000.0),
        ProductEntity(codeNo = 3, name = "REAR", sellPrice = 38000.0),
        ProductEntity(codeNo = 4, name = "STEP1", sellPrice = 1500.0),
        ProductEntity(codeNo = 5, name = "SCUFF1", sellPrice = 1850.0),
        ProductEntity(codeNo = 6, name = "GATE", sellPrice = 9800.0),
        ProductEntity(codeNo = 7, name = "COWL LHD", sellPrice = 7200.0)
    )

    // material codeNo to US
    val boms: Map<Int, List<Pair<Int, Double>>> = mapOf(
        1 to listOf(1 to 0.6, 3 to 2.0, 2 to 0.55, 5 to 1.0, 4 to 7.0),
        2 to listOf(1 to 0.8, 2 to 0.98, 3 to 2.0, 4 to 3.0),
        3 to listOf(1 to 0.72, 2 to 0.65, 3 to 1.0, 5 to 2.0),
        4 to listOf(2 to 0.3, 3 to 6.0),
        5 to listOf(2 to 0.46, 3 to 2.0, 4 to 5.0),
        6 to listOf(2 to 0.74, 3 to 1.0),
        7 to listOf(2 to 0.41, 5 to 2.0)
    )

    val productPlansAugust = mapOf(1 to 7175, 2 to 7175, 3 to 4800, 4 to 5425, 5 to 3425)

    val productionDays = listOf(1, 2, 13, 24, 25)
    val dailyQty = mapOf(1 to 80, 2 to 80, 3 to 70, 4 to 55, 5 to 50)

    val finished = listOf(
        FinishedGoodEntity(codeNo = 1, name = "A1SPK", sellPrice = 101500.0),
        FinishedGoodEntity(codeNo = 2, name = "A2SPK", sellPrice = 103350.0),
        FinishedGoodEntity(codeNo = 3, name = "A1STD", sellPrice = 62000.0),
        FinishedGoodEntity(codeNo = 4, name = "AEVSPK", sellPrice = 100000.0),
        FinishedGoodEntity(codeNo = 5, name = "AEVPESPK", sellPrice = 65350.0)
    )

    val compositions: Map<Int, List<Int>> = mapOf(
        1 to listOf(1, 2, 3, 4),
        2 to listOf(1, 2, 3, 4, 5),
        3 to listOf(1, 2),
        4 to listOf(1, 2, 3),
        5 to listOf(1, 2, 4, 5)
    )

    val finishedPlansAugust = mapOf(1 to 2000, 2 to 1800, 3 to 750, 4 to 1000, 5 to 1625)

    suspend fun populate(db: AppDatabase) {
        db.clearAllTables()

        val materialIds = db.materials().insertAll(materials)
        val productIds = db.products().insertAll(products)
        val finishedIds = db.finished().insertAll(finished)

        val matByNo = materials.indices.associate { materials[it].codeNo to materialIds[it] }
        val prodByNo = products.indices.associate { products[it].codeNo to productIds[it] }
        val finByNo = finished.indices.associate { finished[it].codeNo to finishedIds[it] }

        db.openings().upsertAll(
            materials.indices.map { i ->
                OpeningStockEntity(
                    materialId = materialIds[i],
                    yearMonth = SAMPLE_MONTH,
                    qty = openingsAugust[i]
                )
            }
        )

        inboundAugust.forEach { (no, qty, note) ->
            val id = matByNo.getValue(no)
            val price = materials.first { it.codeNo == no }.unitPrice
            db.movements().insert(
                StockMovementEntity(
                    materialId = id,
                    type = MovementType.INBOUND.name,
                    qty = qty,
                    unitPrice = price,
                    occurredOn = "$SAMPLE_MONTH-10",
                    note = note,
                    createdBy = "샘플"
                )
            )
        }
        scrapAugust.forEach { (no, qty) ->
            val id = matByNo.getValue(no)
            val price = materials.first { it.codeNo == no }.unitPrice
            db.movements().insert(
                StockMovementEntity(
                    materialId = id,
                    type = MovementType.SCRAP.name,
                    qty = qty,
                    unitPrice = price,
                    occurredOn = "$SAMPLE_MONTH-18",
                    note = "불량 폐기",
                    createdBy = "샘플"
                )
            )
        }

        val bomRows = mutableListOf<ProductBomEntity>()
        boms.forEach { (prodNo, lines) ->
            val pid = prodByNo.getValue(prodNo)
            lines.forEachIndexed { index, (matNo, us) ->
                bomRows += ProductBomEntity(
                    productId = pid,
                    materialId = matByNo.getValue(matNo),
                    usQty = us,
                    sortOrder = index
                )
            }
        }
        db.bom().upsertAll(bomRows)

        productPlansAugust.forEach { (no, qty) ->
            db.productPlans().upsert(
                ProductPlanEntity(productId = prodByNo.getValue(no), yearMonth = SAMPLE_MONTH, qty = qty)
            )
        }

        val productions = mutableListOf<DailyProductionEntity>()
        productionDays.forEach { day ->
            dailyQty.forEach { (prodNo, qty) ->
                productions += DailyProductionEntity(
                    productId = prodByNo.getValue(prodNo),
                    workDate = "%s-%02d".format(SAMPLE_MONTH, day),
                    qty = qty
                )
            }
        }
        db.production().upsertAll(productions)

        val comps = mutableListOf<FinishedCompositionEntity>()
        compositions.forEach { (fgNo, prodNos) ->
            val fid = finByNo.getValue(fgNo)
            prodNos.forEachIndexed { index, pNo ->
                comps += FinishedCompositionEntity(
                    finishedGoodId = fid,
                    productId = prodByNo.getValue(pNo),
                    sortOrder = index
                )
            }
        }
        db.composition().upsertAll(comps)

        finishedPlansAugust.forEach { (no, qty) ->
            db.monthlyPlans().upsert(
                MonthlyPlanEntity(finishedGoodId = finByNo.getValue(no), yearMonth = SAMPLE_MONTH, qty = qty)
            )
        }

        // 9월 시작재고 = 8월 현재고 (엑셀 값)
        val septemberOpening = listOf(88.0, 761.0, 4800.0, 5750.0, 1400.0, 450.0, 220.0, 600.0)
        db.openings().upsertAll(
            materials.indices.map { i ->
                OpeningStockEntity(
                    materialId = materialIds[i],
                    yearMonth = NEXT_MONTH,
                    qty = septemberOpening[i]
                )
            }
        )

        db.closes().upsert(
            MonthCloseEntity(yearMonth = SAMPLE_MONTH, closedAt = System.currentTimeMillis(), closedBy = "정길모")
        )
    }
}
