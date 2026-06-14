package com.audiolan.app.data.local.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.audiolan.app.di.MIGRATION_2_3
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class Migration23Test {
    private lateinit var context: Context
    private var helper: SupportSQLiteOpenHelper? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(TEST_DATABASE)
    }

    @After
    fun tearDown() {
        helper?.close()
        context.deleteDatabase(TEST_DATABASE)
    }

    @Test
    fun migration23AddsTransmitterColumnsAndConvertsLegacyServiceTypes() {
        val db = createVersion2Database()
        insertLegacyStream(db, "MIC", "Mic")
        insertLegacyStream(db, "CAST", "Cast")
        insertLegacyStream(db, "RECEIVER", "Receiver")

        MIGRATION_2_3.migrate(db)

        db.query(
            "SELECT service_type, source_type, broadcast_mode FROM streams ORDER BY name",
        ).use { cursor ->
            cursor.moveToNext()
            assertEquals("TRANSMITTER", cursor.getString(0))
            assertEquals("MIC", cursor.getString(1))
            assertEquals(0, cursor.getInt(2))

            cursor.moveToNext()
            assertEquals("TRANSMITTER", cursor.getString(0))
            assertEquals("MIC", cursor.getString(1))
            assertEquals(0, cursor.getInt(2))

            cursor.moveToNext()
            assertEquals("RECEIVER", cursor.getString(0))
            assertEquals("MIC", cursor.getString(1))
            assertEquals(0, cursor.getInt(2))
        }
    }

    private fun createVersion2Database(): SupportSQLiteDatabase {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(TEST_DATABASE)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(2) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS `streams` (
                                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                `service_type` TEXT NOT NULL,
                                `name` TEXT NOT NULL,
                                `host` TEXT NOT NULL,
                                `port` INTEGER NOT NULL,
                                `net_quality` TEXT NOT NULL,
                                `transport_mode` TEXT NOT NULL DEFAULT 'WIFI',
                                `low_latency` INTEGER NOT NULL DEFAULT 0,
                                `volume` REAL NOT NULL,
                                `is_enabled` INTEGER NOT NULL
                            )
                            """.trimIndent(),
                        )
                        db.execSQL(
                            "CREATE INDEX IF NOT EXISTS `index_streams_service_type` " +
                                "ON `streams` (`service_type`)",
                        )
                    }

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int,
                    ) = Unit
                },
            )
            .build()

        helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        return requireNotNull(helper).writableDatabase
    }

    private fun insertLegacyStream(db: SupportSQLiteDatabase, serviceType: String, name: String) {
        db.execSQL(
            """
            INSERT INTO streams (
                service_type,
                name,
                host,
                port,
                net_quality,
                transport_mode,
                low_latency,
                volume,
                is_enabled
            ) VALUES (
                '$serviceType',
                '$name',
                '192.168.1.10',
                6980,
                'OPTIMAL',
                'WIFI',
                0,
                1.0,
                1
            )
            """.trimIndent(),
        )
    }

    private companion object {
        const val TEST_DATABASE = "migration-23-test.db"
    }
}
