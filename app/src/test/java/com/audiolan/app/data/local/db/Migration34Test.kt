package com.audiolan.app.data.local.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.audiolan.app.di.MIGRATION_3_4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class Migration34Test {
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
    fun migration34ReplacesTransportWithNetworkSelectionWithoutDataLoss() {
        val db = createVersion3Database()
        insertVersion3Stream(db, "WIFI", "WiFi Stream")
        insertVersion3Stream(db, "USB_TETHER", "Usb Stream")

        MIGRATION_3_4.migrate(db)

        db.query(
            "SELECT name, network_interface, network_ssid, host, port, source_type, broadcast_mode, volume " +
                "FROM streams ORDER BY name",
        ).use { cursor ->
            cursor.moveToNext()
            assertEquals("Usb Stream", cursor.getString(0))
            assertEquals("__USB_TETHER__", cursor.getString(1))
            assertEquals(null, cursor.getString(2))
            assertEquals("192.168.1.10", cursor.getString(3))
            assertEquals(6980, cursor.getInt(4))
            assertEquals("MIC", cursor.getString(5))
            assertEquals(0, cursor.getInt(6))
            assertEquals(1.0f, cursor.getFloat(7), 0.0001f)

            cursor.moveToNext()
            assertEquals("WiFi Stream", cursor.getString(0))
            assertEquals("__WIFI__", cursor.getString(1))
            assertEquals("__ANY_SSID__", cursor.getString(2))
        }

        db.query("PRAGMA table_info(streams)").use { cursor ->
            val columns = mutableSetOf<String>()
            while (cursor.moveToNext()) {
                columns += cursor.getString(cursor.getColumnIndexOrThrow("name"))
            }
            assertFalse(columns.contains("transport_mode"))
        }
    }

    private fun createVersion3Database(): SupportSQLiteDatabase {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(TEST_DATABASE)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(3) {
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
                                `source_type` TEXT NOT NULL DEFAULT 'MIC',
                                `broadcast_mode` INTEGER NOT NULL DEFAULT 0,
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

    private fun insertVersion3Stream(
        db: SupportSQLiteDatabase,
        transportMode: String,
        name: String,
    ) {
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
                source_type,
                broadcast_mode,
                volume,
                is_enabled
            ) VALUES (
                'TRANSMITTER',
                '$name',
                '192.168.1.10',
                6980,
                'OPTIMAL',
                '$transportMode',
                0,
                'MIC',
                0,
                1.0,
                1
            )
            """.trimIndent(),
        )
    }

    private companion object {
        const val TEST_DATABASE = "migration-34-test.db"
    }
}
