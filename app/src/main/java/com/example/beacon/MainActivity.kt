package com.example.beacon

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.RemoteException
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.RelativeLayout
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
import android.graphics.*
import android.view.MotionEvent
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.viewModels
import androidx.lifecycle.Observer
import com.example.beacon.algorithm.PathFinder
import com.example.beacon.algorithm.PositionCalculator
import com.example.beacon.data.entities.Weight
import com.example.beacon.vm.BeaconViewModel
import com.example.beacon.vm.WeightViewModel
import org.altbeacon.beacon.Beacon

@Suppress("DEPRECATION")
class MainActivity : AppCompatActivity(), BeaconConsumer {

    private lateinit var beaconManager: BeaconManager
    private val beaconIdentifiers = mapOf(
        Pair(1234 to 4321, "A"),
        Pair(7621 to 42517, "B"),
        Pair(8358 to 30324, "C"),
        Pair(60390 to 23173, "D"),
        Pair(23835 to 50544, "E"),
        Pair(5895 to 10259, "F"),
        Pair(34308 to 7692, "G"),
        Pair(64608 to 31, "H")
    )

    private val beaconViewModel: BeaconViewModel by viewModels()
    private var currentLocationView: ImageView? = null
    private val rssiReadings = mutableMapOf<String, MutableList<Double>>()
    private val windowSize = 5
    private var weights = IntArray(256) { 0 }
    private var size = 16
    private var currentEstimatedPosition: Pair<Int, Int>? = null
    private val weightIcons = mutableMapOf<Pair<Int, Int>, TextView>()
    private val pathViews = mutableListOf<View>()
    private val viewModel: WeightViewModel by viewModels()
    private val positionCalculator = PositionCalculator(mapOf())
    private val pathFinder = PathFinder()

    private var isGridVisible = true
    private var areWeightsVisible = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestPermissionsIfNeeded()
        configureMatrixSizeSpinner()
        initializeBeaconManager()
        initializeMapTouchListener()
        drawGridOnMap()

        findViewById<Button>(R.id.toggle_grid_button).setOnClickListener {
            isGridVisible = !isGridVisible
            areWeightsVisible = !areWeightsVisible
            drawGridOnMap()
        }

        viewModel.weights.observe(this, Observer { weightsList ->
            weightsList?.let {
                updateWeights(it)
            }
        })

