package kr.baraplt.material.data

import kr.baraplt.material.data.repo.BackupBundle
import kr.baraplt.material.data.repo.Workspace
import kr.baraplt.material.domain.MovementType

object XlsxExport {
    fun toBytes(bundle: BackupBundle, workspace: Workspace?, factoryId: String): ByteArray {
        val materialName = bundle.materials.associate { it.id to "${it.codeNo} ${it.name}" }
        val productName = bundle.products.associate { it.id to "${it.codeNo} ${it.name}" }
        val finishedName = bundle.finished.associate { it.id to "${it.codeNo} ${it.name}" }
        val typeLabel = MovementType.entries.associate { it.name to it.label }

        val sheets = buildList {
            add(
                XlsxWriter.Sheet(
                    "안내",
                    listOf(
                        listOf("공장", factoryId),
                        listOf("작업월", workspace?.month?.value.orEmpty()),
                        listOf("마감", if (workspace?.closed == true) "예" else "아니오")
                    )
                )
            )
            if (workspace != null) {
                add(
                    XlsxWriter.Sheet(
                        "현재고",
                        listOf(
                            listOf(
                                "번호", "품명", "단위", "단가", "시작재고", "입고", "반출", "폐기", "조정",
                                "생산투입", "현재고", "입고금액", "사용금액", "폐기금액", "상태"
                            )
                        ) + workspace.materials.map { m ->
                            listOf(
                                m.codeNo, m.name, m.unit, m.unitPrice, m.opening, m.inbound, m.outbound,
                                m.scrap, m.adjust, m.usage, m.current, m.purchaseAmount, m.usageAmount,
                                m.scrapCost, m.status.name
                            )
                        }
                    )
                )
                add(
                    XlsxWriter.Sheet(
                        "월실적",
                        listOf(
                            listOf("항목", "값"),
                            listOf("판매금액", workspace.report.salesAmount),
                            listOf("자재사용금액", workspace.report.usageAmount),
                            listOf("입고금액", workspace.report.purchaseAmount),
                            listOf("폐기금액", workspace.report.scrapCost),
                            listOf("생산수량", workspace.report.producedQty),
                            listOf("입고수량", workspace.report.inboundQty),
                            listOf("자재비율", workspace.report.materialRatio),
                            listOf("등급", workspace.report.grade)
                        )
                    )
                )
                add(
                    XlsxWriter.Sheet(
                        "단품실적",
                        listOf(listOf("번호", "단품", "판매단가", "월계획", "생산", "자재비", "자재비율", "사용금액", "판매금액")) +
                            workspace.products.map { p ->
                                listOf(
                                    p.codeNo, p.name, p.sellPrice, p.monthPlan, p.produced,
                                    p.materialCost, p.materialRatio, p.usageAmount, p.salesAmount
                                )
                            }
                    )
                )
            }
            add(
                XlsxWriter.Sheet(
                    "자재",
                    listOf(listOf("번호", "품명", "단위", "포장", "단가", "안전재고", "리드타임", "비고", "사용")) +
                        bundle.materials.map { m ->
                            listOf(
                                m.codeNo, m.name, m.unit, m.packUnit, m.unitPrice,
                                m.safetyStock, m.leadTimeDays, m.note, if (m.isActive) "Y" else "N"
                            )
                        }
                )
            )
            add(
                XlsxWriter.Sheet(
                    "시작재고",
                    listOf(listOf("년월", "자재", "수량")) +
                        bundle.openings.map { listOf(it.yearMonth, materialName[it.materialId] ?: it.materialId, it.qty) }
                )
            )
            add(
                XlsxWriter.Sheet(
                    "입출고",
                    listOf(listOf("일자", "구분", "자재", "수량", "단가", "메모", "입력자")) +
                        bundle.movements.map {
                            listOf(
                                it.occurredOn,
                                typeLabel[it.type] ?: it.type,
                                materialName[it.materialId] ?: it.materialId,
                                it.qty,
                                it.unitPrice,
                                it.note,
                                it.createdBy
                            )
                        }
                )
            )
            add(
                XlsxWriter.Sheet(
                    "단품",
                    listOf(listOf("번호", "단품", "판매단가", "사용")) +
                        bundle.products.map { listOf(it.codeNo, it.name, it.sellPrice, if (it.isActive) "Y" else "N") }
                )
            )
            add(
                XlsxWriter.Sheet(
                    "BOM",
                    listOf(listOf("단품", "자재", "US", "순번")) +
                        bundle.bom.sortedWith(compareBy({ it.productId }, { it.sortOrder })).map {
                            listOf(
                                productName[it.productId] ?: it.productId,
                                materialName[it.materialId] ?: it.materialId,
                                it.usQty,
                                it.sortOrder
                            )
                        }
                )
            )
            add(
                XlsxWriter.Sheet(
                    "일생산",
                    listOf(listOf("일자", "단품", "수량")) +
                        bundle.production.sortedBy { it.workDate }.map {
                            listOf(it.workDate, productName[it.productId] ?: it.productId, it.qty)
                        }
                )
            )
            add(
                XlsxWriter.Sheet(
                    "단품월계획",
                    listOf(listOf("년월", "단품", "수량")) +
                        bundle.productPlans.map { listOf(it.yearMonth, productName[it.productId] ?: it.productId, it.qty) }
                )
            )
            add(
                XlsxWriter.Sheet(
                    "완제품",
                    listOf(listOf("번호", "완제품", "판매단가", "사용")) +
                        bundle.finished.map { listOf(it.codeNo, it.name, it.sellPrice, if (it.isActive) "Y" else "N") }
                )
            )
            add(
                XlsxWriter.Sheet(
                    "완제품구성",
                    listOf(listOf("완제품", "단품", "순번")) +
                        bundle.composition.sortedWith(compareBy({ it.finishedGoodId }, { it.sortOrder })).map {
                            listOf(
                                finishedName[it.finishedGoodId] ?: it.finishedGoodId,
                                productName[it.productId] ?: it.productId,
                                it.sortOrder
                            )
                        }
                )
            )
            add(
                XlsxWriter.Sheet(
                    "완제품월계획",
                    listOf(listOf("년월", "완제품", "수량")) +
                        bundle.monthlyPlans.map {
                            listOf(it.yearMonth, finishedName[it.finishedGoodId] ?: it.finishedGoodId, it.qty)
                        }
                )
            )
            add(
                XlsxWriter.Sheet(
                    "마감",
                    listOf(listOf("년월", "마감시각", "마감자")) +
                        bundle.closes.map { listOf(it.yearMonth, it.closedAt, it.closedBy) }
                )
            )
        }
        return XlsxWriter.write(sheets)
    }
}
