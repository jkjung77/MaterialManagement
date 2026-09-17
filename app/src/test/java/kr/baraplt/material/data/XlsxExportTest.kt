package kr.baraplt.material.data

import kr.baraplt.material.data.entity.MaterialEntity
import kr.baraplt.material.data.repo.BackupBundle
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

class XlsxExportTest {

    @Test
    fun exportIncludesMaterialSheet() {
        val bytes = XlsxExport.toBytes(
            BackupBundle(
                materials = listOf(
                    MaterialEntity(id = 1, codeNo = 1, name = "원단(청색)", unit = "m", unitPrice = 30000.0)
                )
            ),
            workspace = null,
            factoryId = "경주1공장"
        )
        val names = mutableSetOf<String>()
        var found = false
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                names += entry.name
                val body = zip.readBytes().toString(Charsets.UTF_8)
                if (body.contains("원단(청색)") && body.contains("경주1공장")) found = true
                if (body.contains("원단(청색)")) found = true
            }
        }
        assertTrue(names.any { it.startsWith("xl/worksheets/sheet") })
        assertTrue(found)
    }
}
