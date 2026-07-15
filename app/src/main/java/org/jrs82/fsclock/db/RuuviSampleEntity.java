package org.jrs82.fsclock.db;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** One data row from a RuuviTag RAWv2 packet (data format 5).
 *  The channel is always the sensor MAC; the user-visible name (bedroom/living room/balcony)
 *  lives in SettingsManager, not here. The measurement sequence allows filtering out
 *  duplicates when the same packet arrives via multiple BLE advertising frames. */
@Entity(
    tableName = "ruuvi_samples",
    indices = {
        @Index(value = {"mac", "timestamp"}),
        @Index(value = {"mac", "measurement_sequence"})
    }
)
public class RuuviSampleEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    @ColumnInfo(name = "mac")
    public String mac = "";

    @ColumnInfo(name = "timestamp")
    public long timestamp;

    @ColumnInfo(name = "temperature_c")
    @Nullable
    public Double temperatureC;

    @ColumnInfo(name = "humidity_pct")
    @Nullable
    public Double humidityPct;

    @ColumnInfo(name = "pressure_pa")
    @Nullable
    public Integer pressurePa;

    @ColumnInfo(name = "accel_x_mg")
    @Nullable
    public Integer accelXmG;

    @ColumnInfo(name = "accel_y_mg")
    @Nullable
    public Integer accelYmG;

    @ColumnInfo(name = "accel_z_mg")
    @Nullable
    public Integer accelZmG;

    @ColumnInfo(name = "battery_mv")
    @Nullable
    public Integer batteryMv;

    @ColumnInfo(name = "tx_power_dbm")
    @Nullable
    public Integer txPowerDbm;

    @ColumnInfo(name = "movement_counter")
    @Nullable
    public Integer movementCounter;

    @ColumnInfo(name = "measurement_sequence")
    @Nullable
    public Integer measurementSequence;

    @ColumnInfo(name = "rssi")
    public int rssi;

    public RuuviSampleEntity() {}
}
