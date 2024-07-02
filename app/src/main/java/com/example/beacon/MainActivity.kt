package com.example.beacon

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.RemoteException
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.altbeacon.beacon.BeaconConsumer
import org.altbeacon.beacon.BeaconManager
import org.altbeacon.beacon.BeaconParser
import org.altbeacon.beacon.Region
import com.example.beacon.data.entities.Position
import com.example.beacon.data.entities.BeaconPosition

@Suppress("DEPRECATION")
class MainActivity : AppCompatActivity(), BeaconConsumer {

    private lateinit var beaconManager: BeaconManager
    private val beaconIdentifiers = mapOf(
        Pair(1234 to 4321, "A"),
        Pair(7621 to 42517, "B"),
        Pair(8358 to 30324, "C"),
        //Pair(60390 to 23173, "D"),
        //Pair(23835 to 50544, "E"),
        //Pair(5895 to 10259, "F"),
        //Pair(34308 to 7692, "G"),
        //Pair(64608 to 31, "H")
    )

    private val beaconPositions = mapOf(
        "A" to BeaconPosition(0.0, 0.0, "A"),
        "B" to BeaconPosition(10.0, 0.0, "B"),
        "C" to BeaconPosition(0.0, 10.0, "C"),
        //"D" to BeaconPosition(10.0, 10.0, "D"),
        //"E" to BeaconPosition(5.0, 5.0, "E"),
        //"F" to BeaconPosition(15.0, 0.0, "F"),
        //"G" to BeaconPosition(0.0, 15.0, "G"),
        //"H" to BeaconPosition(15.0, 15.0, "H")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Verifica e solicita permissões
        requestPermissionsIfNeeded()

        // Inicia do BeaconManager
        beaconManager = BeaconManager.getInstanceForApplication(this)
        beaconManager.beaconParsers.add(BeaconParser().setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24"))

        beaconManager.bind(this)
    }

    private fun requestPermissionsIfNeeded() {
        val permissionsNeeded = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_SCAN)
            }
        }

        if (permissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toTypedArray(), 1)
        } else {
            enableBluetooth()
        }
    }

    private fun enableBluetooth() {
        val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter != null && !bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return
            }
            startActivityForResult(enableBtIntent, 1)
        }
    }

    override fun onBeaconServiceConnect() {
        beaconManager.addRangeNotifier { beacons, region ->
            runOnUiThread {
                val textView = findViewById<TextView>(R.id.textView)
                val beaconDistances = beacons.associate {
                    val identifier = beaconIdentifiers[it.id2.toInt() to it.id3.toInt()] ?: "Desconhecido"
                    identifier to it.distance
                }

                val position = calculatePosition(beaconDistances)
                textView.text = buildString {
                    append("Beacons Detectados:\n")
                    beacons.forEach { beacon ->
                        val identifier = beaconIdentifiers[beacon.id2.toInt() to beacon.id3.toInt()] ?: "Desconhecido"
                        append("Identificador: $identifier, UUID: ${beacon.id1}, Major: ${beacon.id2}, Minor: ${beacon.id3}, Distância: ${String.format("%.2f", beacon.distance)}m\n")
                    }
                    position?.let {
                        append("Posição Estimada: (${String.format("%.2f", it.x)}, ${String.format("%.2f", it.y)})\n")
                    }
                }
            }
        }

        try {
            beaconManager.startRangingBeaconsInRegion(Region("myBeaconRegion", null, null, null))
        } catch (e: RemoteException) {
            e.printStackTrace()
        }
    }

    private fun calculatePosition(beaconDistances: Map<String, Double>): Position? {
        if (beaconDistances.size < 3) return null // Precisamos de pelo menos 3 beacons

        val positions = beaconDistances.keys.mapNotNull { beaconPositions[it] }
        val distances = beaconDistances.values.toList()

        if (positions.size < 3) return null // Verifique se temos posições suficientes

        val x1 = positions[0].x
        val y1 = positions[0].y
        val r1 = distances[0]

        val x2 = positions[1].x
        val y2 = positions[1].y
        val r2 = distances[1]

        val x3 = positions[2].x
        val y3 = positions[2].y
        val r3 = distances[2]

        val A = 2 * x2 - 2 * x1
        val B = 2 * y2 - 2 * y1
        val C = r1 * r1 - r2 * r2 - x1 * x1 - y1 * y1 + x2 * x2 + y2 * y2
        val D = 2 * x3 - 2 * x2
        val E = 2 * y3 - 2 * y2
        val F = r2 * r2 - r3 * r3 - x2 * x2 - y2 * y2 + x3 * x3 + y3 * y3

        val x = (C * E - F * B) / (E * A - B * D)
        val y = (C * D - A * F) / (B * D - A * E)

        return Position(x, y)
    }

    override fun onDestroy() {
        super.onDestroy()
        beaconManager.unbind(this)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            enableBluetooth()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1 && resultCode == RESULT_OK) {
            // Bluetooth ativo
        }
    }
}