        beaconViewModel.beaconPositions.observe(this, Observer { beaconPositions ->
            beaconPositions?.let {
                updateBeaconPositions(it)
                positionCalculator.updateBeaconPositions(it.associateBy { pos -> pos.identifier })
                Log.d("MainActivity", "Updated beacon positions in PositionCalculator")
            }
        })
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
                ) != PackageManager.PERMISSION_GRANTED) {
                return
            }
            startActivityForResult(enableBtIntent, 1)
        }
    }

    private fun configureMatrixSizeSpinner() {
        val matrixSizeSpinner = findViewById<Spinner>(R.id.matrix_size_spinner)
        val adapter = ArrayAdapter.createFromResource(
            this,
            R.array.matrix_sizes,
            android.R.layout.simple_spinner_item
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        matrixSizeSpinner.adapter = adapter
        matrixSizeSpinner.setSelection(0)

        matrixSizeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val matrixId = when (position) {
                    0 -> 1
                    1 -> 2
                    else -> 2
                }
                size = when (position) {
                    0 -> 8
                    1 -> 16
                    else -> 16
                }
                weights = IntArray(size * size) { 0 }
                drawGridOnMap()
                currentEstimatedPosition?.let { updateCurrentLocationOnMap(it) }
                viewModel.loadWeights(size)
                beaconViewModel.loadBeaconPositions(matrixId)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun updateBeaconPositions(beaconPositions: List<BeaconPosition>) {
        beaconPositions.forEach { beaconPosition ->
            Log.d("MainActivity", "Beacon: ${beaconPosition.identifier}, X: ${beaconPosition.x}, Y: ${beaconPosition.y}")
        }
        positionCalculator.updateBeaconPositions(beaconPositions.associateBy { it.identifier })
    }

    private fun initializeBeaconManager() {
        beaconManager = BeaconManager.getInstanceForApplication(this)
        beaconManager.beaconParsers.add(BeaconParser().setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24"))
        beaconManager.bind(this)
    }

    private fun initializeMapTouchListener() {
        val mapLayout = findViewById<RelativeLayout>(R.id.map_layout)
        mapLayout.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                handleMapTouch(event.x, event.y)
            }
            true
        }
    }

    private fun handleMapTouch(x: Float, y: Float) {
        val escala = 1000f / size
        val touchedX = (x / escala).toInt()
        val touchedY = (y / escala).toInt()
        val index = touchedY * size + touchedX
        if (index in weights.indices) {
            weights[index] = (weights[index] % 5) + 1
            drawWeightOnGrid(touchedX, touchedY, weights[index], findViewById(R.id.map_layout))
            Toast.makeText(this, "Peso ajustado para ${weights[index]} na posição ($touchedX, $touchedY)", Toast.LENGTH_SHORT).show()
        }
    }

    private fun drawGridOnMap() {
        val relativeLayout = findViewById<RelativeLayout>(R.id.map_layout)
        relativeLayout.removeAllViews()
        val imageView = ImageView(this)
        val bitmap = Bitmap.createBitmap(1000, 1000, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()

        val background = BitmapFactory.decodeResource(resources, R.drawable.output)
        val scaleFactor = Math.min(bitmap.width.toFloat() / background.width, bitmap.height.toFloat() / background.height)
        val scaledBitmap = Bitmap.createScaledBitmap(background, (background.width * scaleFactor).toInt(), (background.height * scaleFactor).toInt(), true)
        canvas.drawBitmap(scaledBitmap, 0f, 0f, paint)

        if (isGridVisible) {
            paint.color = Color.RED
            paint.style = Paint.Style.STROKE
            paint.textSize = 20f

            val gridSize = 1000f / size
            for (i in 0 until size) {
                for (j in 0 until size) {
                    val x = i * gridSize
                    val y = j * gridSize
                    canvas.drawRect(x, y, x + gridSize, y + gridSize, paint)
                }
            }
        }

        imageView.setImageBitmap(bitmap)
        relativeLayout.addView(imageView)

        if (areWeightsVisible) {
            weights.forEachIndexed { index, weight ->
                val x = index % size
                val y = index / size
                drawWeightOnGrid(x, y, weight, relativeLayout)
            }
        }
    }

    private fun updateCurrentLocationOnMap(position: Pair<Int, Int>) {
        val mapLayout = findViewById<RelativeLayout>(R.id.map_layout)
        val escala = 1000f / size // Ajuste a escala de acordo com o tamanho atual da grade

        currentLocationView?.let { mapLayout.removeView(it) }

        val x = position.first * escala
        val y = position.second * escala

        val estimatedPositionIcon = ImageView(this@MainActivity)
        estimatedPositionIcon.setImageResource(R.drawable.baseline_location_on_24)
        val layoutParams = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.WRAP_CONTENT,
            RelativeLayout.LayoutParams.WRAP_CONTENT
        )
        layoutParams.leftMargin = x.toInt()
        layoutParams.topMargin = y.toInt()
        estimatedPositionIcon.layoutParams = layoutParams
        mapLayout.addView(estimatedPositionIcon)
        currentLocationView = estimatedPositionIcon
    }

    private fun drawWeightOnGrid(x: Int, y: Int, weight: Int, mapLayout: RelativeLayout) {
        if (!areWeightsVisible) return

        weightIcons[Pair(x, y)]?.let { mapLayout.removeView(it) }

        val weightIcon = TextView(this)
        weightIcon.text = weight.toString()
        weightIcon.setTextColor(Color.BLACK)
        weightIcon.textSize = 14f
        val layoutParams = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.WRAP_CONTENT,
            RelativeLayout.LayoutParams.WRAP_CONTENT
        )
        layoutParams.leftMargin = (x * 1000f / size).toInt()
        layoutParams.topMargin = (y * 1000f / size).toInt()
        weightIcon.layoutParams = layoutParams
        mapLayout.addView(weightIcon)

        weightIcons[Pair(x, y)] = weightIcon
    }

    private fun handleBeaconDetection(beacons: Collection<Beacon>) {
        val textView = findViewById<TextView>(R.id.textView)
        val mapLayout = findViewById<RelativeLayout>(R.id.map_layout)

        val beaconDistances = beacons.associate {
            val identifier = beaconIdentifiers[it.id2.toInt() to it.id3.toInt()] ?: "Desconhecido"
            identifier to filterRssi(identifier, it.distance)
        }

        beaconDistances.forEach { (identifier, distance) ->
            Log.d("BeaconDetection", "Identifier: $identifier, Distance: $distance")
        }

        val position = positionCalculator.calculatePosition(beaconDistances)
        textView.text = buildString {
            append("Beacons Detectados:\n")
            beacons.forEach { beacon ->
                val identifier = beaconIdentifiers[beacon.id2.toInt() to beacon.id3.toInt()] ?: "Desconhecido"
                append("Identificador: $identifier, UUID: ${beacon.id1}, Major: ${beacon.id2}, Minor: ${beacon.id3}, Distância: ${String.format("%.2f", beacon.distance)}m\n")
            }
        }

        position?.let {
            currentEstimatedPosition = Pair(it.x.toInt(), it.y.toInt())
            updateCurrentLocationOnMap(currentEstimatedPosition!!)
        }
    }

    private fun filterRssi(identifier: String, rssi: Double): Double {
        val readings = rssiReadings.getOrPut(identifier) { mutableListOf() }
        readings.add(rssi)
        if (readings.size > windowSize) readings.removeAt(0)
        return readings.average()
    }

    fun onCalculatePathClick(view: View) {
        val endPointInput = findViewById<EditText>(R.id.end_point).text.toString()
        val endPoint = endPointInput.split(",").let { Pair(it[0].toInt(), it[1].toInt()) }

        val startPoint = getCurrentLocation() ?: run {
            Toast.makeText(this, "Current location not available.", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d("MainActivity", "Start Point: $startPoint, End Point: $endPoint")

        val matrix = generateMatrixFromWeights(weights, size)

        Log.d("MainActivity", "Weight Matrix: ${matrix.contentDeepToString()}")

        if (startPoint.first !in 0 until size || startPoint.second !in 0 until size ||
            endPoint.first !in 0 until size || endPoint.second !in 0 until size) {
            Toast.makeText(this, "Points are out of bounds.", Toast.LENGTH_SHORT).show()
            return
        }

        if (!isValidPoint(startPoint, matrix) || !isValidPoint(endPoint, matrix)) {
            Toast.makeText(this, "Start or end point is not valid.", Toast.LENGTH_SHORT).show()
            return
        }

        val path = pathFinder.findShortestPath(matrix, startPoint, endPoint)
        if (path.isEmpty()) {
            Toast.makeText(this, "No path found.", Toast.LENGTH_SHORT).show()
        } else {
            drawPathOnMap(path)
        }
    }

    private fun generateMatrixFromWeights(weights: IntArray, size: Int): Array<IntArray> {
        val matrix = Array(size) { IntArray(size) }
        for (i in weights.indices) {
            matrix[i / size][i % size] = weights[i]
        }
        return matrix
    }

    private fun isValidPoint(point: Pair<Int, Int>, matrix: Array<IntArray>): Boolean {
        return matrix[point.first][point.second] != 0
    }

    private fun getCurrentLocation(): Pair<Int, Int>? {
        return currentEstimatedPosition?.let { (x, y) ->
            val escala = 1000f / size.toFloat() // Ajuste a escala de acordo com o tamanho atual da grade
            Pair((x / escala).toInt(), (y / escala).toInt())
        }
    }

    private fun drawPathOnMap(path: List<Pair<Int, Int>>) {
        val mapLayout = findViewById<RelativeLayout>(R.id.map_layout)
        val escala = 1000f / size

        pathViews.forEach { mapLayout.removeView(it) }
        pathViews.clear()

        for ((x, y) in path) {
            val pathView = View(this)
            pathView.setBackgroundColor(Color.BLUE)
            val layoutParams = RelativeLayout.LayoutParams((escala / 2).toInt(), (escala / 2).toInt())
            layoutParams.leftMargin = (x * escala).toInt()
            layoutParams.topMargin = (y * escala).toInt()
            pathView.layoutParams = layoutParams
            mapLayout.addView(pathView)
            pathViews.add(pathView)
        }
    }

    private fun updateWeights(weightsList: List<Weight>) {
        for (weight in weightsList) {
            val index = weight.y * size + weight.x
            if (index in weights.indices) {
                weights[index] = weight.weight
                drawWeightOnGrid(weight.x, weight.y, weight.weight, findViewById(R.id.map_layout))
            }
        }
    }

    override fun onBeaconServiceConnect() {
        beaconManager.addRangeNotifier { beacons, _ ->
            runOnUiThread {
                handleBeaconDetection(beacons)
            }
        }

        try {
            beaconManager.startRangingBeaconsInRegion(Region("myBeaconRegion", null, null, null))
        } catch (e: RemoteException) {
            e.printStackTrace()
        }
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